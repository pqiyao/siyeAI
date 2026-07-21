package com.example.sillyspringboot.admin.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPermissionCatalogTest {

    private final AdminPermissionCatalog catalog = new AdminPermissionCatalog();

    @Test
    void keepsLegacyPermissionKeysValid() {
        assertThat(catalog.allPermissionKeys()).containsExactlyInAnyOrderElementsOf(List.of(
                "system:admin-user:view",
                "system:admin-user:edit",
                "system:admin-user:create",
                "system:admin-user:update",
                "system:admin-user:status",
                "system:admin-user:reset-password",
                "system:admin-user:delete",
                "system:admin-role:view",
                "system:admin-role:edit",
                "system:admin-role:create",
                "system:admin-role:update",
                "system:admin-role:status",
                "system:admin-role:delete",
                "system:permission-log:view",
                "system:notice:view",
                "system:notice:edit",
                "support:ticket:list",
                "support:ticket:view",
                "support:ticket:reply",
                "support:ticket:update",
                "content:character:view",
                "content:character:edit",
                "content:review:view",
                "content:review:edit",
                "content:tag:view",
                "content:tag:edit",
                "content:lorebook:view",
                "content:lorebook:edit",
                "commerce:entitlement:view",
                "commerce:entitlement:edit",
                "commerce:entitlement-log:view",
                "commerce:entitlement-log:delete",
                "commerce:user:view",
                "commerce:user:edit",
                "commerce:user:update",
                "commerce:user:security",
                "commerce:user:batch-policy",
                "commerce:user:delete",
                "commerce:product:view",
                "commerce:product:edit",
                "commerce:payment:view",
                "commerce:payment:edit",
                "commerce:order:view",
                "commerce:order:edit",
                "ops:openrouter:view",
                "ops:openrouter:edit",
                "ops:openrouter:delete",
                "ops:ailog:view",
                "ops:ailog:clean",
                "conversation:runtime:view",
                "conversation:runtime:edit",
                AdminPermissionCatalog.ALL_PERMISSION
        ));
    }

    @Test
    void permissionKeysAreUniqueAndStructured() {
        Set<String> keys = new HashSet<>();
        catalog.permissionGroups().forEach(group -> {
            assertThat(group.key()).isNotBlank();
            assertThat(group.label()).isNotBlank();
            assertThat(group.description()).isNotBlank();
            assertThat(group.sortOrder()).isGreaterThan(0);
            assertThat(group.items()).isNotEmpty();

            group.items().forEach(item -> {
                assertThat(keys.add(item.key())).as("duplicate permission key: %s", item.key()).isTrue();
                assertThat(item.label()).isNotBlank();
                assertThat(item.pageKey()).isNotBlank();
                assertThat(item.pageLabel()).isNotBlank();
                assertThat(item.actionKey()).isNotBlank();
                assertThat(item.actionLabel()).isNotBlank();
                assertThat(item.description()).isNotBlank();
                assertThat(item.riskLevel()).isNotNull();
                assertThat(item.riskLevel().label()).isNotBlank();
                assertThat(item.riskLevel().sortOrder()).isGreaterThan(0);
            });
        });
    }

    @Test
    void builtInRolePermissionsKeepExistingContract() {
        assertThat(catalog.builtInRolePermissions()).containsOnlyKeys(
                "super-admin",
                "admin",
                "support",
                "content",
                "finance",
                "ops"
        );

        assertThat(catalog.builtInRolePermissions().get("super-admin"))
                .containsExactly(AdminPermissionCatalog.ALL_PERMISSION);
        assertThat(catalog.builtInRolePermissions().get("admin"))
                .containsExactly(AdminPermissionCatalog.ALL_PERMISSION);
    }

    @Test
    void builtInRoleTemplatesOnlyReferenceKnownPermissions() {
        Set<String> validKeys = catalog.allPermissionKeys();

        catalog.builtInRoleTemplates().values().forEach(template -> {
            assertThat(template.key()).isNotBlank();
            assertThat(template.label()).isNotBlank();
            assertThat(template.description()).isNotBlank();
            assertThat(template.permissionKeys()).isNotEmpty();
            assertThat(template.sortOrder()).isGreaterThan(0);
            assertThat(template.permissionKeys()).allSatisfy(permission ->
                    assertThat(validKeys).as(template.key() + " references " + permission).contains(permission)
            );
        });
    }
}
