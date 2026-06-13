package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.compat.h5.entity.AppH5Profile;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ClientUidMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ProfileMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminUserDisplayService {

    private final AppUserMapper userMapper;
    private final AppH5ProfileMapper profileMapper;
    private final AppH5UserProfileExtMapper profileExtMapper;
    private final AppH5ClientUidMapper clientUidMapper;

    public AdminUserDisplayService(
            AppUserMapper userMapper,
            AppH5ProfileMapper profileMapper,
            AppH5UserProfileExtMapper profileExtMapper,
            AppH5ClientUidMapper clientUidMapper
    ) {
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
        this.profileExtMapper = profileExtMapper;
        this.clientUidMapper = clientUidMapper;
    }

    public UserDisplayInfo resolve(Long userId) {
        if (userId == null || userId <= 0) {
            return UserDisplayInfo.empty();
        }
        return resolveAll(List.of(userId)).getOrDefault(userId, UserDisplayInfo.of(userId, "user#" + userId, ""));
    }

    public Map<Long, UserDisplayInfo> resolveAll(Collection<Long> userIds) {
        Map<Long, UserDisplayInfo> resolved = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return resolved;
        }
        for (Long userId : userIds) {
            if (userId == null || userId <= 0 || resolved.containsKey(userId)) {
                continue;
            }
            resolved.put(userId, resolveSingle(userId));
        }
        return resolved;
    }

    private UserDisplayInfo resolveSingle(long userId) {
        AppUser user = userMapper.findById(userId);
        AppH5Profile profile = profileMapper == null ? null : profileMapper.findByUserId(userId);
        AppH5UserProfileExt profileExt = profileExtMapper == null ? null : profileExtMapper.findByUserId(userId);
        String clientUid = clientUidMapper == null ? null : clientUidMapper.findAnyClientUidByUserId(userId);

        String firstName = trimToNull(user == null ? null : user.getFirstName());
        String lastName = trimToNull(user == null ? null : user.getLastName());
        String fullName = trimToNull((firstName == null ? "" : firstName) + (lastName == null ? "" : (" " + lastName)));
        String primary = firstNonBlank(
                trimToNull(profileExt == null ? null : profileExt.getNickname()),
                trimToNull(profile == null ? null : profile.getDisplayName()),
                trimToNull(user == null ? null : user.getUsername()),
                fullName,
                trimToNull(clientUid),
                buildFallbackId(userId, user)
        );
        String fallbackId = firstNonBlank(trimToNull(clientUid), buildFallbackId(userId, user));
        String subLabel = primary.equals(fallbackId) ? "" : fallbackId;
        return UserDisplayInfo.of(userId, primary, subLabel);
    }

    private static String buildFallbackId(long userId, AppUser user) {
        if (user != null && user.getTelegramUserId() != null && user.getTelegramUserId() > 0) {
            return "tg_" + user.getTelegramUserId();
        }
        return "user#" + userId;
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return "";
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record UserDisplayInfo(Long userId, String displayName, String subLabel) {

        public static UserDisplayInfo of(Long userId, String displayName, String subLabel) {
            return new UserDisplayInfo(userId, blank(displayName), blank(subLabel));
        }

        public static UserDisplayInfo empty() {
            return new UserDisplayInfo(null, "", "");
        }

        private static String blank(String value) {
            return value == null ? "" : value;
        }
    }
}
