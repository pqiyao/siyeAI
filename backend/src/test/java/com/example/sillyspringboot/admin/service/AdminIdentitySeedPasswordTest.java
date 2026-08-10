package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.config.RuoYiAdminProperties;
import com.example.sillyspringboot.admin.entity.AppAdminAccount;
import com.example.sillyspringboot.admin.entity.AppAdminRole;
import com.example.sillyspringboot.admin.mapper.AppAdminAccountMapper;
import com.example.sillyspringboot.admin.mapper.AppAdminAccountRoleMapper;
import com.example.sillyspringboot.admin.mapper.AppAdminPermissionChangeLogMapper;
import com.example.sillyspringboot.admin.mapper.AppAdminRoleMapper;
import com.example.sillyspringboot.admin.security.AdminPermissionCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminIdentitySeedPasswordTest {

    @Test
    void startupNeverOverwritesPasswordOfExistingBuiltInAccount() {
        AppAdminAccountMapper accountMapper = mock(AppAdminAccountMapper.class);
        AppAdminRoleMapper roleMapper = mock(AppAdminRoleMapper.class);
        AppAdminAccountRoleMapper accountRoleMapper = mock(AppAdminAccountRoleMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        RuoYiAdminProperties properties = new RuoYiAdminProperties();
        properties.setUsername("admin");
        properties.setEncodedPassword("A-strong-configured-password-123!");
        properties.setRole(AdminIdentityService.SUPER_ADMIN_ROLE);

        AppAdminAccount existing = new AppAdminAccount();
        existing.setId(19L);
        existing.setUsername("admin");
        existing.setNickName("Existing administrator");
        existing.setEncodedPassword("{bcrypt}database-owned-password-hash");
        existing.setStatus(AdminIdentityService.STATUS_ACTIVE);
        existing.setBuiltIn(true);
        when(accountMapper.findByUsername("admin")).thenReturn(existing);
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}new-config-hash");

        AtomicLong roleIds = new AtomicLong(1L);
        when(roleMapper.findByRoleKey(anyString())).thenAnswer(invocation -> {
            AppAdminRole role = new AppAdminRole();
            role.setId(roleIds.getAndIncrement());
            role.setRoleKey(invocation.getArgument(0));
            role.setRoleName(invocation.getArgument(0));
            role.setPermissionsJson("[\"*:*:*\"]");
            role.setEnabled(true);
            role.setBuiltIn(true);
            role.setSortOrder(10);
            return role;
        });
        when(accountRoleMapper.listRoleIdsByAccountId(existing.getId())).thenReturn(List.of(1L));

        AdminIdentityService service = new AdminIdentityService(
                accountMapper,
                roleMapper,
                accountRoleMapper,
                mock(AppAdminPermissionChangeLogMapper.class),
                new AdminPermissionCatalog(),
                new ObjectMapper(),
                passwordEncoder,
                properties
        );

        service.run(null);

        verify(accountMapper, never()).updatePassword(anyLong(), anyString(), anyBoolean(), anyString());
        verify(accountMapper).updateProfile(existing);
    }
}
