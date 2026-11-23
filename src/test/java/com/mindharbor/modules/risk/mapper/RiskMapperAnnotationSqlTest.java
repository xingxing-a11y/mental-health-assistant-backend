package com.mindharbor.modules.risk.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 注解 SQL 的结构断言。
 *
 * <p>重点钉住三种"改成看起来更清楚、实际会出错"的写法：
 * <ul>
 *   <li>把 {@code INSERT IGNORE} 换成先查后插 —— 两个消费者并发时都会通过；</li>
 *   <li>把 {@code ON DUPLICATE KEY UPDATE total = total + 1} 换成先读再写 —— 并发丢计数；</li>
 *   <li>把 {@code AND status = 'PENDING'} 去掉 —— 同一任务被重复发关怀消息。</li>
 * </ul>
 */
@DisplayName("风险事件注解 SQL")
class RiskMapperAnnotationSqlTest {

    @Test
    @DisplayName("消费幂等靠 INSERT IGNORE，不是先查后插")
    void consumptionUsesInsertIgnore() throws Exception {
        String sql = insertSql(
                RiskAlertConsumptionMapper.class,
                "insertIgnore", String.class, String.class, Instant.class);

        assertTrue(sql.contains("INSERT IGNORE INTO risk_alert_consumptions"),
                "实际=" + sql);
        assertTrue(sql.contains("(alert_id, consumer, created_at)"));
    }

    @Test
    @DisplayName("统计自增必须是原子 UPSERT，先读再写会丢计数")
    void dailyStatUsesAtomicUpsert() throws Exception {
        String sql = insertSql(
                RiskAlertDailyStatMapper.class,
                "increment", LocalDate.class, String.class, Instant.class);

        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"), "实际=" + sql);
        assertTrue(sql.trim().replaceAll("\\s+", " ")
                        .contains("ON DUPLICATE KEY UPDATE total = total + 1"),
                "自增必须是 total = total + 1（原子），不能先 SELECT 再 UPDATE；实际=" + sql);
    }

    @Test
    @DisplayName("回访任务按 alertId 去重，重复发布不产生第二条")
    void followupInsertIsIdempotent() throws Exception {
        Method method = CareFollowupMapper.class.getMethod("insertIgnore",
                com.mindharbor.modules.risk.entity.CareFollowup.class);
        String sql = String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ").trim();

        assertTrue(sql.contains("INSERT IGNORE INTO care_followups"), "实际=" + sql);
        assertTrue(sql.contains("(alert_id, user_id, due_at, status, created_at)"));
    }

    @Test
    @DisplayName("标记已发送必须带 status='PENDING' 条件，否则重复发关怀消息")
    void markSentOnlyTouchesPending() throws Exception {
        Method method = CareFollowupMapper.class.getMethod("markSent", Long.class, Instant.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ").trim();

        assertTrue(sql.contains("status = 'SENT'"), "实际=" + sql);
        assertTrue(sql.contains("AND status = 'PENDING'"),
                "缺这个条件时，延迟消息与兜底扫描可能同时发出两条关怀；实际=" + sql);
    }

    @Test
    @DisplayName("兜底扫描只取到期的 PENDING 任务")
    void findDueFiltersPendingAndDueAt() throws Exception {
        Method method = CareFollowupMapper.class.getMethod("findDue", Instant.class, int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ").trim();

        assertTrue(sql.contains("status = 'PENDING'"), "实际=" + sql);
        assertTrue(sql.contains("due_at <="), "必须用 <= now 判断到期；实际=" + sql);
    }

    private static String insertSql(
            Class<?> mapper, String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = mapper.getMethod(methodName, parameterTypes);
        return String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ").trim();
    }
}
