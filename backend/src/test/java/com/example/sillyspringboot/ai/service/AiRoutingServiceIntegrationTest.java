package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AiRoutingServiceIntegrationTest {

    @Autowired private AiRoutingService service;
    @Autowired private AiRoutingRuntimeSettingsService runtimeSettingsService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void encryptsKeysPreservesExplicitOrderAndSerializesConcurrentFailures() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String secret = "secret-" + suffix;
        Long firstAccount = null;
        Long secondAccount = null;
        Long firstDeployment = null;
        Long secondDeployment = null;
        Long routeId = null;
        try {
            Map<String, Object> first = service.saveProvider(providerBody("first_" + suffix, "First", "model-a", secret));
            Map<String, Object> second = service.saveProvider(providerBody("second_" + suffix, "Second", "model-b", "second-" + secret));
            firstAccount = id((Map<?, ?>) first.get("account"));
            secondAccount = id((Map<?, ?>) second.get("account"));
            firstDeployment = id((Map<?, ?>) first.get("deployment"));
            secondDeployment = id((Map<?, ?>) second.get("deployment"));

            String storedCipher = jdbc.queryForObject(
                    "SELECT api_key_cipher FROM app_ai_provider_account WHERE id = ?", String.class, firstAccount);
            assertThat(storedCipher).startsWith("v1:").doesNotContain(secret);
            assertThat(service.adminSnapshot().toString()).doesNotContain(secret);

            Map<String, Object> route = service.saveRoute(new LinkedHashMap<>(Map.of(
                    "routeKey", AiCapability.CHAT.defaultRouteKey(),
                    "displayName", "Chat " + suffix,
                    "capability", "CHAT",
                    "deploymentIds", List.of(secondDeployment, firstDeployment),
                    "enabled", true
            )));
            routeId = ((List<Map<String, Object>>) route.get("routes")).stream()
                    .filter(item -> AiCapability.CHAT.defaultRouteKey().equals(item.get("routeKey")))
                    .map(item -> ((Number) item.get("id")).longValue())
                    .findFirst().orElseThrow();
            assertThat(service.resolve(AiCapability.CHAT))
                    .extracting(AiRoutingService.ResolvedProvider::deploymentId)
                    .containsExactly(secondDeployment, firstDeployment);
            Map<String, Object> summary = service.capabilitySummary(AiCapability.CHAT);
            assertThat(summary)
                    .containsEntry("routeConfigured", true)
                    .containsEntry("configuredNodeCount", 2)
                    .doesNotContainKeys("apiKey", "apiKeyMask");
            assertThat(summary.toString()).doesNotContain(secret);

            int workers = 8;
            long failureDeploymentId = firstDeployment;
            ExecutorService executor = Executors.newFixedThreadPool(workers);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int index = 0; index < workers; index++) {
                    int attempt = index;
                    futures.add(executor.submit(() -> service.recordFailure(failureDeploymentId, "failure-" + attempt)));
                }
                for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
            Integer failures = jdbc.queryForObject(
                    "SELECT consecutive_failures FROM app_ai_provider_deployment WHERE id = ?",
                    Integer.class,
                    firstDeployment
            );
            assertThat(failures).isEqualTo(workers);
        } finally {
            if (routeId != null) service.deleteRoute(routeId);
            if (firstDeployment != null) service.deleteDeployment(firstDeployment);
            if (secondDeployment != null) service.deleteDeployment(secondDeployment);
            if (firstAccount != null) service.deleteAccount(firstAccount);
            if (secondAccount != null) service.deleteAccount(secondAccount);
        }
    }

    @Test
    void expiredCircuitAllowsExactlyOneConcurrentHalfOpenProbe() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Long accountId = null;
        Long deploymentId = null;
        Long routeId = null;
        try {
            Map<String, Object> provider = service.saveProvider(
                    providerBody("half_open_" + suffix, "Half open", "model-half-open", "secret-" + suffix));
            accountId = id((Map<?, ?>) provider.get("account"));
            deploymentId = id((Map<?, ?>) provider.get("deployment"));
            Map<String, Object> route = service.saveRoute(new LinkedHashMap<>(Map.of(
                    "routeKey", AiCapability.CHAT.defaultRouteKey(),
                    "displayName", "Half open " + suffix,
                    "capability", "CHAT",
                    "deploymentIds", List.of(deploymentId),
                    "enabled", true
            )));
            routeId = ((List<Map<String, Object>>) route.get("routes")).stream()
                    .filter(item -> AiCapability.CHAT.defaultRouteKey().equals(item.get("routeKey")))
                    .map(item -> ((Number) item.get("id")).longValue())
                    .findFirst().orElseThrow();
            jdbc.update(
                    "UPDATE app_ai_provider_deployment "
                            + "SET failure_threshold = 1, consecutive_failures = 1, "
                            + "circuit_open_until = DATEADD('SECOND', -1, CURRENT_TIMESTAMP) WHERE id = ?",
                    deploymentId
            );

            int workers = 12;
            CountDownLatch ready = new CountDownLatch(workers);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger admitted = new AtomicInteger();
            ExecutorService executor = Executors.newFixedThreadPool(workers);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int index = 0; index < workers; index++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                        if (!service.resolve(AiCapability.CHAT).isEmpty()) {
                            admitted.incrementAndGet();
                        }
                        return null;
                    }));
                }
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                for (Future<?> future : futures) {
                    future.get(10, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }

            assertThat(admitted.get()).isEqualTo(1);
        } finally {
            if (routeId != null) service.deleteRoute(routeId);
            if (deploymentId != null) service.deleteDeployment(deploymentId);
            if (accountId != null) service.deleteAccount(accountId);
        }
    }

    @Test
    void deploymentEditResetsHealthAndAccountWritesUseOptimisticLock() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Long accountId = null;
        Long deploymentId = null;
        try {
            Map<String, Object> created = service.saveProvider(
                    providerBody("health_" + suffix, "Health", "model-old", "secret-" + suffix));
            Map<?, ?> account = (Map<?, ?>) created.get("account");
            Map<?, ?> deployment = (Map<?, ?>) created.get("deployment");
            accountId = id(account);
            deploymentId = id(deployment);
            service.recordFailure(deploymentId, "temporary");

            Map<String, Object> edit = providerBody("health_" + suffix, "Health", "model-new", "");
            edit.put("accountId", accountId);
            edit.put("deploymentId", deploymentId);
            edit.put("accountVersion", account.get("versionNo"));
            edit.put("apiKeyAction", "preserve");
            service.saveProvider(edit);

            Map<String, Object> health = jdbc.queryForMap(
                    "SELECT consecutive_failures, last_health_status FROM app_ai_provider_deployment WHERE id = ?",
                    deploymentId);
            assertThat(((Number) health.get("consecutive_failures")).intValue()).isZero();
            assertThat(health.get("last_health_status")).isEqualTo("unknown");

            Map<String, Object> stale = new LinkedHashMap<>(edit);
            stale.put("displayName", "Stale edit");
            stale.put("accountVersion", -1L);
            assertThatThrownBy(() -> service.saveProvider(stale))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("其他管理员修改");
        } finally {
            if (deploymentId != null) service.deleteDeployment(deploymentId);
            if (accountId != null) service.deleteAccount(accountId);
        }
    }

    @Test
    void runtimeSettingsRequireConfirmationAndCanResetToEnvironmentDefaults() {
        runtimeSettingsService.resetToEnvironment();
        try {
            assertThatThrownBy(() -> runtimeSettingsService.save(Map.of("enabled", true)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("明确确认");

            AiRoutingRuntimeSettingsService.Settings saved = runtimeSettingsService.save(Map.of(
                    "confirmed", true,
                    "enabled", true,
                    "shadowEnabled", true,
                    "chatCanaryPercent", 7,
                    "visionEnabled", true,
                    "imageEnabled", false,
                    "ttsEnabled", false,
                    "sttEnabled", false
            ));
            assertThat(saved.enabled()).isTrue();
            assertThat(saved.chatCanaryPercent()).isEqualTo(7);
            assertThat(saved.visionEnabled()).isTrue();
            assertThat(saved.source()).isEqualTo("database");

            AiRoutingRuntimeSettingsService.Settings reset = runtimeSettingsService.resetToEnvironment();
            assertThat(reset.source()).isEqualTo("environment");
        } finally {
            runtimeSettingsService.resetToEnvironment();
        }
    }

    @Test
    void emptyExistingRouteDeletesItAndDeploymentErrorNamesEveryReference() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String routeKey = "test.route." + suffix;
        String routeName = "测试执行路由 " + suffix;
        Long accountId = null;
        Long deploymentId = null;
        Long routeId = null;
        try {
            Map<String, Object> provider = service.saveProvider(
                    providerBody("delete_" + suffix, "Delete", "model-delete", "secret-" + suffix));
            accountId = id((Map<?, ?>) provider.get("account"));
            deploymentId = id((Map<?, ?>) provider.get("deployment"));
            Map<String, Object> snapshot = service.saveRoute(new LinkedHashMap<>(Map.of(
                    "routeKey", routeKey,
                    "displayName", routeName,
                    "capability", "CHAT",
                    "deploymentIds", List.of(deploymentId),
                    "enabled", true
            )));
            routeId = routeId(snapshot, routeKey);

            long referencedDeploymentId = deploymentId;
            assertThatThrownBy(() -> service.deleteDeployment(referencedDeploymentId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(routeName);

            service.saveRoute(new LinkedHashMap<>(Map.of(
                    "id", routeId,
                    "routeKey", routeKey,
                    "displayName", routeName,
                    "capability", "CHAT",
                    "deploymentIds", List.of(),
                    "enabled", true
            )));
            routeId = null;
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM app_ai_route WHERE route_key = ?", Integer.class, routeKey)).isZero();
        } finally {
            if (routeId != null) service.deleteRoute(routeId);
            if (deploymentId != null) service.deleteDeployment(deploymentId);
            if (accountId != null) service.deleteAccount(accountId);
        }
    }

    @Test
    void cannotDeleteDefaultRouteWhileCapabilityIsRunning() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Long accountId = null;
        Long deploymentId = null;
        Long routeId = null;
        runtimeSettingsService.resetToEnvironment();
        try {
            Map<String, Object> provider = service.saveProvider(
                    providerBody("live_" + suffix, "Live", "model-live", "secret-" + suffix));
            accountId = id((Map<?, ?>) provider.get("account"));
            deploymentId = id((Map<?, ?>) provider.get("deployment"));
            Map<String, Object> snapshot = service.saveRoute(new LinkedHashMap<>(Map.of(
                    "routeKey", AiCapability.CHAT.defaultRouteKey(),
                    "displayName", "Live chat " + suffix,
                    "capability", "CHAT",
                    "deploymentIds", List.of(deploymentId),
                    "enabled", true
            )));
            routeId = routeId(snapshot, AiCapability.CHAT.defaultRouteKey());
            runtimeSettingsService.save(Map.of(
                    "confirmed", true,
                    "enabled", true,
                    "chatCanaryPercent", 10
            ));

            long activeRouteId = routeId;
            assertThatThrownBy(() -> service.deleteRoute(activeRouteId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("正在运行")
                    .hasMessageContaining("运行开关");
        } finally {
            runtimeSettingsService.resetToEnvironment();
            if (routeId != null) service.deleteRoute(routeId);
            if (deploymentId != null) service.deleteDeployment(deploymentId);
            if (accountId != null) service.deleteAccount(accountId);
        }
    }

    @Test
    void deletingDeploymentReclaimsLegacyOrphanDedicatedOfferingRoute() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String routeKey = "chat.offer.orphan." + suffix;
        Long accountId = null;
        Long deploymentId = null;
        Long routeId = null;
        try {
            Map<String, Object> provider = service.saveProvider(
                    providerBody("orphan_" + suffix, "Orphan", "model-orphan", "secret-" + suffix));
            accountId = id((Map<?, ?>) provider.get("account"));
            deploymentId = id((Map<?, ?>) provider.get("deployment"));
            Map<String, Object> snapshot = service.saveRoute(new LinkedHashMap<>(Map.of(
                    "routeKey", routeKey,
                    "displayName", "孤立用户模型路由 " + suffix,
                    "capability", "CHAT",
                    "deploymentIds", List.of(deploymentId),
                    "enabled", true
            )));
            routeId = routeId(snapshot, routeKey);

            service.deleteDeployment(deploymentId);

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM app_ai_provider_deployment WHERE id = ?", Integer.class, deploymentId)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM app_ai_route WHERE id = ?", Integer.class, routeId)).isZero();
            deploymentId = null;
            routeId = null;
        } finally {
            if (routeId != null) service.deleteRoute(routeId);
            if (deploymentId != null) service.deleteDeployment(deploymentId);
            if (accountId != null) service.deleteAccount(accountId);
        }
    }

    @Test
    void migrateDeleteReplacesUniqueRouteNodeBeforeDeletingDeployment() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String routeKey = "test.migrate." + suffix;
        Long sourceAccountId = null;
        Long replacementAccountId = null;
        Long sourceDeploymentId = null;
        Long replacementDeploymentId = null;
        Long routeId = null;
        try {
            Map<String, Object> source = service.saveProvider(
                    providerBody("migrate_source_" + suffix, "Source", "model-source", "source-" + suffix));
            Map<String, Object> replacement = service.saveProvider(
                    providerBody("migrate_target_" + suffix, "Target", "model-target", "target-" + suffix));
            sourceAccountId = id((Map<?, ?>) source.get("account"));
            replacementAccountId = id((Map<?, ?>) replacement.get("account"));
            sourceDeploymentId = id((Map<?, ?>) source.get("deployment"));
            replacementDeploymentId = id((Map<?, ?>) replacement.get("deployment"));
            Map<String, Object> snapshot = service.saveRoute(new LinkedHashMap<>(Map.of(
                    "routeKey", routeKey,
                    "displayName", "迁移测试路由 " + suffix,
                    "capability", "CHAT",
                    "deploymentIds", List.of(sourceDeploymentId),
                    "enabled", true
            )));
            routeId = routeId(snapshot, routeKey);

            Map<String, Object> result = service.migrateAndDeleteDeployment(
                    sourceDeploymentId, Map.of("replacementDeploymentId", replacementDeploymentId));

            assertThat(result.get("updatedRoutes")).asList().contains("迁移测试路由 " + suffix);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM app_ai_provider_deployment WHERE id = ?", Integer.class, sourceDeploymentId)).isZero();
            assertThat(service.resolveRoute(routeKey, AiCapability.CHAT))
                    .extracting(AiRoutingService.ResolvedProvider::deploymentId)
                    .containsExactly(replacementDeploymentId);
            sourceDeploymentId = null;
        } finally {
            if (routeId != null) service.deleteRoute(routeId);
            if (sourceDeploymentId != null) service.deleteDeployment(sourceDeploymentId);
            if (replacementDeploymentId != null) service.deleteDeployment(replacementDeploymentId);
            if (sourceAccountId != null) service.deleteAccount(sourceAccountId);
            if (replacementAccountId != null) service.deleteAccount(replacementAccountId);
        }
    }

    @Test
    void migrateDeleteRequiresReplacementForUniqueNonOrphanRoute() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String routeKey = "test.required." + suffix;
        Long accountId = null;
        Long deploymentId = null;
        Long routeId = null;
        try {
            Map<String, Object> provider = service.saveProvider(
                    providerBody("required_" + suffix, "Required", "model-required", "secret-" + suffix));
            accountId = id((Map<?, ?>) provider.get("account"));
            deploymentId = id((Map<?, ?>) provider.get("deployment"));
            Map<String, Object> snapshot = service.saveRoute(new LinkedHashMap<>(Map.of(
                    "routeKey", routeKey,
                    "displayName", "必须替换路由 " + suffix,
                    "capability", "CHAT",
                    "deploymentIds", List.of(deploymentId),
                    "enabled", true
            )));
            routeId = routeId(snapshot, routeKey);

            long sourceId = deploymentId;
            assertThatThrownBy(() -> service.migrateAndDeleteDeployment(sourceId, Map.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("必须替换路由")
                    .hasMessageContaining("选择同能力替换模型");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM app_ai_provider_deployment WHERE id = ?", Integer.class, deploymentId)).isOne();
        } finally {
            if (routeId != null) service.deleteRoute(routeId);
            if (deploymentId != null) service.deleteDeployment(deploymentId);
            if (accountId != null) service.deleteAccount(accountId);
        }
    }

    private static Map<String, Object> providerBody(String key, String name, String model, String secret) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("providerKey", key);
        body.put("displayName", name);
        body.put("vendor", "custom");
        body.put("baseUrl", "https://example.com/chat/completions");
        body.put("apiKey", secret);
        body.put("apiKeyAction", "replace");
        body.put("capability", "CHAT");
        body.put("modelName", model);
        body.put("failureThreshold", 20);
        body.put("cooldownSeconds", 30);
        return body;
    }

    private static Long id(Map<?, ?> view) {
        return ((Number) view.get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private static Long routeId(Map<String, Object> snapshot, String routeKey) {
        return ((List<Map<String, Object>>) snapshot.get("routes")).stream()
                .filter(item -> routeKey.equals(item.get("routeKey")))
                .map(item -> ((Number) item.get("id")).longValue())
                .findFirst()
                .orElseThrow();
    }
}
