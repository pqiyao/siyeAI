package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.entity.AppMessageSegment;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageSegmentMapper;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import com.example.sillyspringboot.conversation.mapper.AppConversationBranchMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversationBranchService {

    private static final int FORK_COPY_LIMIT = 20000;

    private final AppConversationMapper conversationMapper;
    private final AppConversationBranchMapper branchMapper;
    private final AppMessageMapper messageMapper;
    private final AppMessageSegmentMapper segmentMapper;
    private final ConversationMemoryCleanupService memoryCleanupService;

    public ConversationBranchService(
            AppConversationMapper conversationMapper,
            AppConversationBranchMapper branchMapper,
            AppMessageMapper messageMapper,
            AppMessageSegmentMapper segmentMapper,
            ConversationMemoryCleanupService memoryCleanupService
    ) {
        this.conversationMapper = conversationMapper;
        this.branchMapper = branchMapper;
        this.messageMapper = messageMapper;
        this.segmentMapper = segmentMapper;
        this.memoryCleanupService = memoryCleanupService;
    }

    @Transactional
    public AppConversationBranch requireActiveBranch(AppConversation conversation) {
        if (conversation == null || conversation.getId() == null || conversation.getUserId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        Long activeBranchId = conversation.getActiveBranchId();
        if (activeBranchId != null && activeBranchId > 0) {
            AppConversationBranch active = branchMapper.findByIdForConversation(conversation.getId(), activeBranchId);
            if (isOwnedByUser(active, conversation.getUserId())) {
                return active;
            }
        }
        AppConversationBranch fallback = ensureDefaultBranch(conversation.getId(), conversation.getUserId());
        conversationMapper.setActiveBranchId(conversation.getId(), fallback.getId());
        conversation.setActiveBranchId(fallback.getId());
        return fallback;
    }

    @Transactional
    public AppConversationBranch ensureDefaultBranch(long conversationId, long userId) {
        AppConversationBranch existing = branchMapper.findDefaultByConversationId(conversationId);
        if (existing != null) {
            if (isOwnedByUser(existing, userId)) {
                return existing;
            }
            throw new BusinessException(ErrorCode.NOT_FOUND, "default branch not found");
        }
        AppConversationBranch branch = new AppConversationBranch();
        branch.setConversationId(conversationId);
        branch.setUserId(userId);
        branch.setOpeningVariantIndex(0);
        branch.setTitle("默认分支");
        branch.setDefaultBranch(true);
        branchMapper.insert(branch);
        return branch;
    }

    public List<AppConversationBranch> listBranches(AppConversation conversation) {
        requireActiveBranch(conversation);
        return branchMapper.listByConversationId(conversation.getId());
    }

    @Transactional
    public List<AppConversationBranch> reconcileOpeningVariantBindings(
            AppConversation conversation,
            List<String> openingVariants
    ) {
        requireActiveBranch(conversation);
        List<AppConversationBranch> branches = new ArrayList<>();
        for (AppConversationBranch branch : branchMapper.listByConversationId(conversation.getId())) {
            if (belongsToConversationUser(branch, conversation)) {
                branches.add(branch);
            }
        }
        Map<Integer, AppConversationBranch> assigned = new HashMap<>();
        for (AppConversationBranch branch : branches) {
            if (branch != null && branch.getOpeningVariantIndex() != null) {
                assigned.putIfAbsent(branch.getOpeningVariantIndex(), branch);
            }
        }
        for (AppConversationBranch branch : branches) {
            if (branch == null || branch.getId() == null || branch.getOpeningVariantIndex() != null) {
                continue;
            }
            AppMessage opening = messageMapper.findOpeningByConversationBranch(conversation.getId(), branch.getId());
            int variantIndex = findOpeningVariantIndex(opening == null ? null : opening.getContent(), openingVariants);
            if (variantIndex < 0 || assigned.containsKey(variantIndex)) {
                continue;
            }
            branchMapper.setOpeningVariantIndex(branch.getId(), variantIndex);
            branch.setOpeningVariantIndex(variantIndex);
            assigned.put(variantIndex, branch);
        }
        return branches;
    }

    @Transactional
    public AppConversationBranch selectOpeningBranch(
            AppConversation conversation,
            int variantIndex,
            List<String> openingVariants
    ) {
        if (openingVariants == null || variantIndex < 0 || variantIndex >= openingVariants.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "opening variant not found");
        }
        reconcileOpeningVariantBindings(conversation, openingVariants);
        AppConversationBranch existing = branchMapper.findByConversationIdAndOpeningVariantIndex(
                conversation.getId(),
                variantIndex
        );
        if (existing != null) {
            return switchActiveBranch(conversation, existing.getId());
        }

        AppConversationBranch branch = new AppConversationBranch();
        branch.setConversationId(conversation.getId());
        branch.setUserId(conversation.getUserId());
        branch.setOpeningVariantIndex(variantIndex);
        branch.setTitle("Opening " + (variantIndex + 1));
        branch.setDefaultBranch(false);
        branchMapper.insert(branch);
        insertOpeningMessages(branch, openingVariants, variantIndex);
        incrementMemorySourceRevision(conversation.getId(), branch.getId());
        conversationMapper.setActiveBranchId(conversation.getId(), branch.getId());
        conversation.setActiveBranchId(branch.getId());
        return branch;
    }

    public void touchBranch(long branchId) {
        if (branchId > 0) {
            branchMapper.touch(branchId);
        }
    }

    public void incrementMemorySourceRevision(long conversationId, long branchId) {
        if (conversationId <= 0 || branchId <= 0) {
            return;
        }
        if (branchMapper.incrementMemorySourceRevision(conversationId, branchId) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation branch not found");
        }
    }

    public void incrementMemorySourceRevisionForConversation(long conversationId) {
        if (conversationId > 0) {
            branchMapper.incrementMemorySourceRevisionForConversation(conversationId);
        }
    }

    @Transactional
    public AppConversationBranch switchActiveBranch(AppConversation conversation, long branchId) {
        AppConversationBranch target = branchMapper.findByIdForConversation(conversation.getId(), branchId);
        if (target == null || target.getUserId() == null || target.getUserId().longValue() != conversation.getUserId()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "branch not found");
        }
        conversationMapper.setActiveBranchId(conversation.getId(), branchId);
        branchMapper.touch(branchId);
        return target;
    }

    @Transactional
    public AppConversationBranch renameBranch(AppConversation conversation, long branchId, String title) {
        AppConversationBranch branch = requireOwnedBranch(conversation, branchId);
        String safeTitle = title == null ? "" : title.trim();
        if (!StringUtils.hasText(safeTitle)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "branch title is required");
        }
        if (safeTitle.length() > 80) {
            safeTitle = safeTitle.substring(0, 80);
        }
        if (branchMapper.updateTitle(conversation.getId(), branchId, safeTitle) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "branch not found");
        }
        branch.setTitle(safeTitle);
        return branch;
    }

    @Transactional
    public AppConversationBranch deleteBranch(AppConversation conversation, long branchId) {
        AppConversationBranch branch = requireOwnedBranch(conversation, branchId);
        if (branch.isDefaultBranch()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "default branch cannot be deleted");
        }

        AppConversationBranch fallback = null;
        if (branch.getParentBranchId() != null && branch.getParentBranchId() > 0) {
            AppConversationBranch parent = branchMapper.findByIdForConversation(
                    conversation.getId(),
                    branch.getParentBranchId()
            );
            if (isOwnedByUser(parent, conversation.getUserId())) {
                fallback = parent;
            }
        }
        if (fallback == null) {
            fallback = ensureDefaultBranch(conversation.getId(), conversation.getUserId());
        }

        branchMapper.reparentChildren(conversation.getId(), branchId, branch.getParentBranchId());
        if (branchMapper.softDelete(conversation.getId(), branchId) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "branch not found");
        }
        if (conversation.getActiveBranchId() != null && conversation.getActiveBranchId().longValue() == branchId) {
            conversationMapper.setActiveBranchId(conversation.getId(), fallback.getId());
            conversation.setActiveBranchId(fallback.getId());
            branchMapper.touch(fallback.getId());
        }
        memoryCleanupService.clearBranchMemory(
                conversation.getId(),
                branchId,
                conversation.getUserId()
        );
        return fallback;
    }

    private AppConversationBranch requireOwnedBranch(AppConversation conversation, long branchId) {
        if (conversation == null || conversation.getId() == null || conversation.getUserId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        AppConversationBranch branch = branchMapper.findByIdForConversation(conversation.getId(), branchId);
        if (!isOwnedByUser(branch, conversation.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "branch not found");
        }
        return branch;
    }

    @Transactional
    public AppConversationBranch forkFromMessage(
            AppConversation conversation,
            long sourceMessageId,
            Integer variantIndex,
            String title
    ) {
        AppConversationBranch active = requireActiveBranch(conversation);
        AppMessage forkMessage = messageMapper.findById(sourceMessageId);
        if (forkMessage == null
                || forkMessage.getConversationId() == null
                || forkMessage.getConversationId().longValue() != conversation.getId()
                || forkMessage.getBranchId() == null
                || forkMessage.getBranchId().longValue() != active.getId()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }

        AppConversationBranch branch = new AppConversationBranch();
        branch.setConversationId(conversation.getId());
        branch.setUserId(conversation.getUserId());
        branch.setParentBranchId(active.getId());
        branch.setForkMessageId(sourceMessageId);
        branch.setTitle(resolveBranchTitle(conversation.getId(), title));
        branch.setDefaultBranch(false);
        branchMapper.insert(branch);

        copyPrefixIntoBranch(conversation.getId(), active.getId(), branch.getId(), sourceMessageId, variantIndex);
        incrementMemorySourceRevision(conversation.getId(), branch.getId());
        conversationMapper.setActiveBranchId(conversation.getId(), branch.getId());
        return branch;
    }

    private String resolveBranchTitle(long conversationId, String requestedTitle) {
        String safe = requestedTitle == null ? "" : requestedTitle.trim();
        if (StringUtils.hasText(safe)) {
            return safe.length() > 80 ? safe.substring(0, 80) : safe;
        }
        int existingCount = Math.max(1, branchMapper.countByConversationId(conversationId));
        return "分支 " + existingCount;
    }

    private void copyPrefixIntoBranch(
            long conversationId,
            long sourceBranchId,
            long targetBranchId,
            long forkMessageId,
            Integer variantIndex
    ) {
        List<AppMessage> sourceRows = messageMapper.listByConversationBranchAsc(conversationId, sourceBranchId, FORK_COPY_LIMIT);
        Map<String, List<AppMessage>> swipeRowsByRef = indexSwipeRowsByRef(sourceRows);
        Map<Long, Long> copiedIdsBySourceId = new HashMap<>();
        Long previousCopiedId = null;
        boolean copiedFork = false;
        for (AppMessage source : sourceRows) {
            if (source == null || source.getId() == null) {
                continue;
            }
            if (source.getId() > forkMessageId) {
                break;
            }
            if (!includeTimelineMessage(source)) {
                continue;
            }
            ForkSelection selection = currentSelection(source);
            if (source.getId().longValue() == forkMessageId) {
                selection = resolveForkMessageSelection(source, variantIndex);
                copiedFork = true;
            }
            AppMessage copied = copyMessage(
                    source,
                    targetBranchId,
                    previousCopiedId,
                    copiedIdsBySourceId,
                    selection.content(),
                    selection.swipeIndex()
            );
            copyMessageSegments(selection.segmentSourceMessageId(), copied.getId());
            copySwipeVariants(
                    source,
                    copied,
                    targetBranchId,
                    swipeRowsByRef.get(source.getStMessageRef()),
                    selection.swipeIndex()
            );
            previousCopiedId = copied.getId();
            copiedIdsBySourceId.put(source.getId(), copied.getId());
        }
        if (!copiedFork) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "fork point not found in active branch");
        }
    }

    private ForkSelection currentSelection(AppMessage source) {
        int swipeIndex = source != null && source.getSwipeIndex() != null
                ? Math.max(0, source.getSwipeIndex())
                : 0;
        return new ForkSelection(
                source == null ? null : source.getContent(),
                swipeIndex,
                source == null ? null : source.getId()
        );
    }

    private ForkSelection resolveForkMessageSelection(AppMessage source, Integer variantIndex) {
        ForkSelection current = currentSelection(source);
        if (!"assistant".equalsIgnoreCase(source.getRole()) || variantIndex == null || variantIndex < 0) {
            return current;
        }
        String ref = source.getStMessageRef();
        if (!StringUtils.hasText(ref)) {
            return current;
        }
        AppMessage variant = messageMapper.findByStMessageRefAndSwipeIndexAndBranch(ref, variantIndex, source.getBranchId());
        if (variant == null || variant.getContent() == null || variant.getContent().isBlank()) {
            return current;
        }
        return new ForkSelection(variant.getContent(), variantIndex, variant.getId());
    }

    private AppMessage copyMessage(
            AppMessage source,
            long targetBranchId,
            Long previousCopiedId,
            Map<Long, Long> copiedIdsBySourceId,
            String content,
            int swipeIndex
    ) {
        AppMessage row = new AppMessage();
        row.setUserId(source.getUserId());
        row.setConversationId(source.getConversationId());
        row.setBranchId(targetBranchId);
        row.setParentMessageId(previousCopiedId);
        row.setRole(source.getRole());
        row.setMessageKind(source.getMessageKind());
        row.setContinueFromMessageId(remapNullableId(source.getContinueFromMessageId(), copiedIdsBySourceId));
        row.setClientMessageId(source.getClientMessageId() == null ? null : source.getClientMessageId() + "_b" + targetBranchId);
        row.setContent(content);
        row.setVoiceUrl(source.getVoiceUrl());
        row.setVoiceDurationMs(source.getVoiceDurationMs());
        row.setStatus(source.getStatus());
        row.setErrorCode(source.getErrorCode());
        row.setTraceId(source.getTraceId());
        row.setSpeakerMemberId(source.getSpeakerMemberId());
        row.setSpeakerNameSnapshot(source.getSpeakerNameSnapshot());
        row.setSpeakerAvatarSnapshot(source.getSpeakerAvatarSnapshot());
        if ("assistant".equalsIgnoreCase(source.getRole()) && source.getContent() != null && !source.getContent().isBlank()) {
            row.setSwipeIndex(Math.max(0, swipeIndex));
        }
        messageMapper.insert(row);
        messageMapper.incrementTotalMessageCounter();
        if ("assistant".equalsIgnoreCase(row.getRole()) && row.getContent() != null && !row.getContent().isBlank()) {
            String ref = "root:" + row.getId();
            messageMapper.updateVariantMeta(row.getId(), ref, row.getSwipeIndex(), row.getTraceId());
            row.setStMessageRef(ref);
        }
        return row;
    }

    private Map<String, List<AppMessage>> indexSwipeRowsByRef(List<AppMessage> rows) {
        Map<String, List<AppMessage>> indexed = new HashMap<>();
        if (rows == null) {
            return indexed;
        }
        for (AppMessage row : rows) {
            String ref = row == null || row.getStMessageRef() == null ? "" : row.getStMessageRef().trim();
            if (!ref.isBlank()) {
                indexed.computeIfAbsent(ref, ignored -> new ArrayList<>()).add(row);
            }
        }
        return indexed;
    }

    private void copySwipeVariants(
            AppMessage sourceRoot,
            AppMessage copiedRoot,
            long targetBranchId,
            List<AppMessage> sourceVariants,
            int activeSwipeIndex
    ) {
        if (!"assistant".equalsIgnoreCase(sourceRoot.getRole())
                || copiedRoot == null
                || copiedRoot.getId() == null
                || sourceVariants == null
                || sourceVariants.size() <= 1) {
            return;
        }

        Map<Integer, AppMessage> variantsByIndex = new LinkedHashMap<>();
        for (AppMessage variant : sourceVariants) {
            if (!isUsableSwipeVariant(variant)) {
                continue;
            }
            int index = Math.max(0, variant.getSwipeIndex());
            if (variant.getId() != null && variant.getId().equals(sourceRoot.getId())) {
                variantsByIndex.put(index, variant);
            } else {
                variantsByIndex.putIfAbsent(index, variant);
            }
        }

        String copiedRef = "root:" + copiedRoot.getId();
        for (Map.Entry<Integer, AppMessage> entry : variantsByIndex.entrySet()) {
            int index = entry.getKey();
            if (index == activeSwipeIndex) {
                continue;
            }
            AppMessage sourceVariant = entry.getValue();
            AppMessage copiedVariant = new AppMessage();
            copiedVariant.setUserId(sourceVariant.getUserId());
            copiedVariant.setConversationId(sourceVariant.getConversationId());
            copiedVariant.setBranchId(targetBranchId);
            copiedVariant.setParentMessageId(copiedRoot.getId());
            copiedVariant.setRole("assistant");
            copiedVariant.setMessageKind(sourceVariant.getMessageKind());
            copiedVariant.setContinueFromMessageId(copiedRoot.getContinueFromMessageId());
            copiedVariant.setClientMessageId("swipe_" + copiedRoot.getId() + "_idx_" + index);
            copiedVariant.setContent(sourceVariant.getContent());
            copiedVariant.setVoiceUrl(sourceVariant.getVoiceUrl());
            copiedVariant.setVoiceDurationMs(sourceVariant.getVoiceDurationMs());
            copiedVariant.setStMessageRef(copiedRef);
            copiedVariant.setSwipeIndex(index);
            copiedVariant.setStatus(sourceVariant.getStatus());
            copiedVariant.setErrorCode(sourceVariant.getErrorCode());
            copiedVariant.setTraceId(sourceVariant.getTraceId());
            copiedVariant.setSpeakerMemberId(sourceVariant.getSpeakerMemberId());
            copiedVariant.setSpeakerNameSnapshot(sourceVariant.getSpeakerNameSnapshot());
            copiedVariant.setSpeakerAvatarSnapshot(sourceVariant.getSpeakerAvatarSnapshot());
            messageMapper.insert(copiedVariant);
            messageMapper.incrementTotalMessageCounter();
            copyMessageSegments(sourceVariant.getId(), copiedVariant.getId());
        }
    }

    private void copyMessageSegments(Long sourceMessageId, Long targetMessageId) {
        if (sourceMessageId == null || sourceMessageId <= 0 || targetMessageId == null || targetMessageId <= 0) {
            return;
        }
        List<AppMessageSegment> sourceSegments = segmentMapper.listByMessageId(sourceMessageId);
        if (sourceSegments == null || sourceSegments.isEmpty()) {
            return;
        }
        int index = 0;
        for (AppMessageSegment source : sourceSegments) {
            if (source == null || source.getContent() == null || source.getContent().isBlank()) {
                continue;
            }
            AppMessageSegment copied = new AppMessageSegment();
            copied.setMessageId(targetMessageId);
            copied.setSegmentIndex(index++);
            copied.setSegmentType(source.getSegmentType());
            copied.setSpeakerMemberId(source.getSpeakerMemberId());
            copied.setSpeakerNameSnapshot(source.getSpeakerNameSnapshot());
            copied.setSpeakerAvatarSnapshot(source.getSpeakerAvatarSnapshot());
            copied.setContent(source.getContent());
            copied.setStatus(StringUtils.hasText(source.getStatus()) ? source.getStatus() : "SUCCESS");
            segmentMapper.insert(copied);
        }
    }

    private boolean isUsableSwipeVariant(AppMessage message) {
        if (message == null
                || !"assistant".equalsIgnoreCase(message.getRole())
                || message.getSwipeIndex() == null
                || message.getContent() == null
                || message.getContent().isBlank()) {
            return false;
        }
        String status = message.getStatus() == null ? "" : message.getStatus();
        return "SUCCESS".equalsIgnoreCase(status) || "STOPPED".equalsIgnoreCase(status);
    }

    private void insertOpeningMessages(
            AppConversationBranch branch,
            List<String> openingVariants,
            int activeVariantIndex
    ) {
        AppMessage root = new AppMessage();
        root.setUserId(branch.getUserId());
        root.setConversationId(branch.getConversationId());
        root.setBranchId(branch.getId());
        root.setRole("assistant");
        root.setClientMessageId("opening_branch_" + branch.getId());
        root.setContent(openingVariants.get(activeVariantIndex));
        root.setSwipeIndex(activeVariantIndex);
        root.setStatus("SUCCESS");
        root.setTraceId("opening_branch");
        messageMapper.insert(root);
        messageMapper.incrementTotalMessageCounter();

        String rootRef = "root:" + root.getId();
        messageMapper.updateVariantMeta(root.getId(), rootRef, activeVariantIndex, root.getTraceId());
        root.setStMessageRef(rootRef);

        for (int i = 0; i < openingVariants.size(); i++) {
            if (i == activeVariantIndex) {
                continue;
            }
            AppMessage variant = new AppMessage();
            variant.setUserId(branch.getUserId());
            variant.setConversationId(branch.getConversationId());
            variant.setBranchId(branch.getId());
            variant.setParentMessageId(root.getId());
            variant.setRole("assistant");
            variant.setClientMessageId("swipe_" + root.getId() + "_idx_" + i);
            variant.setContent(openingVariants.get(i));
            variant.setStMessageRef(rootRef);
            variant.setSwipeIndex(i);
            variant.setStatus("SUCCESS");
            variant.setTraceId("opening_branch");
            messageMapper.insert(variant);
            messageMapper.incrementTotalMessageCounter();
        }
    }

    private int findOpeningVariantIndex(String content, List<String> openingVariants) {
        String normalized = normalizeOpeningText(content);
        if (normalized.isBlank() || openingVariants == null) {
            return -1;
        }
        for (int i = 0; i < openingVariants.size(); i++) {
            if (normalized.equals(normalizeOpeningText(openingVariants.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private String normalizeOpeningText(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private boolean belongsToConversationUser(AppConversationBranch branch, AppConversation conversation) {
        return branch != null
                && branch.getConversationId() != null
                && branch.getConversationId().equals(conversation.getId())
                && isOwnedByUser(branch, conversation.getUserId());
    }

    private boolean isOwnedByUser(AppConversationBranch branch, Long userId) {
        return branch != null
                && userId != null
                && branch.getUserId() != null
                && branch.getUserId().equals(userId);
    }

    private boolean includeTimelineMessage(AppMessage message) {
        if (message == null) {
            return false;
        }
        String status = message.getStatus() == null ? "" : message.getStatus();
        if ("FAILED".equalsIgnoreCase(status) || "DELETED".equalsIgnoreCase(status)) {
            return false;
        }
        if ("user".equalsIgnoreCase(message.getRole())) {
            return true;
        }
        if (!"assistant".equalsIgnoreCase(message.getRole())) {
            return false;
        }
        if (message.getContent() == null || message.getContent().isBlank()) {
            return false;
        }
        if (!"SUCCESS".equalsIgnoreCase(status) && !"STOPPED".equalsIgnoreCase(status)) {
            return false;
        }
        String ref = message.getStMessageRef();
        if (ref != null && ref.startsWith("root:")) {
            try {
                long rootId = Long.parseLong(ref.substring("root:".length()));
                return message.getId() != null && message.getId().longValue() == rootId;
            } catch (Exception ignored) {
                return true;
            }
        }
        return true;
    }

    private Long remapNullableId(Long sourceId, Map<Long, Long> copiedIdsBySourceId) {
        if (sourceId == null || sourceId <= 0) {
            return null;
        }
        return copiedIdsBySourceId.get(sourceId);
    }

    private record ForkSelection(String content, int swipeIndex, Long segmentSourceMessageId) {
    }
}
