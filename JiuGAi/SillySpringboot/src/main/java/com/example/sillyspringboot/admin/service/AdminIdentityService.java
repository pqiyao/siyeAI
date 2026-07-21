package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.config.RuoYiAdminProperties;
import com.example.sillyspringboot.admin.entity.AppAdminAccount;
import com.example.sillyspringboot.admin.entity.AppAdminPermissionChangeLog;
import com.example.sillyspringboot.admin.entity.AppAdminRole;
import com.example.sillyspringboot.admin.mapper.AppAdminAccountMapper;
import com.example.sillyspringboot.admin.mapper.AppAdminAccountRoleMapper;
import com.example.sillyspringboot.admin.mapper.AppAdminPermissionChangeLogMapper;
import com.example.sillyspringboot.admin.mapper.AppAdminRoleMapper;
import com.example.sillyspringboot.admin.security.AdminPermissionCatalog;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AdminIdentityService implements ApplicationRunner {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String SUPER_ADMIN_ROLE = "super-admin";
    private static final String TARGET_ROLE = "ROLE";
    private static final String TARGET_ACCOUNT = "ACCOUNT";
    private static final String ACTION_ROLE_CREATE = "ROLE_CREATE";
    private static final String ACTION_ROLE_UPDATE = "ROLE_UPDATE";
    private static final String ACTION_ROLE_STATUS = "ROLE_STATUS";
    private static final String ACTION_ROLE_DELETE = "ROLE_DELETE";
    private static final String ACTION_ACCOUNT_CREATE = "ACCOUNT_CREATE";
    private static final String ACTION_ACCOUNT_UPDATE = "ACCOUNT_UPDATE";
    private static final String ACTION_ACCOUNT_STATUS = "ACCOUNT_STATUS";
    private static final String ACTION_ACCOUNT_DELETE = "ACCOUNT_DELETE";

    private final AppAdminAccountMapper accountMapper;
    private final AppAdminRoleMapper roleMapper;
    private final AppAdminAccountRoleMapper accountRoleMapper;
    private final AppAdminPermissionChangeLogMapper permissionChangeLogMapper;
    private final AdminPermissionCatalog permissionCatalog;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final RuoYiAdminProperties props;

    public AdminIdentityService(
            AppAdminAccountMapper accountMapper,
            AppAdminRoleMapper roleMapper,
            AppAdminAccountRoleMapper accountRoleMapper,
            AppAdminPermissionChangeLogMapper permissionChangeLogMapper,
            AdminPermissionCatalog permissionCatalog,
            ObjectMapper objectMapper,
            PasswordEncoder passwordEncoder,
            RuoYiAdminProperties props
    ) {
        this.accountMapper = accountMapper;
        this.roleMapper = roleMapper;
        this.accountRoleMapper = accountRoleMapper;
        this.permissionChangeLogMapper = permissionChangeLogMapper;
        this.permissionCatalog = permissionCatalog;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureBuiltInRoles();
        ensureSeedAccount(
                props.getUsername(),
                props.getNickName(),
                props.getEncodedPassword(),
                resolveConfiguredRoleKeys(props.getRole(), props.getRoles())
        );
        for (RuoYiAdminProperties.Account account : props.getAccounts()) {
            if (account == null || !account.isEnabled()) {
                continue;
            }
            ensureSeedAccount(
                    account.getUsername(),
                    account.getNickName(),
                    account.getEncodedPassword(),
                    resolveConfiguredRoleKeys(account.getRole(), account.getRoles())
            );
        }
    }

    @Transactional(readOnly = true)
    public AdminAccountBundle loadAccountBundleByUsername(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return null;
        }
        AppAdminAccount account = accountMapper.findByUsername(normalized);
        if (account == null) {
            return null;
        }
        List<AppAdminRole> roles = accountRoleMapper.listRolesByAccountId(account.getId());
        List<AppAdminRole> effectiveRoles = effectiveRoles(roles);
        return new AdminAccountBundle(account, effectiveRoles, mergePermissions(effectiveRoles));
    }

    @Transactional
    public void recordLoginSuccess(long accountId, String loginIp) {
        accountMapper.updateLastLogin(accountId, trimToNull(loginIp));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildRoleMeta() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("permissionGroups", permissionCatalog.permissionGroups().stream().map(group -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", group.key());
            row.put("label", group.label());
            row.put("description", group.description());
            row.put("sortOrder", group.sortOrder());
            row.put("items", group.items().stream().map(item -> {
                Map<String, Object> inner = new LinkedHashMap<>();
                inner.put("key", item.key());
                inner.put("label", item.label());
                inner.put("pageKey", item.pageKey());
                inner.put("pageLabel", item.pageLabel());
                inner.put("actionKey", item.actionKey());
                inner.put("actionLabel", item.actionLabel());
                inner.put("riskLevel", item.riskLevel().name());
                inner.put("riskLabel", item.riskLevel().label());
                inner.put("riskSortOrder", item.riskLevel().sortOrder());
                inner.put("description", item.description());
                inner.put("recommendedRoleKeys", item.recommendedRoleKeys());
                return inner;
            }).toList());
            return row;
        }).toList());
        data.put("roleTemplates", permissionCatalog.builtInRoleTemplates().values().stream().map(template -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", template.key());
            row.put("label", template.label());
            row.put("description", template.description());
            row.put("permissionKeys", template.permissionKeys());
            row.put("fullAccess", template.fullAccess());
            row.put("sortOrder", template.sortOrder());
            return row;
        }).toList());
        data.put("builtInRoleKeys", List.copyOf(permissionCatalog.builtInRoleTemplates().keySet()));
        return data;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildAccountMeta() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("statusOptions", List.of(
                Map.of("value", STATUS_ACTIVE, "label", "启用"),
                Map.of("value", STATUS_DISABLED, "label", "停用")
        ));
        data.put("roles", listRoleOptions(true));
        data.put("permissionGroups", buildPermissionGroupsMeta());
        return data;
    }

    @Transactional(readOnly = true)
    public long countRoles(String keyword, Boolean enabled) {
        return roleMapper.countList(trimToNull(keyword), enabled);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRoles(String keyword, Boolean enabled, int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePageNum - 1) * safePageSize;
        return roleMapper.listPage(trimToNull(keyword), enabled, offset, safePageSize)
                .stream()
                .map(this::toRoleSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRole(long id) {
        AppAdminRole role = requireRole(id);
        return toRoleDetail(role);
    }

    @Transactional
    public Map<String, Object> saveRole(RoleSaveCommand command, String operator) {
        List<String> permissions = normalizePermissions(command.permissionKeys());
        AppAdminRole existing = command.id() == null ? null : requireRole(command.id());
        Map<String, Object> beforeSnapshot = existing == null ? null : rolePermissionSnapshot(existing);
        boolean builtIn = existing != null && Boolean.TRUE.equals(existing.getBuiltIn());
        String roleKey = normalizeRoleKey(command.roleKey());
        if (existing == null) {
            if (roleKey == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "roleKey required");
            }
            ensureRoleKeyUnique(roleKey, null);
        } else {
            roleKey = existing.getRoleKey();
        }

        if (SUPER_ADMIN_ROLE.equals(roleKey)) {
            permissions = List.of(AdminPermissionCatalog.ALL_PERMISSION);
        } else if (permissions.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "At least one permission is required");
        }

        AppAdminRole row = existing == null ? new AppAdminRole() : existing;
        row.setRoleKey(roleKey);
        row.setRoleName(requireText(command.roleName(), 120, "roleName required"));
        row.setPermissionsJson(writeJson(permissions));
        row.setEnabled(existing != null && SUPER_ADMIN_ROLE.equals(existing.getRoleKey()) ? true : defaultBoolean(command.enabled(), true));
        row.setBuiltIn(existing != null ? existing.getBuiltIn() : builtIn);
        row.setSortOrder(command.sortOrder() == null ? 0 : Math.max(command.sortOrder(), 0));
        row.setRemark(limitText(command.remark(), 255));
        row.setUpdatedBy(trimToNull(operator));
        if (existing == null) {
            row.setCreatedBy(trimToNull(operator));
            row.setBuiltIn(false);
            roleMapper.insert(row);
        } else {
            if (builtIn && SUPER_ADMIN_ROLE.equals(existing.getRoleKey())) {
                row.setEnabled(true);
            }
            roleMapper.update(row);
        }
        AppAdminRole saved = requireRole(row.getId());
        Map<String, Object> afterSnapshot = rolePermissionSnapshot(saved);
        recordPermissionChangeIfChanged(
                TARGET_ROLE,
                saved.getId(),
                saved.getRoleKey(),
                saved.getRoleName(),
                existing == null ? ACTION_ROLE_CREATE : ACTION_ROLE_UPDATE,
                operator,
                beforeSnapshot,
                afterSnapshot,
                roleChangeSummary(existing == null ? ACTION_ROLE_CREATE : ACTION_ROLE_UPDATE, saved)
        );
        return toRoleDetail(saved);
    }

    @Transactional
    public Map<String, Object> updateRoleStatus(long id, boolean enabled, String operator) {
        AppAdminRole role = requireRole(id);
        if (SUPER_ADMIN_ROLE.equals(role.getRoleKey()) && !enabled) {
            throw new BusinessException(ErrorCode.CONFLICT, "super-admin role cannot be disabled");
        }
        Map<String, Object> beforeSnapshot = rolePermissionSnapshot(role);
        roleMapper.updateStatus(id, enabled, trimToNull(operator));
        AppAdminRole saved = requireRole(id);
        Map<String, Object> afterSnapshot = rolePermissionSnapshot(saved);
        recordPermissionChangeIfChanged(
                TARGET_ROLE,
                saved.getId(),
                saved.getRoleKey(),
                saved.getRoleName(),
                ACTION_ROLE_STATUS,
                operator,
                beforeSnapshot,
                afterSnapshot,
                roleChangeSummary(ACTION_ROLE_STATUS, saved)
        );
        return toRoleDetail(saved);
    }

    @Transactional
    public void deleteRoles(List<Long> ids, String operator) {
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            AppAdminRole role = requireRole(id);
            if (Boolean.TRUE.equals(role.getBuiltIn()) || SUPER_ADMIN_ROLE.equals(role.getRoleKey())) {
                throw new BusinessException(ErrorCode.CONFLICT, "Built-in roles cannot be deleted");
            }
            if (accountRoleMapper.countAccountsByRoleId(id) > 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "Role is assigned to admin accounts");
            }
            Map<String, Object> beforeSnapshot = rolePermissionSnapshot(role);
            roleMapper.deleteById(id);
            recordPermissionChange(
                    TARGET_ROLE,
                    role.getId(),
                    role.getRoleKey(),
                    role.getRoleName(),
                    ACTION_ROLE_DELETE,
                    operator,
                    beforeSnapshot,
                    null,
                    roleChangeSummary(ACTION_ROLE_DELETE, role)
            );
        }
    }

    @Transactional(readOnly = true)
    public long countAccounts(String keyword, String status) {
        return accountMapper.countList(trimToNull(keyword), normalizeStatusOptional(status));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAccounts(String keyword, String status, int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePageNum - 1) * safePageSize;
        return accountMapper.listPage(trimToNull(keyword), normalizeStatusOptional(status), offset, safePageSize);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAccount(long id) {
        AppAdminAccount account = requireAccount(id);
        return toAccountDetail(account);
    }

    @Transactional
    public Map<String, Object> saveAccount(AccountSaveCommand command, String operator, Long currentAccountId) {
        AppAdminAccount existing = command.id() == null ? null : requireAccount(command.id());
        Map<String, Object> beforeSnapshot = existing == null ? null : accountPermissionSnapshot(existing);
        List<Long> roleIds = normalizeRoleIds(command.roleIds());
        if (roleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "At least one role is required");
        }

        List<AppAdminRole> roles = loadRoles(roleIds);
        String nextStatus = normalizeStatus(command.status());
        String username = existing == null ? normalizeUsername(command.username()) : existing.getUsername();
        if (username == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "username required");
        }
        ensureUsernameUnique(username, existing == null ? null : existing.getId());

        if (existing == null) {
            String rawPassword = command.password();
            if (rawPassword == null || rawPassword.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "password required");
            }
            AppAdminAccount row = new AppAdminAccount();
            row.setUsername(username);
            row.setNickName(requireText(command.nickName(), 120, "nickName required"));
            row.setEncodedPassword(passwordEncoder.encode(rawPassword.trim()));
            row.setStatus(nextStatus);
            row.setBuiltIn(false);
            row.setMustResetPassword(defaultBoolean(command.mustResetPassword(), false));
            row.setRemark(limitText(command.remark(), 255));
            row.setCreatedBy(trimToNull(operator));
            row.setUpdatedBy(trimToNull(operator));
            accountMapper.insert(row);
            replaceAccountRoles(row.getId(), roleIds);
            AppAdminAccount saved = requireAccount(row.getId());
            Map<String, Object> afterSnapshot = accountPermissionSnapshot(saved);
            recordPermissionChange(
                    TARGET_ACCOUNT,
                    saved.getId(),
                    saved.getUsername(),
                    saved.getNickName(),
                    ACTION_ACCOUNT_CREATE,
                    operator,
                    null,
                    afterSnapshot,
                    accountChangeSummary(ACTION_ACCOUNT_CREATE, saved)
            );
            return toAccountDetail(saved);
        }

        if (Boolean.TRUE.equals(existing.getBuiltIn()) && STATUS_DISABLED.equals(nextStatus)) {
            guardLastSuperAdmin(existing, roles, nextStatus, currentAccountId);
        }
        if (Objects.equals(existing.getId(), currentAccountId) && STATUS_DISABLED.equals(nextStatus)) {
            throw new BusinessException(ErrorCode.CONFLICT, "You cannot disable the current admin account");
        }

        existing.setNickName(requireText(command.nickName(), 120, "nickName required"));
        existing.setStatus(nextStatus);
        existing.setMustResetPassword(defaultBoolean(command.mustResetPassword(), existing.getMustResetPassword()));
        existing.setRemark(limitText(command.remark(), 255));
        existing.setUpdatedBy(trimToNull(operator));
        guardLastSuperAdmin(existing, roles, nextStatus, currentAccountId);
        accountMapper.updateProfile(existing);

        if (command.password() != null && !command.password().isBlank()) {
            accountMapper.updatePassword(
                    existing.getId(),
                    passwordEncoder.encode(command.password().trim()),
                    defaultBoolean(command.mustResetPassword(), existing.getMustResetPassword()),
                    trimToNull(operator)
            );
        }
        replaceAccountRoles(existing.getId(), roleIds);
        AppAdminAccount saved = requireAccount(existing.getId());
        Map<String, Object> afterSnapshot = accountPermissionSnapshot(saved);
        recordPermissionChangeIfChanged(
                TARGET_ACCOUNT,
                saved.getId(),
                saved.getUsername(),
                saved.getNickName(),
                ACTION_ACCOUNT_UPDATE,
                operator,
                beforeSnapshot,
                afterSnapshot,
                accountChangeSummary(ACTION_ACCOUNT_UPDATE, saved)
        );
        return toAccountDetail(saved);
    }

    @Transactional
    public Map<String, Object> updateAccountStatus(long id, String status, String operator, Long currentAccountId) {
        AppAdminAccount account = requireAccount(id);
        String nextStatus = normalizeStatus(status);
        if (Objects.equals(account.getId(), currentAccountId) && STATUS_DISABLED.equals(nextStatus)) {
            throw new BusinessException(ErrorCode.CONFLICT, "You cannot disable the current admin account");
        }
        guardLastSuperAdmin(account, accountRoleMapper.listRolesByAccountId(id), nextStatus, currentAccountId);
        Map<String, Object> beforeSnapshot = accountPermissionSnapshot(account);
        accountMapper.updateStatus(id, nextStatus, trimToNull(operator));
        AppAdminAccount saved = requireAccount(id);
        Map<String, Object> afterSnapshot = accountPermissionSnapshot(saved);
        recordPermissionChangeIfChanged(
                TARGET_ACCOUNT,
                saved.getId(),
                saved.getUsername(),
                saved.getNickName(),
                ACTION_ACCOUNT_STATUS,
                operator,
                beforeSnapshot,
                afterSnapshot,
                accountChangeSummary(ACTION_ACCOUNT_STATUS, saved)
        );
        return toAccountDetail(saved);
    }

    @Transactional
    public Map<String, Object> resetPassword(long id, String rawPassword, boolean mustResetPassword, String operator) {
        AppAdminAccount account = requireAccount(id);
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "password required");
        }
        accountMapper.updatePassword(
                id,
                passwordEncoder.encode(rawPassword.trim()),
                mustResetPassword,
                trimToNull(operator)
        );
        return toAccountDetail(requireAccount(account.getId()));
    }

    @Transactional
    public void deleteAccounts(List<Long> ids, Long currentAccountId, String operator) {
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            AppAdminAccount account = requireAccount(id);
            if (Objects.equals(account.getId(), currentAccountId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "You cannot delete the current admin account");
            }
            guardLastSuperAdmin(account, accountRoleMapper.listRolesByAccountId(account.getId()), STATUS_DISABLED, currentAccountId);
            Map<String, Object> beforeSnapshot = accountPermissionSnapshot(account);
            accountMapper.deleteById(account.getId());
            recordPermissionChange(
                    TARGET_ACCOUNT,
                    account.getId(),
                    account.getUsername(),
                    account.getNickName(),
                    ACTION_ACCOUNT_DELETE,
                    operator,
                    beforeSnapshot,
                    null,
                    accountChangeSummary(ACTION_ACCOUNT_DELETE, account)
            );
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRoleOptions(boolean enabledOnly) {
        List<AppAdminRole> roles = enabledOnly ? roleMapper.listEnabled() : roleMapper.listAll();
        return roles.stream().map(role -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", role.getId());
            row.put("roleKey", role.getRoleKey());
            row.put("roleName", role.getRoleName());
            row.put("enabled", role.getEnabled());
            row.put("builtIn", role.getBuiltIn());
            return row;
        }).toList();
    }

    private void ensureBuiltInRoles() {
        int sortOrder = 0;
        for (Map.Entry<String, List<String>> entry : permissionCatalog.builtInRolePermissions().entrySet()) {
            String roleKey = entry.getKey();
            AppAdminRole existing = roleMapper.findByRoleKey(roleKey);
            if (existing == null) {
                AppAdminRole row = new AppAdminRole();
                row.setRoleKey(roleKey);
                row.setRoleName(defaultRoleName(roleKey));
                row.setPermissionsJson(writeJson(normalizePermissions(entry.getValue())));
                row.setEnabled(true);
                row.setBuiltIn(true);
                row.setSortOrder(sortOrder);
                row.setRemark("系统内置角色");
                row.setCreatedBy("system");
                row.setUpdatedBy("system");
                roleMapper.insert(row);
            } else {
                existing.setRoleName(existing.getRoleName() == null || existing.getRoleName().isBlank() ? defaultRoleName(roleKey) : existing.getRoleName());
                existing.setPermissionsJson(
                        SUPER_ADMIN_ROLE.equals(roleKey)
                                ? writeJson(List.of(AdminPermissionCatalog.ALL_PERMISSION))
                                : existing.getPermissionsJson()
                );
                existing.setEnabled(existing.getEnabled() == null ? true : existing.getEnabled());
                existing.setBuiltIn(true);
                existing.setSortOrder(existing.getSortOrder() == null ? sortOrder : existing.getSortOrder());
                existing.setUpdatedBy("system");
                roleMapper.update(existing);
            }
            sortOrder += 10;
        }
    }

    private void ensureSeedAccount(
            String username,
            String nickName,
            String encodedPassword,
            List<String> configuredRoleKeys
    ) {
        String normalized = normalizeUsername(username);
        if (normalized == null || encodedPassword == null || encodedPassword.isBlank()) {
            return;
        }
        AppAdminAccount existing = accountMapper.findByUsername(normalized);
        if (existing == null) {
            AppAdminAccount row = new AppAdminAccount();
            row.setUsername(normalized);
            row.setNickName(nickName == null || nickName.isBlank() ? normalized : nickName.trim());
            row.setEncodedPassword(encodedPassword.trim());
            row.setStatus(STATUS_ACTIVE);
            row.setBuiltIn(true);
            row.setMustResetPassword(false);
            row.setRemark("配置初始化管理员");
            row.setCreatedBy("system");
            row.setUpdatedBy("system");
            accountMapper.insert(row);
            replaceAccountRoles(row.getId(), resolveRoleIdsByKeys(configuredRoleKeys));
            return;
        }
        if (!Boolean.TRUE.equals(existing.getBuiltIn())) {
            return;
        }
        existing.setNickName(nickName == null || nickName.isBlank() ? existing.getNickName() : nickName.trim());
        existing.setStatus(STATUS_ACTIVE);
        existing.setUpdatedBy("system");
        accountMapper.updateProfile(existing);
        accountMapper.updatePassword(existing.getId(), encodedPassword.trim(), false, "system");
        List<Long> roleIds = resolveRoleIdsByKeys(configuredRoleKeys);
        if (roleIds.isEmpty() && accountRoleMapper.listRoleIdsByAccountId(existing.getId()).isEmpty()) {
            roleIds = resolveRoleIdsByKeys(List.of(SUPER_ADMIN_ROLE));
        }
        if (!roleIds.isEmpty()) {
            replaceAccountRoles(existing.getId(), roleIds);
        }
    }

    private Map<String, Object> toRoleSummary(AppAdminRole role) {
        List<String> permissions = readPermissions(role.getPermissionsJson());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", role.getId());
        row.put("roleKey", role.getRoleKey());
        row.put("roleName", role.getRoleName());
        row.put("enabled", role.getEnabled());
        row.put("builtIn", role.getBuiltIn());
        row.put("sortOrder", role.getSortOrder());
        row.put("remark", role.getRemark());
        row.put("permissionCount", permissions.size());
        row.put("templateStatus", roleTemplateStatus(role, permissions));
        row.put("createdAt", role.getCreatedAt());
        row.put("updatedAt", role.getUpdatedAt());
        return row;
    }

    private Map<String, Object> toRoleDetail(AppAdminRole role) {
        Map<String, Object> row = toRoleSummary(role);
        row.put("permissionKeys", readPermissions(role.getPermissionsJson()));
        row.put("assignedAccountCount", accountRoleMapper.countAccountsByRoleId(role.getId()));
        return row;
    }

    private Map<String, Object> roleTemplateStatus(AppAdminRole role, List<String> permissions) {
        Map<String, Object> row = new LinkedHashMap<>();
        AdminPermissionCatalog.RoleTemplate template = permissionCatalog.builtInRoleTemplates().get(role.getRoleKey());
        if (template == null) {
            row.put("status", "CUSTOM");
            row.put("matched", false);
            return row;
        }

        List<String> expectedKeys = template.fullAccess()
                ? expandPermissions(List.of(AdminPermissionCatalog.ALL_PERMISSION))
                : expandPermissions(template.permissionKeys());
        List<String> actualKeys = expandPermissions(permissions);
        Set<String> actualSet = new LinkedHashSet<>(actualKeys);
        Set<String> expectedSet = new LinkedHashSet<>(expectedKeys);
        List<String> missingKeys = expectedKeys.stream().filter(key -> !actualSet.contains(key)).toList();
        List<String> extraKeys = actualKeys.stream().filter(key -> !expectedSet.contains(key)).toList();
        boolean matched = missingKeys.isEmpty() && extraKeys.isEmpty();

        row.put("status", matched ? "MATCH" : "DRIFT");
        row.put("matched", matched);
        row.put("templateKey", template.key());
        row.put("templateLabel", template.label());
        row.put("expectedCount", expectedKeys.size());
        row.put("actualCount", actualKeys.size());
        row.put("matchedCount", expectedKeys.size() - missingKeys.size());
        row.put("missingCount", missingKeys.size());
        row.put("extraCount", extraKeys.size());
        row.put("missingKeys", missingKeys);
        row.put("extraKeys", extraKeys);
        return row;
    }

    private Map<String, Object> toAccountDetail(AppAdminAccount account) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", account.getId());
        row.put("username", account.getUsername());
        row.put("nickName", account.getNickName());
        row.put("status", account.getStatus());
        row.put("builtIn", account.getBuiltIn());
        row.put("mustResetPassword", account.getMustResetPassword());
        row.put("lastLoginAt", account.getLastLoginAt());
        row.put("lastLoginIp", account.getLastLoginIp());
        row.put("remark", account.getRemark());
        row.put("createdAt", account.getCreatedAt());
        row.put("updatedAt", account.getUpdatedAt());
        List<AppAdminRole> roles = accountRoleMapper.listRolesByAccountId(account.getId());
        List<Map<String, Object>> roleRows = roles.stream().map(role -> {
            List<String> permissions = readPermissions(role.getPermissionsJson());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", role.getId());
            item.put("roleKey", role.getRoleKey());
            item.put("roleName", role.getRoleName());
            item.put("enabled", role.getEnabled());
            item.put("builtIn", role.getBuiltIn());
            item.put("permissionKeys", permissions);
            item.put("permissionCount", permissions.size());
            item.put("contributesEffectivePermissions", Boolean.TRUE.equals(role.getEnabled()));
            return item;
        }).toList();
        List<AppAdminRole> effectiveRoles = effectiveRoles(roles);
        List<String> effectivePermissionKeys = mergePermissions(effectiveRoles);
        List<String> effectivePermissionExpandedKeys = expandPermissions(effectivePermissionKeys);
        row.put("roleRows", roleRows);
        row.put("roleIds", roleRows.stream().map(item -> Long.parseLong(String.valueOf(item.get("id")))).toList());
        row.put("effectivePermissionKeys", effectivePermissionKeys);
        row.put("effectivePermissionExpandedKeys", effectivePermissionExpandedKeys);
        row.put("effectivePermissionCount", effectivePermissionExpandedKeys.size());
        row.put("hasFullAccess", effectivePermissionKeys.contains(AdminPermissionCatalog.ALL_PERMISSION));
        row.put("loginRecords", buildLoginRecords(account));
        return row;
    }

    private Map<String, Object> rolePermissionSnapshot(AppAdminRole role) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", role.getId());
        row.put("roleKey", role.getRoleKey());
        row.put("roleName", role.getRoleName());
        row.put("permissionKeys", readPermissions(role.getPermissionsJson()));
        row.put("enabled", Boolean.TRUE.equals(role.getEnabled()));
        row.put("builtIn", Boolean.TRUE.equals(role.getBuiltIn()));
        row.put("sortOrder", role.getSortOrder());
        row.put("remark", role.getRemark());
        return row;
    }

    private Map<String, Object> accountPermissionSnapshot(AppAdminAccount account) {
        List<AppAdminRole> roles = accountRoleMapper.listRolesByAccountId(account.getId());
        List<AppAdminRole> effectiveRoles = effectiveRoles(roles);
        List<String> effectivePermissionKeys = mergePermissions(effectiveRoles);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", account.getId());
        row.put("username", account.getUsername());
        row.put("status", account.getStatus());
        row.put("builtIn", Boolean.TRUE.equals(account.getBuiltIn()));
        row.put("roleIds", roles.stream().map(AppAdminRole::getId).toList());
        row.put("roles", roles.stream().map(this::roleReferenceSnapshot).toList());
        row.put("effectivePermissionKeys", effectivePermissionKeys);
        row.put("effectivePermissionExpandedKeys", expandPermissions(effectivePermissionKeys));
        return row;
    }

    private Map<String, Object> roleReferenceSnapshot(AppAdminRole role) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", role.getId());
        row.put("roleKey", role.getRoleKey());
        row.put("roleName", role.getRoleName());
        row.put("enabled", Boolean.TRUE.equals(role.getEnabled()));
        row.put("permissionKeys", readPermissions(role.getPermissionsJson()));
        return row;
    }

    private void recordPermissionChangeIfChanged(
            String targetType,
            Long targetId,
            String targetKey,
            String targetName,
            String action,
            String operator,
            Map<String, Object> beforeSnapshot,
            Map<String, Object> afterSnapshot,
            String summary
    ) {
        if (Objects.equals(beforeSnapshot, afterSnapshot)) {
            return;
        }
        recordPermissionChange(targetType, targetId, targetKey, targetName, action, operator, beforeSnapshot, afterSnapshot, summary);
    }

    private void recordPermissionChange(
            String targetType,
            Long targetId,
            String targetKey,
            String targetName,
            String action,
            String operator,
            Object beforeSnapshot,
            Object afterSnapshot,
            String summary
    ) {
        AppAdminPermissionChangeLog row = new AppAdminPermissionChangeLog();
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setTargetKey(limitText(targetKey, 120));
        row.setTargetName(limitText(targetName, 120));
        row.setAction(action);
        row.setOperator(limitText(operator, 64));
        row.setChangeSummary(limitText(summary, 500));
        row.setBeforeJson(beforeSnapshot == null ? null : writeJson(beforeSnapshot));
        row.setAfterJson(afterSnapshot == null ? null : writeJson(afterSnapshot));
        permissionChangeLogMapper.insert(row);
    }

    private String roleChangeSummary(String action, AppAdminRole role) {
        String label = role.getRoleName() == null || role.getRoleName().isBlank() ? role.getRoleKey() : role.getRoleName();
        return switch (action) {
            case ACTION_ROLE_CREATE -> "Created role: " + label;
            case ACTION_ROLE_UPDATE -> "Updated role permissions: " + label;
            case ACTION_ROLE_STATUS -> (Boolean.TRUE.equals(role.getEnabled()) ? "Enabled role: " : "Disabled role: ") + label;
            case ACTION_ROLE_DELETE -> "Deleted role: " + label;
            default -> "Changed role: " + label;
        };
    }

    private String accountChangeSummary(String action, AppAdminAccount account) {
        String label = account.getNickName() == null || account.getNickName().isBlank() ? account.getUsername() : account.getNickName();
        return switch (action) {
            case ACTION_ACCOUNT_CREATE -> "Created admin account permissions: " + label;
            case ACTION_ACCOUNT_UPDATE -> "Updated admin account roles: " + label;
            case ACTION_ACCOUNT_STATUS -> STATUS_ACTIVE.equals(account.getStatus()) ? "Enabled admin account: " + label : "Disabled admin account: " + label;
            case ACTION_ACCOUNT_DELETE -> "Deleted admin account: " + label;
            default -> "Changed admin account permissions: " + label;
        };
    }

    private void replaceAccountRoles(long accountId, List<Long> roleIds) {
        accountRoleMapper.deleteByAccountId(accountId);
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            accountRoleMapper.insert(accountId, roleId);
        }
    }

    private void ensureRoleKeyUnique(String roleKey, Long selfId) {
        AppAdminRole existing = roleMapper.findByRoleKey(roleKey);
        if (existing != null && !Objects.equals(existing.getId(), selfId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "roleKey already exists");
        }
    }

    private void ensureUsernameUnique(String username, Long selfId) {
        AppAdminAccount existing = accountMapper.findByUsername(username);
        if (existing != null && !Objects.equals(existing.getId(), selfId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "username already exists");
        }
    }

    private void guardLastSuperAdmin(
            AppAdminAccount account,
            List<AppAdminRole> nextRoles,
            String nextStatus,
            Long currentAccountId
    ) {
        boolean targetIsActive = STATUS_ACTIVE.equalsIgnoreCase(nextStatus);
        boolean targetHasSuperAdmin = effectiveRoles(nextRoles).stream().anyMatch(role -> SUPER_ADMIN_ROLE.equals(role.getRoleKey()) || "admin".equals(role.getRoleKey()));
        if (targetIsActive && targetHasSuperAdmin) {
            return;
        }
        List<AppAdminRole> currentRoles = effectiveRoles(accountRoleMapper.listRolesByAccountId(account.getId()));
        boolean currentlySuperAdmin = currentRoles.stream().anyMatch(role -> SUPER_ADMIN_ROLE.equals(role.getRoleKey()) || "admin".equals(role.getRoleKey()));
        if (!currentlySuperAdmin || !STATUS_ACTIVE.equalsIgnoreCase(account.getStatus())) {
            return;
        }
        long activeSuperAdmins = accountRoleMapper.countActiveAccountsByRoleKey(SUPER_ADMIN_ROLE)
                + accountRoleMapper.countActiveAccountsByRoleKey("admin");
        if (activeSuperAdmins <= 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "At least one active super-admin must remain");
        }
    }

    private AppAdminRole requireRole(long id) {
        AppAdminRole role = roleMapper.findById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Role not found");
        }
        return role;
    }

    private AppAdminAccount requireAccount(long id) {
        AppAdminAccount account = accountMapper.findById(id);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Admin account not found");
        }
        return account;
    }

    private List<AppAdminRole> loadRoles(List<Long> roleIds) {
        List<AppAdminRole> roles = new ArrayList<>();
        for (Long roleId : roleIds) {
            AppAdminRole role = requireRole(roleId);
            if (!Boolean.TRUE.equals(role.getEnabled())) {
                throw new BusinessException(ErrorCode.CONFLICT, "Cannot assign disabled role: " + role.getRoleKey());
            }
            roles.add(role);
        }
        return roles;
    }

    private List<Long> resolveRoleIdsByKeys(Collection<String> roleKeys) {
        LinkedHashSet<Long> roleIds = new LinkedHashSet<>();
        for (String roleKey : roleKeys) {
            if (roleKey == null || roleKey.isBlank()) {
                continue;
            }
            AppAdminRole role = roleMapper.findByRoleKey(roleKey.trim());
            if (role != null) {
                roleIds.add(role.getId());
            }
        }
        return List.copyOf(roleIds);
    }

    private List<String> resolveConfiguredRoleKeys(String singleRole, List<String> multiRoles) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (multiRoles != null) {
            for (String item : multiRoles) {
                if (item != null && !item.isBlank()) {
                    keys.add(item.trim());
                }
            }
        }
        if (keys.isEmpty() && singleRole != null && !singleRole.isBlank()) {
            keys.add(singleRole.trim());
        }
        if (keys.isEmpty()) {
            keys.add(SUPER_ADMIN_ROLE);
        }
        return List.copyOf(keys);
    }

    private List<String> mergePermissions(List<AppAdminRole> roles) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (AppAdminRole role : roles) {
            merged.addAll(readPermissions(role.getPermissionsJson()));
        }
        return List.copyOf(merged);
    }

    private List<AppAdminRole> effectiveRoles(Collection<AppAdminRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .filter(role -> role != null && Boolean.TRUE.equals(role.getEnabled()))
                .toList();
    }

    private List<String> expandPermissions(Collection<String> permissions) {
        if (permissions != null && permissions.contains(AdminPermissionCatalog.ALL_PERMISSION)) {
            return permissionCatalog.permissionItems().stream()
                    .map(AdminPermissionCatalog.PermissionItem::key)
                    .toList();
        }
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        if (permissions != null) {
            for (String permission : permissions) {
                if (permission != null && !permission.isBlank() && !AdminPermissionCatalog.ALL_PERMISSION.equals(permission)) {
                    expanded.add(permission);
                }
            }
        }
        return List.copyOf(expanded);
    }

    private List<Map<String, Object>> buildLoginRecords(AppAdminAccount account) {
        if (account.getLastLoginAt() == null && (account.getLastLoginIp() == null || account.getLastLoginIp().isBlank())) {
            return List.of();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("loginAt", account.getLastLoginAt());
        row.put("loginIp", account.getLastLoginIp());
        row.put("type", "LAST_SUCCESS");
        row.put("label", "最近一次成功登录");
        return List.of(row);
    }

    private List<Map<String, Object>> buildPermissionGroupsMeta() {
        return permissionCatalog.permissionGroups().stream().map(group -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", group.key());
            row.put("label", group.label());
            row.put("description", group.description());
            row.put("sortOrder", group.sortOrder());
            row.put("items", group.items().stream().map(item -> {
                Map<String, Object> inner = new LinkedHashMap<>();
                inner.put("key", item.key());
                inner.put("label", item.label());
                inner.put("pageKey", item.pageKey());
                inner.put("pageLabel", item.pageLabel());
                inner.put("actionKey", item.actionKey());
                inner.put("actionLabel", item.actionLabel());
                inner.put("riskLevel", item.riskLevel().name());
                inner.put("riskLabel", item.riskLevel().label());
                inner.put("riskSortOrder", item.riskLevel().sortOrder());
                inner.put("description", item.description());
                inner.put("recommendedRoleKeys", item.recommendedRoleKeys());
                return inner;
            }).toList());
            return row;
        }).toList();
    }

    private List<String> normalizePermissions(Collection<String> input) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (input != null) {
            for (String item : input) {
                if (item == null || item.isBlank()) {
                    continue;
                }
                String permission = item.trim();
                if (!permissionCatalog.isValidPermission(permission)) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown permission: " + permission);
                }
                normalized.add(permission);
            }
        }
        return List.copyOf(normalized);
    }

    private List<Long> normalizeRoleIds(Collection<Long> roleIds) {
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                if (roleId != null) {
                    normalized.add(roleId);
                }
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> readPermissions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            Object value = objectMapper.readValue(raw, List.class);
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            List<String> items = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    items.add(String.valueOf(item));
                }
            }
            return List.copyOf(new LinkedHashSet<>(items));
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "JSON serialization failed");
        }
    }

    private String requireText(String value, int maxLen, String message) {
        String text = trimToNull(value);
        if (text == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }

    private String limitText(String value, int maxLen) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }

    private boolean defaultBoolean(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String normalizeRoleKey(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return text.toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private String normalizeUsername(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return text.toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String value) {
        String status = normalizeStatusOptional(value);
        return status == null ? STATUS_ACTIVE : status;
    }

    private String normalizeStatusOptional(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(upper) && !STATUS_DISABLED.equals(upper)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid status");
        }
        return upper;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private String defaultRoleName(String roleKey) {
        return switch (roleKey) {
            case SUPER_ADMIN_ROLE, "admin" -> "超级管理员";
            case "support" -> "客服";
            case "content" -> "内容运营";
            case "finance" -> "商业运营";
            case "ops" -> "平台运维";
            default -> roleKey;
        };
    }

    public record AdminAccountBundle(
            AppAdminAccount account,
            List<AppAdminRole> roles,
            List<String> permissions
    ) {
    }

    public record RoleSaveCommand(
            Long id,
            String roleKey,
            String roleName,
            List<String> permissionKeys,
            Boolean enabled,
            Integer sortOrder,
            String remark
    ) {
    }

    public record AccountSaveCommand(
            Long id,
            String username,
            String nickName,
            String password,
            List<Long> roleIds,
            String status,
            Boolean mustResetPassword,
            String remark
    ) {
    }
}
