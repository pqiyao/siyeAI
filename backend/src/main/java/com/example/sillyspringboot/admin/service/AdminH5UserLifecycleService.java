package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.mapper.AdminH5UserCleanupMapper;
import com.example.sillyspringboot.admin.mapper.AdminH5UserMapper;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminH5UserLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AdminH5UserLifecycleService.class);
    private static final String H5_UPLOAD_MARKER = "/uploads/h5/";

    private final AdminH5UserMapper adminH5UserMapper;
    private final AdminH5UserCleanupMapper cleanupMapper;
    private final StAdapter stAdapter;
    private final TransactionTemplate transactionTemplate;
    private final Path h5UploadRoot;

    public AdminH5UserLifecycleService(
            AdminH5UserMapper adminH5UserMapper,
            AdminH5UserCleanupMapper cleanupMapper,
            StAdapter stAdapter,
            PlatformTransactionManager transactionManager,
            @Value("${app.upload.dir:${user.dir}/data/uploads}") String uploadRootDir
    ) {
        this.adminH5UserMapper = adminH5UserMapper;
        this.cleanupMapper = cleanupMapper;
        this.stAdapter = stAdapter;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.h5UploadRoot = Path.of(uploadRootDir).toAbsolutePath().normalize().resolve("h5").normalize();
    }

    public Map<String, Object> deleteUsers(List<Long> userIds) {
        int requested = userIds == null ? 0 : userIds.size();
        int deleted = 0;
        int skipped = 0;
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Long userId : userIds) {
            if (userId == null || userId <= 0) {
                skipped++;
                continue;
            }
            Map<String, Object> detail = adminH5UserMapper.findDetail(userId);
            if (detail == null || detail.isEmpty()) {
                skipped++;
                continue;
            }
            try {
                transactionTemplate.executeWithoutResult(status -> deleteSingleUser(userId));
                deleted++;
            } catch (RuntimeException ex) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", userId);
                item.put("reason", clip(rootMessage(ex), 180));
                failed.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requested", requested);
        result.put("deleted", deleted);
        result.put("skipped", skipped);
        result.put("failedCount", failed.size());
        result.put("failed", failed);
        return result;
    }

    public void deleteUserById(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        transactionTemplate.executeWithoutResult(status -> deleteSingleUser(userId));
    }

    private void deleteSingleUser(long userId) {
        List<Map<String, Object>> stChats = cleanupMapper.listConversationStRefs(userId);
        List<Map<String, Object>> ownedCharacters = cleanupMapper.listOwnedCharacterCleanupRows(userId);
        Set<String> localAssetUrls = collectLocalAssetUrls(userId, ownedCharacters);

        deleteStChats(stChats);
        deleteStCharacters(ownedCharacters);

        cleanupMapper.deleteSupportTicketMessagesByUser(userId);
        cleanupMapper.deleteSupportTicketsByUser(userId);
        cleanupMapper.deleteUserMessages(userId);
        cleanupMapper.deleteUserNoticeReads(userId);
        cleanupMapper.deleteUserNoticeReadState(userId);
        cleanupMapper.deleteCharacterFavorites(userId);
        cleanupMapper.deleteCharacterFavoritesForOwnedCharacters(userId);
        cleanupMapper.deleteCharacterVotes(userId);
        cleanupMapper.deleteCharacterVotesForOwnedCharacters(userId);
        cleanupMapper.deleteWalletLedger(userId);
        cleanupMapper.deletePaymentOrders(userId);
        cleanupMapper.deleteGenerationTasksByUser(userId);
        cleanupMapper.deleteMessagesByUser(userId);
        cleanupMapper.deleteConversationBindingsByUser(userId);
        cleanupMapper.deleteConversationArchivesByUser(userId);
        cleanupMapper.deleteConversationMemoriesByUser(userId);
        cleanupMapper.deleteConversationIdempotencyByUser(userId);
        cleanupMapper.deleteConversationsByUser(userId);
        cleanupMapper.deleteUserSessions(userId);
        cleanupMapper.deleteH5UserAiProvider(userId);
        cleanupMapper.deleteEntitlementAuditLogsByUser(userId);
        cleanupMapper.deleteVisitorDevicesByUser(userId);
        cleanupMapper.deleteClientUidBindings(userId);
        cleanupMapper.deleteUserIdentities(userId);
        cleanupMapper.deleteH5Profile(userId);
        cleanupMapper.deleteH5ProfileExt(userId);
        cleanupMapper.deleteCharacterReviewLogsByOwner(userId);
        cleanupMapper.deleteLorebookEntriesForOwnedCharacters(userId);
        cleanupMapper.deleteOwnedCharacters(userId);
        cleanupMapper.deleteAppUser(userId);
        deleteLocalUploads(localAssetUrls);
    }

    private Set<String> collectLocalAssetUrls(long userId, List<Map<String, Object>> ownedCharacters) {
        Set<String> urls = new LinkedHashSet<>();
        List<String> userAssets = cleanupMapper.listUserAssetUrls(userId);
        if (userAssets != null) {
            for (String url : userAssets) {
                addNonBlank(urls, url);
            }
        }
        if (ownedCharacters != null) {
            for (Map<String, Object> row : ownedCharacters) {
                addNonBlank(urls, text(row.get("avatarUrl")));
                addNonBlank(urls, text(row.get("coverUrl")));
                addNonBlank(urls, text(row.get("chatBackgroundUrl")));
            }
        }
        return urls;
    }

    private void deleteStChats(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String avatarUrl = text(row.get("stAvatarUrl"));
            String chatFileName = text(row.get("stChatFileName"));
            if (avatarUrl.isBlank() || chatFileName.isBlank()) {
                continue;
            }
            String key = avatarUrl + "\n" + chatFileName;
            if (seen.add(key)) {
                stAdapter.deleteChat(avatarUrl, chatFileName);
            }
        }
    }

    private void deleteStCharacters(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String avatarUrl = text(row.get("stAvatarUrl"));
            if (!avatarUrl.isBlank() && seen.add(avatarUrl)) {
                stAdapter.deleteCharacter(avatarUrl, true);
            }
        }
    }

    private void deleteLocalUploads(Set<String> assetUrls) {
        if (assetUrls == null || assetUrls.isEmpty()) {
            return;
        }
        for (String assetUrl : assetUrls) {
            Path path = resolveH5UploadPath(assetUrl);
            if (path == null) {
                continue;
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException ex) {
                log.warn("delete h5 upload failed path={}", path, ex);
            }
        }
    }

    private Path resolveH5UploadPath(String rawUrl) {
        String raw = rawUrl == null ? "" : rawUrl.trim();
        if (raw.isBlank()) {
            return null;
        }
        String pathPart = raw.replace('\\', '/');
        if (pathPart.startsWith("http://") || pathPart.startsWith("https://")) {
            try {
                pathPart = URI.create(pathPart).getPath();
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        int queryIndex = pathPart.indexOf('?');
        if (queryIndex >= 0) {
            pathPart = pathPart.substring(0, queryIndex);
        }
        String filePart;
        int markerIndex = pathPart.indexOf(H5_UPLOAD_MARKER);
        if (markerIndex >= 0) {
            filePart = pathPart.substring(markerIndex + H5_UPLOAD_MARKER.length());
        } else if (pathPart.startsWith("uploads/h5/")) {
            filePart = pathPart.substring("uploads/h5/".length());
        } else {
            return null;
        }
        if (filePart.isBlank()) {
            return null;
        }
        String decoded = URLDecoder.decode(filePart, StandardCharsets.UTF_8);
        Path target = h5UploadRoot.resolve(decoded).normalize();
        return target.startsWith(h5UploadRoot) ? target : null;
    }

    private static void addNonBlank(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null && cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor == null ? "" : cursor.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static String clip(String value, int maxLength) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
