package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.compat.h5.mapper.AppConversationArchiveMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationIdempotencyMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationStBindingMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCleanupService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * H5：收件箱「删会话」与聊天内「重新开始」——清空业务消息事实并维护归档/快照。
 */
@Service
public class H5TavernSessionService {

    private static final Logger log = LoggerFactory.getLogger(H5TavernSessionService.class);

    private final AppMessageMapper messageMapper;
    private final AppGenerationTaskMapper taskMapper;
    private final ConversationMemoryCleanupService memoryCleanupService;
    private final AppConversationMapper conversationMapper;
    private final AppConversationIdempotencyMapper idempotencyMapper;
    private final AppConversationStBindingMapper bindingMapper;
    private final AppConversationArchiveMapper archiveMapper;
    private final ChatSnapshotService snapshotService;

    public H5TavernSessionService(
            AppMessageMapper messageMapper,
            AppGenerationTaskMapper taskMapper,
            ConversationMemoryCleanupService memoryCleanupService,
            AppConversationMapper conversationMapper,
            AppConversationIdempotencyMapper idempotencyMapper,
            AppConversationStBindingMapper bindingMapper,
            AppConversationArchiveMapper archiveMapper,
            ChatSnapshotService snapshotService) {
        this.messageMapper = messageMapper;
        this.taskMapper = taskMapper;
        this.memoryCleanupService = memoryCleanupService;
        this.conversationMapper = conversationMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.bindingMapper = bindingMapper;
        this.archiveMapper = archiveMapper;
        this.snapshotService = snapshotService;
    }

    @Transactional
    public void wipeConversationMessages(long conversationId) {
        int activeTasks = taskMapper.countActiveByConversationId(conversationId);
        int activeMessages = messageMapper.countActiveByConversationId(conversationId);
        if (activeTasks > 0 || activeMessages > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前会话仍在生成中，请等待完成或停止后再删除");
        }
        taskMapper.softDeleteByConversationId(conversationId);
        memoryCleanupService.clearConversationMemory(conversationId);
        messageMapper.softDeleteByConversationId(conversationId, "conversation_wipe");
    }

    @Transactional
    public void purgeUserCharacterConversations(long userId, long characterId) {
        List<Long> conversationIds = conversationMapper.listIdsByUserAndCharacter(userId, characterId);
        for (Long conversationId : conversationIds) {
            if (conversationId == null) {
                continue;
            }
            int activeTasks = taskMapper.countActiveByConversationId(conversationId);
            int activeMessages = messageMapper.countActiveByConversationId(conversationId);
            if (activeTasks > 0 || activeMessages > 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前会话仍在生成中，请等待完成或停止后再删除");
            }
        }
        for (Long conversationId : conversationIds) {
            if (conversationId == null) {
                continue;
            }
            try {
                snapshotService.saveEmptySnapshot(conversationId);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "清空聊天快照失败，请稍后重试");
            }
            taskMapper.softDeleteByConversationId(conversationId);
            memoryCleanupService.clearConversationMemory(conversationId);
            messageMapper.softDeleteByConversationId(conversationId, "character_conversation_purge");
            archiveMapper.upsert(userId, conversationId);
            idempotencyMapper.deleteByConversationForUser(conversationId, userId);
            bindingMapper.deleteByConversationId(conversationId);
        }
    }

    /**
     * 删会话：归档（收件箱隐藏）+ 清空消息；ST 快照写空头。下次成功生成后会自动取消归档以回到收件箱。
     */
    @Transactional
    public void archiveHideAndWipe(long conversationId) {
        wipeConversationMessages(conversationId);
        try {
            snapshotService.saveEmptySnapshot(conversationId);
        } catch (Exception e) {
            log.warn("ST 快照清空失败（业务消息已删），conversationId={}: {}", conversationId, e.toString());
        }
    }

    /**
     * 聊天内「重新开始」：取消归档 + 清空消息 + 空头快照。
     */
    @Transactional
    public void restartFresh(long conversationId) {
        wipeConversationMessages(conversationId);
        try {
            snapshotService.saveEmptySnapshot(conversationId);
        } catch (Exception e) {
            log.warn("ST 快照清空失败（业务消息已删），conversationId={}: {}", conversationId, e.toString());
        }
    }
}
