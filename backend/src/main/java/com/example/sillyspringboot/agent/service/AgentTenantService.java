package com.example.sillyspringboot.agent.service;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Controlled tenant membership lifecycle for the Agent administration center. */
@Service
public class AgentTenantService {
    private static final Set<String> ROLES = Set.of("OWNER", "ADMIN", "MEMBER", "VIEWER");
    private final AgentAdminMapper mapper;

    public AgentTenantService(AgentAdminMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> createTenant(Object rawKey, Object rawName, Long operatorId) {
        String key = tenantKey(rawKey == null ? null : String.valueOf(rawKey));
        String name = rawName == null ? "" : String.valueOf(rawName).trim();
        if (name.isBlank() || name.length() > 255) throw new IllegalArgumentException("租户名称不能为空或过长");
        if (mapper.findTenant(key) != null) throw new IllegalArgumentException("tenantKey 已存在");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenantKey", key);
        row.put("displayName", name);
        row.put("status", "ACTIVE");
        row.put("createdBy", operatorId);
        row.put("updatedBy", operatorId);
        if (mapper.insertTenant(row) != 1) throw new IllegalStateException("Agent 租户创建失败");
        Map<String, Object> result = mapper.findTenant(key);
        audit("TENANT", result, key, "CREATE", operatorId, "Created Agent tenant");
        return result;
    }

    @Transactional
    public Map<String, Object> changeTenantStatus(String rawKey, String rawStatus, Long operatorId) {
        String key = tenantKey(rawKey);
        String status = rawStatus == null ? "ACTIVE" : rawStatus.trim().toUpperCase();
        if (!Set.of("ACTIVE", "SUSPENDED", "DISABLED").contains(status)) throw new IllegalArgumentException("租户状态无效");
        if (mapper.findTenant(key) == null) throw new IllegalArgumentException("Agent 租户不存在");
        if ("platform".equals(key) && !"ACTIVE".equals(status)) throw new IllegalArgumentException("platform 租户不能停用");
        if (mapper.updateTenantStatus(key, status, operatorId) != 1) throw new IllegalStateException("租户状态更新失败");
        Map<String, Object> result = mapper.findTenant(key);
        audit("TENANT", result, key, "STATUS_" + status, operatorId, "Changed Agent tenant status");
        return result;
    }

    @Transactional
    public Map<String, Object> updateBudget(String rawKey, Object rawTokens, Object rawCost, Object rawTasks, Long operatorId) {
        String key = tenantKey(rawKey);
        if (mapper.findTenant(key) == null) throw new IllegalArgumentException("Agent 租户不存在");
        long tokens = nonNegativeLong(rawTokens, "maxDailyTokens");
        long tasks = nonNegativeLong(rawTasks, "maxDailyTasks");
        java.math.BigDecimal cost = nonNegativeCost(rawCost);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenantKey", key); row.put("maxDailyTokens", tokens); row.put("maxDailyTasks", tasks);
        row.put("maxDailyCost", cost); row.put("enabled", 1); row.put("updatedBy", operatorId);
        if (mapper.upsertTenantBudget(row) != 1) throw new IllegalStateException("租户预算保存失败");
        Map<String, Object> result = mapper.findTenantBudget(key);
        audit("TENANT_BUDGET", result, key, "UPDATE", operatorId, "Updated Agent tenant budget");
        return result;
    }

    public Map<String, Object> budget(String rawKey) {
        String key = tenantKey(rawKey);
        if (mapper.findTenant(key) == null) throw new IllegalArgumentException("Agent 租户不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("budget", mapper.findTenantBudget(key));
        result.put("usage", mapper.tenantDailyBudgetUsage(key));
        return result;
    }

    @Transactional
    public Map<String, Object> addMember(String tenantKey, Object rawUserId, Object rawRole, Long operatorId) {
        String key = tenantKey(tenantKey);
        long userId = userId(rawUserId);
        String role = role(rawRole);
        if (mapper.findTenant(key) == null) throw new IllegalArgumentException("Agent 租户不存在");
        Map<String, Object> existing = mapper.findTenantMember(key, userId);
        if (existing != null && "ACTIVE".equals(String.valueOf(existing.get("status")))) {
            throw new IllegalArgumentException("用户已经是该租户的有效成员");
        }
        if (existing != null) {
            mapper.updateTenantMemberStatus(key, userId, "ACTIVE", role, operatorId);
            Map<String, Object> result = new LinkedHashMap<>(existing);
            result.put("memberRole", role);
            result.put("status", "ACTIVE");
            audit("TENANT_MEMBER", result, key + ":" + userId, "RESTORE", operatorId, "Restored Agent tenant member");
            return result;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenantKey", key);
        row.put("userId", userId);
        row.put("memberRole", role);
        row.put("status", "ACTIVE");
        row.put("createdBy", operatorId);
        if (mapper.insertTenantMember(row) != 1) throw new IllegalStateException("租户成员创建失败");
        Map<String, Object> result = mapper.findTenantMember(key, userId);
        audit("TENANT_MEMBER", result, key + ":" + userId, "CREATE", operatorId, "Created Agent tenant member");
        return result;
    }

    @Transactional
    public Map<String, Object> changeMemberStatus(String tenantKey, Object rawUserId, String rawStatus, Long operatorId) {
        String key = tenantKey(tenantKey);
        long userId = userId(rawUserId);
        String status = status(rawStatus);
        if (mapper.findTenantMember(key, userId) == null) throw new IllegalArgumentException("租户成员不存在");
        if (mapper.updateTenantMemberStatus(key, userId, status, null, operatorId) != 1) {
            throw new IllegalStateException("租户成员状态更新失败");
        }
        Map<String, Object> result = mapper.findTenantMember(key, userId);
        audit("TENANT_MEMBER", result, key + ":" + userId, "STATUS_" + status, operatorId, "Changed Agent tenant member status");
        return result;
    }

    @Transactional
    public Map<String, Object> upsertAdminScope(Object rawAdminId, String rawTenantKey,
                                                Object rawRole, Long operatorId) {
        long adminId = userId(rawAdminId);
        String key = tenantKey(rawTenantKey);
        if (mapper.findTenant(key) == null) throw new IllegalArgumentException("Agent 租户不存在");
        String role = rawRole == null ? "VIEWER" : String.valueOf(rawRole).trim().toUpperCase();
        if (!Set.of("OWNER", "ADMIN", "VIEWER").contains(role)) throw new IllegalArgumentException("管理员租户角色无效");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("adminId", adminId); row.put("tenantKey", key); row.put("accessRole", role);
        row.put("status", "ACTIVE"); row.put("createdBy", operatorId); row.put("updatedBy", operatorId);
        if (mapper.upsertAdminTenantScope(row) != 1) throw new IllegalStateException("管理员租户范围保存失败");
        Map<String, Object> result = mapper.findAdminTenantScope(adminId, key);
        audit("ADMIN_TENANT_SCOPE", result, adminId + ":" + key, "UPSERT", operatorId, "Updated administrator tenant scope");
        return result;
    }

    @Transactional
    public Map<String, Object> changeAdminScopeStatus(Object rawAdminId, String rawTenantKey,
                                                       String rawStatus, Long operatorId) {
        long adminId = userId(rawAdminId);
        String key = tenantKey(rawTenantKey);
        String status = rawStatus == null ? "ACTIVE" : rawStatus.trim().toUpperCase();
        if (!Set.of("ACTIVE", "SUSPENDED", "REMOVED").contains(status)) throw new IllegalArgumentException("管理员租户范围状态无效");
        if (mapper.findAdminTenantScope(adminId, key) == null) throw new IllegalArgumentException("管理员租户范围不存在");
        if (mapper.updateAdminTenantScopeStatus(adminId, key, status, operatorId) != 1) throw new IllegalStateException("管理员租户范围状态更新失败");
        Map<String, Object> result = mapper.findAdminTenantScope(adminId, key);
        audit("ADMIN_TENANT_SCOPE", result, adminId + ":" + key, "STATUS_" + status, operatorId, "Changed administrator tenant scope status");
        return result;
    }

    public java.util.List<Map<String, Object>> adminScopes(Object rawAdminId) {
        return mapper.listAdminTenantScopes(userId(rawAdminId), "ACTIVE");
    }

    public boolean hasAdminTenantAccess(long adminId, String rawTenantKey, String requiredRole) {
        String key = tenantKey(rawTenantKey);
        Map<String, Object> scope = mapper.findAdminTenantScope(adminId, key);
        if (scope == null || !"ACTIVE".equalsIgnoreCase(String.valueOf(scope.get("status")))) return false;
        String role = String.valueOf(scope.getOrDefault("accessRole", "VIEWER")).toUpperCase();
        return switch (requiredRole == null ? "VIEWER" : requiredRole.toUpperCase()) {
            case "OWNER" -> "OWNER".equals(role);
            case "ADMIN" -> "OWNER".equals(role) || "ADMIN".equals(role);
            default -> true;
        };
    }

    private void audit(String type, Map<String, Object> row, String key, String action, Long operatorId, String summary) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("configType", type);
        audit.put("configId", row == null ? null : row.get("id"));
        audit.put("configKey", key);
        audit.put("version", null);
        audit.put("action", action);
        audit.put("operatorId", operatorId);
        audit.put("summary", summary);
        if (mapper.insertConfigAudit(audit) != 1) throw new IllegalStateException("Agent 租户审计写入失败");
    }

    private static String tenantKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.length() > 120 || !key.matches("[a-z0-9][a-z0-9:_-]{0,119}")) throw new IllegalArgumentException("tenantKey 格式无效");
        return key;
    }
    private static long userId(Object value) {
        try { long id = value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value)); if (id > 0) return id; }
        catch (Exception ignored) { }
        throw new IllegalArgumentException("userId 无效");
    }
    private static String role(Object value) {
        String role = value == null ? "MEMBER" : String.valueOf(value).trim().toUpperCase();
        if (!ROLES.contains(role)) throw new IllegalArgumentException("成员角色无效");
        return role;
    }
    private static String status(String value) {
        String status = value == null ? "ACTIVE" : value.trim().toUpperCase();
        if (!Set.of("ACTIVE", "SUSPENDED", "REMOVED").contains(status)) throw new IllegalArgumentException("成员状态无效");
        return status;
    }
    private static long nonNegativeLong(Object value, String label) { try { long n = value == null ? 0 : Long.parseLong(String.valueOf(value)); if(n < 0) throw new IllegalArgumentException(label + " 不能为负数"); return n; } catch (NumberFormatException e) { throw new IllegalArgumentException(label + " 格式无效"); } }
    private static java.math.BigDecimal nonNegativeCost(Object value) { try { java.math.BigDecimal n = value == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(String.valueOf(value)); if(n.signum() < 0) throw new IllegalArgumentException("maxDailyCost 不能为负数"); return n; } catch (NumberFormatException e) { throw new IllegalArgumentException("maxDailyCost 格式无效"); } }
}
