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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminIdentityServicePermissionMergeTest {

    @Test
    void disabledRolesDoNotContributeToLoginSessionPermissions() {
        FakeAccountMapper accountMapper = new FakeAccountMapper(account());
        FakeAccountRoleMapper accountRoleMapper = new FakeAccountRoleMapper(List.of(
                role(1L, "enabled-role", true, "[\"system:notice:view\"]"),
                role(2L, "disabled-role", false, "[\"system:admin-role:edit\"]")
        ));
        AdminIdentityService service = service(accountMapper, accountRoleMapper);

        AdminIdentityService.AdminAccountBundle bundle = service.loadAccountBundleByUsername("Admin");

        assertThat(bundle).isNotNull();
        assertThat(bundle.roles()).extracting(AppAdminRole::getRoleKey).containsExactly("enabled-role");
        assertThat(bundle.permissions()).containsExactly("system:notice:view");
    }

    @Test
    void accountDetailKeepsDisabledRoleBindingButExcludesItFromEffectivePermissions() {
        AppAdminAccount account = account();
        FakeAccountMapper accountMapper = new FakeAccountMapper(account);
        FakeAccountRoleMapper accountRoleMapper = new FakeAccountRoleMapper(List.of(
                role(1L, "enabled-role", true, "[\"system:notice:view\"]"),
                role(2L, "disabled-role", false, "[\"system:admin-role:edit\"]")
        ));
        AdminIdentityService service = service(accountMapper, accountRoleMapper);

        Map<String, Object> detail = service.getAccount(account.getId());

        assertThat((List<?>) detail.get("roleRows")).hasSize(2);
        List<String> effectivePermissionKeys = stringList(detail.get("effectivePermissionKeys"));
        assertThat(effectivePermissionKeys).containsExactly("system:notice:view");
        assertThat(effectivePermissionKeys).doesNotContain("system:admin-role:edit");
        List<?> roleRows = (List<?>) detail.get("roleRows");
        Map<?, ?> disabledRole = (Map<?, ?>) roleRows.get(1);
        assertThat(disabledRole.get("roleKey")).isEqualTo("disabled-role");
        assertThat(disabledRole.get("contributesEffectivePermissions")).isEqualTo(false);
    }

    private static AdminIdentityService service(
            AppAdminAccountMapper accountMapper,
            AppAdminAccountRoleMapper accountRoleMapper
    ) {
        return new AdminIdentityService(
                accountMapper,
                new FakeRoleMapper(),
                accountRoleMapper,
                new FakePermissionChangeLogMapper(),
                new AdminPermissionCatalog(),
                new ObjectMapper(),
                NoOpPasswordEncoder.getInstance(),
                new RuoYiAdminProperties()
        );
    }

    private static AppAdminAccount account() {
        AppAdminAccount account = new AppAdminAccount();
        account.setId(100L);
        account.setUsername("admin");
        account.setNickName("Admin");
        account.setEncodedPassword("{noop}password");
        account.setStatus(AdminIdentityService.STATUS_ACTIVE);
        account.setBuiltIn(false);
        account.setMustResetPassword(false);
        return account;
    }

    private static AppAdminRole role(Long id, String roleKey, boolean enabled, String permissionsJson) {
        AppAdminRole role = new AppAdminRole();
        role.setId(id);
        role.setRoleKey(roleKey);
        role.setRoleName(roleKey);
        role.setEnabled(enabled);
        role.setBuiltIn(false);
        role.setPermissionsJson(permissionsJson);
        role.setSortOrder(id.intValue());
        return role;
    }

    private static List<String> stringList(Object value) {
        return ((List<?>) value).stream().map(String::valueOf).toList();
    }

    private static final class FakeAccountMapper implements AppAdminAccountMapper {
        private final AppAdminAccount account;

        private FakeAccountMapper(AppAdminAccount account) {
            this.account = account;
        }

        @Override
        public AppAdminAccount findById(long id) {
            return account != null && account.getId() == id ? account : null;
        }

        @Override
        public AppAdminAccount findByUsername(String username) {
            return account != null && account.getUsername().equals(username) ? account : null;
        }

        @Override
        public long countList(String keyword, String status) {
            return 0;
        }

        @Override
        public List<Map<String, Object>> listPage(String keyword, String status, int offset, int limit) {
            return List.of();
        }

        @Override
        public int insert(AppAdminAccount row) {
            return 0;
        }

        @Override
        public int updateProfile(AppAdminAccount row) {
            return 0;
        }

        @Override
        public int updateStatus(long id, String status, String updatedBy) {
            return 0;
        }

        @Override
        public int updatePassword(long id, String encodedPassword, boolean mustResetPassword, String updatedBy) {
            return 0;
        }

        @Override
        public int updateLastLogin(long id, String lastLoginIp) {
            return 0;
        }

        @Override
        public int deleteById(long id) {
            return 0;
        }

        @Override
        public long countByStatus(String status) {
            return 0;
        }
    }

    private static final class FakeAccountRoleMapper implements AppAdminAccountRoleMapper {
        private final List<AppAdminRole> roles;

        private FakeAccountRoleMapper(List<AppAdminRole> roles) {
            this.roles = new ArrayList<>(roles);
        }

        @Override
        public List<Long> listRoleIdsByAccountId(long accountId) {
            return roles.stream().map(AppAdminRole::getId).toList();
        }

        @Override
        public List<AppAdminRole> listRolesByAccountId(long accountId) {
            return roles;
        }

        @Override
        public List<Map<String, Object>> listAccountRoleRows(long accountId) {
            return List.of();
        }

        @Override
        public int insert(long accountId, long roleId) {
            return 0;
        }

        @Override
        public int deleteByAccountId(long accountId) {
            return 0;
        }

        @Override
        public long countAccountsByRoleId(long roleId) {
            return 0;
        }

        @Override
        public long countActiveAccountsByRoleKey(String roleKey) {
            return 0;
        }
    }

    private static final class FakeRoleMapper implements AppAdminRoleMapper {
        @Override
        public AppAdminRole findById(long id) {
            return null;
        }

        @Override
        public AppAdminRole findByRoleKey(String roleKey) {
            return null;
        }

        @Override
        public List<AppAdminRole> listAll() {
            return List.of();
        }

        @Override
        public List<AppAdminRole> listEnabled() {
            return List.of();
        }

        @Override
        public long countList(String keyword, Boolean enabled) {
            return 0;
        }

        @Override
        public List<AppAdminRole> listPage(String keyword, Boolean enabled, int offset, int limit) {
            return List.of();
        }

        @Override
        public int insert(AppAdminRole row) {
            return 0;
        }

        @Override
        public int update(AppAdminRole row) {
            return 0;
        }

        @Override
        public int updateStatus(long id, boolean enabled, String updatedBy) {
            return 0;
        }

        @Override
        public int deleteById(long id) {
            return 0;
        }
    }

    private static final class FakePermissionChangeLogMapper implements AppAdminPermissionChangeLogMapper {
        @Override
        public int insert(AppAdminPermissionChangeLog row) {
            return 1;
        }

        @Override
        public long countList(String targetType, String action, String operator, String keyword) {
            return 0;
        }

        @Override
        public List<Map<String, Object>> listPage(String targetType, String action, String operator, String keyword, int offset, int limit) {
            return List.of();
        }
    }
}
