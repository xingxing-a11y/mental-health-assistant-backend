package com.mindharbor.modules.risk.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.modules.risk.entity.HandleResult;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 风险事件 XML 语句的结构断言。
 *
 * <p>假 mapper 只能钉住 Java 侧逻辑，钉不住 XML 里写的 SQL。这里让 MyBatis 真解析一遍 XML，
 * 再断言生成的语句——XML 语法错误、条件写反、占位符与参数不匹配都能在这一层发现。
 */
@DisplayName("风险事件 XML SQL")
class RiskAlertMapperXmlTest {

    private static final String NAMESPACE = "com.mindharbor.modules.risk.mapper.RiskAlertMapper";

    private static Configuration configuration;

    @BeforeAll
    static void parseMapperXml() throws Exception {
        configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream("mapper/risk/RiskAlertMapper.xml")) {
            new XMLMapperBuilder(
                    input, configuration, "mapper/risk/RiskAlertMapper.xml",
                    configuration.getSqlFragments())
                    .parse();
        }
    }

    @Test
    @DisplayName("补偿重投的候选集：只看未成功、超过最小间隔、未超重投上限")
    void findPublishableConditions() {
        Map<String, Object> params = new HashMap<>();
        params.put("before", Instant.parse("2026-11-23T00:00:00Z"));
        params.put("maxAttempts", 5);
        params.put("limit", 100);

        String sql = sqlOf("findPublishable", params);

        assertTrue(sql.contains("publish_status IN ('PENDING', 'FAILED')"),
                "必须包含 PENDING 与 FAILED 两种状态，否则失败的永远不再重投；实际=" + sql);
        assertTrue(sql.contains("publish_attempts <"), "必须限制重投次数，否则坏消息无限重投");
        assertTrue(sql.contains("created_at <"), "必须带上最小间隔条件，避开 confirm 尚未返回的窗口");
        assertEquals(3, countPlaceholders(sql), "占位符数量应为 before/maxAttempts/limit 三个；实际=" + sql);
    }

    @Test
    @DisplayName("冷却期统计：按用户 + 来源 + 时间下界过滤")
    void countRecentBySourceConditions() {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 1L);
        params.put("source", "DIARY_STREAK");
        params.put("since", Instant.parse("2026-11-16T00:00:00Z"));

        String sql = sqlOf("countRecentBySource", params);

        assertTrue(sql.contains("user_id ="));
        assertTrue(sql.contains("source ="));
        assertTrue(sql.contains("created_at >="));
        assertEquals(3, countPlaceholders(sql));
    }

    @Test
    @DisplayName("人工处理写入结论与处理人，archived_at 用 COALESCE 保留首次时间")
    void markHandledKeepsFirstHandledTime() {
        Map<String, Object> params = new HashMap<>();
        params.put("id", 1L);
        params.put("handleResult", HandleResult.CONTACTED);
        params.put("handleNote", "已电话联系");
        params.put("handledBy", 42L);
        params.put("handledAt", Instant.parse("2026-11-24T00:00:00Z"));

        String sql = sqlOf("markHandled", params);

        assertTrue(sql.contains("COALESCE(archived_at"),
                "重复处理时不能冲掉首次处理时间；实际=" + sql);
        assertTrue(sql.contains("handle_result ="), "要写入处理方式；实际=" + sql);
        assertTrue(sql.contains("handle_note ="), "要写入处理备注；实际=" + sql);
        assertTrue(sql.contains("handled_by ="), "要写入处理人；实际=" + sql);
        assertEquals(5, countPlaceholders(sql));
    }

    @Test
    @DisplayName("管理端分页按发生时间倒序，offset 参与分页")
    void findPageOrdersByOccurredAt() {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", 20);
        params.put("offset", 40L);

        String sql = sqlOf("findPage", params);

        assertTrue(sql.contains("ORDER BY occurred_at DESC"), "应按事件发生时间排序，而不是入库时间");
        assertTrue(sql.contains("LIMIT ? OFFSET ?"), "分页参数必须都在 SQL 里；实际=" + sql);
    }

    private static String sqlOf(String statement, Map<String, Object> params) {
        MappedStatement mapped = configuration.getMappedStatement(NAMESPACE + "." + statement);
        assertNotNull(mapped, "找不到语句 " + statement);
        BoundSql boundSql = mapped.getBoundSql(params);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    private static int countPlaceholders(String sql) {
        return (int) sql.chars().filter(ch -> ch == '?').count();
    }
}
