package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.mapper.AdminDashboardMapper;
import com.example.sillyspringboot.compat.h5.entity.AppNotice;
import com.example.sillyspringboot.compat.h5.mapper.AppNoticeMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminDashboardService {

    private static final DateTimeFormatter DAY_KEY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DAY_LABEL_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private final AdminDashboardMapper dashboardMapper;
    private final AppNoticeMapper noticeMapper;
    private final AdminJiugaiCharacterService characterService;
    private final AdminUserDisplayService userDisplayService;

    public AdminDashboardService(
            AdminDashboardMapper dashboardMapper,
            AppNoticeMapper noticeMapper,
            AdminJiugaiCharacterService characterService,
            AdminUserDisplayService userDisplayService
    ) {
        this.dashboardMapper = dashboardMapper;
        this.noticeMapper = noticeMapper;
        this.characterService = characterService;
        this.userDisplayService = userDisplayService;
    }

    public Map<String, Object> overview(String trendRange) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        long totalTasks = dashboardMapper.totalTasks();
        long successTasks = dashboardMapper.successTasks();
        Integer trendDays = resolveTrendDays(trendRange);
        metrics.put("totalCharacters", dashboardMapper.totalCharacters());
        metrics.put("systemCharacters", dashboardMapper.systemCharacters());
        metrics.put("userCharacters", dashboardMapper.userCharacters());
        metrics.put("totalUsers", dashboardMapper.totalUsers());
        metrics.put("totalConversations", dashboardMapper.totalConversations());
        metrics.put("activeConversations7d", dashboardMapper.activeConversationsRecent(7));
        metrics.put("totalMessages", dashboardMapper.totalMessages());
        metrics.put("totalTasks", totalTasks);
        metrics.put("successRate", totalTasks <= 0 ? 0d : (double) successTasks / (double) totalTasks);
        metrics.put("totalPaidOrders", dashboardMapper.totalPaidOrders());
        metrics.put("totalRevenueYuan", String.format(Locale.ROOT, "%.2f", dashboardMapper.totalRevenueCents() / 100.0d));

        List<AppNotice> latestNotices = noticeMapper.listTopForAdmin(5);
        List<Map<String, Object>> noticeRows = latestNotices.stream().map(this::noticeRow).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metrics", metrics);
        data.put("generationTrend", normalizeGenerationTrend(dashboardMapper.generationTrend(trendDays), trendDays));
        data.put("trendRange", normalizeTrendRange(trendRange));
        data.put("topActiveUsers", decorateTopUsers(dashboardMapper.topActiveUsers(8)));
        data.put("hotCharacters", dashboardMapper.hotCharacters(8));
        data.put("latestNotices", noticeRows);
        data.put("userCreatedStats", characterService.userCreatedStats(8));
        return data;
    }

    private List<Map<String, Object>> decorateTopUsers(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        for (Map<String, Object> row : rows) {
            Long userId = longVal(row.get("userId"));
            AdminUserDisplayService.UserDisplayInfo displayInfo = userDisplayService.resolve(userId);
            row.put("displayName", displayInfo.displayName());
            row.put("subLabel", displayInfo.subLabel());
            row.put("label", displayInfo.displayName());
        }
        return rows;
    }

    private static Long longVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<Map<String, Object>> normalizeGenerationTrend(List<Map<String, Object>> rows, Integer trendDays) {
        if (trendDays == null) {
            return rows == null ? List.of() : rows;
        }
        Map<String, Map<String, Object>> byDay = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String dayKey = text(firstValue(row, "dayKey", "daykey", "DAYKEY", "day_key", "DAY_KEY"));
                if (!dayKey.isBlank()) {
                    byDay.put(dayKey, row);
                }
            }
        }
        LocalDate start = LocalDate.now().minusDays(Math.max(1, trendDays) - 1L);
        List<Map<String, Object>> normalized = new java.util.ArrayList<>();
        for (int i = 0; i < Math.max(1, trendDays); i++) {
            LocalDate day = start.plusDays(i);
            String dayKey = DAY_KEY_FMT.format(day);
            Map<String, Object> row = byDay.get(dayKey);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("dayKey", dayKey);
            out.put("date", DAY_LABEL_FMT.format(day));
            out.put("generations", row == null ? 0L : longVal(firstValue(row, "generations", "GENERATIONS"), 0L));
            out.put("successCount", row == null ? 0L : longVal(firstValue(row, "successCount", "successcount", "SUCCESSCOUNT", "success_count", "SUCCESS_COUNT"), 0L));
            out.put("failureCount", row == null ? 0L : longVal(firstValue(row, "failureCount", "failurecount", "FAILURECOUNT", "failure_count", "FAILURE_COUNT"), 0L));
            normalized.add(out);
        }
        return normalized;
    }

    private static Object firstValue(Map<String, Object> row, String... keys) {
        if (row == null || row.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key != null && row.containsKey(key)) {
                return row.get(key);
            }
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String actual = entry.getKey();
            if (actual == null) {
                continue;
            }
            for (String key : keys) {
                if (key != null && actual.equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static long longVal(Object value, long fallback) {
        Long parsed = longVal(value);
        return parsed == null ? fallback : parsed;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Integer resolveTrendDays(String trendRange) {
        return switch (normalizeTrendRange(trendRange)) {
            case "365d" -> 365;
            case "180d" -> 180;
            case "30d" -> 30;
            case "all" -> null;
            default -> 14;
        };
    }

    private static String normalizeTrendRange(String trendRange) {
        String normalized = trendRange == null ? "" : trendRange.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "all", "365d", "180d", "30d", "14d" -> normalized;
            default -> "14d";
        };
    }

    private Map<String, Object> noticeRow(AppNotice notice) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", notice.getId());
        row.put("title", notice.getTitle() == null ? "" : notice.getTitle());
        row.put("level", notice.getLevel() == null ? "info" : notice.getLevel());
        row.put("createTime", notice.getCreatedAt() == null ? "" : notice.getCreatedAt().toString().replace('T', ' '));
        return row;
    }
}
