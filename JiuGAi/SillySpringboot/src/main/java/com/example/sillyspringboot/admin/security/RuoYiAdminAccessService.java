package com.example.sillyspringboot.admin.security;

import com.example.sillyspringboot.admin.service.AdminIdentityService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
public class RuoYiAdminAccessService {

    private final AdminIdentityService identityService;
    private final PasswordEncoder passwordEncoder;

    public RuoYiAdminAccessService(AdminIdentityService identityService, PasswordEncoder passwordEncoder) {
        this.identityService = identityService;
        this.passwordEncoder = passwordEncoder;
    }

    public AdminSession authenticate(String username, String rawPassword) {
        AdminIdentityService.AdminAccountBundle bundle = identityService.loadAccountBundleByUsername(username);
        if (bundle == null
                || bundle.account() == null
                || rawPassword == null
                || bundle.account().getEncodedPassword() == null
                || !AdminIdentityService.STATUS_ACTIVE.equalsIgnoreCase(bundle.account().getStatus())
                || !passwordEncoder.matches(rawPassword, bundle.account().getEncodedPassword())) {
            return null;
        }
        return toSession(bundle);
    }

    public AdminSession loadSession(String username) {
        AdminIdentityService.AdminAccountBundle bundle = identityService.loadAccountBundleByUsername(username);
        if (bundle == null
                || bundle.account() == null
                || !AdminIdentityService.STATUS_ACTIVE.equalsIgnoreCase(bundle.account().getStatus())) {
            return null;
        }
        return toSession(bundle);
    }

    public boolean hasAnyPermission(Collection<String> ownedPermissions, String... requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.length == 0) {
            return true;
        }
        return hasAnyPermission(ownedPermissions, List.of(requiredPermissions));
    }

    public boolean hasAnyPermission(Collection<String> ownedPermissions, Collection<String> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return true;
        }
        if (ownedPermissions == null || ownedPermissions.isEmpty()) {
            return false;
        }
        if (ownedPermissions.contains(AdminPermissionCatalog.ALL_PERMISSION)) {
            return true;
        }
        for (String permission : requiredPermissions) {
            if (permission != null && ownedPermissions.contains(permission)) {
                return true;
            }
        }
        return false;
    }

    private AdminSession toSession(AdminIdentityService.AdminAccountBundle bundle) {
        return new AdminSession(
                bundle.account().getId(),
                bundle.account().getUsername(),
                bundle.account().getNickName(),
                bundle.roles().stream().map(role -> role.getRoleKey()).toList(),
                bundle.permissions()
        );
    }

    public static final class AdminSession {
        private final Long id;
        private final String username;
        private final String nickName;
        private final List<String> roles;
        private final List<String> permissions;

        public AdminSession(Long id, String username, String nickName, List<String> roles, List<String> permissions) {
            this.id = id;
            this.username = username;
            this.nickName = nickName;
            this.roles = List.copyOf(Objects.requireNonNullElse(roles, List.of()));
            this.permissions = List.copyOf(Objects.requireNonNullElse(permissions, List.of()));
        }

        public Long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getNickName() {
            return nickName;
        }

        public List<String> getRoles() {
            return roles;
        }

        public List<String> getPermissions() {
            return permissions;
        }
    }
}
