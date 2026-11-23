package com.mindharbor.modules.risk.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 建表脚本的结构断言。
 *
 * <p>项目没有 Flyway/Liquibase，表结构靠两份文件手工维护：增量迁移与全量 schema。
 * 只要有人只改一边，新环境就会出现"本地能跑、部署报 Unknown column"，
 * 所以这里直接比对两份文件的表名集合。
 */
@DisplayName("风险事件建表脚本")
class RiskSchemaTest {

    private static final Pattern CREATE_TABLE =
            Pattern.compile("CREATE TABLE IF NOT EXISTS\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    private static String migration;

    private static String schema;

    @BeforeAll
    static void loadScripts() throws Exception {
        migration = read("db/migrations/V20261123_01__add_risk_alert_tables.sql");
        schema = read("db/schema.sql");
    }

    @Test
    @DisplayName("迁移脚本声明了四张表")
    void migrationDeclaresFourTables() {
        Set<String> tables = tableNames(migration);

        assertTrue(tables.contains("risk_alerts"), "实际表=" + tables);
        assertTrue(tables.contains("risk_alert_consumptions"), "实际表=" + tables);
        assertTrue(tables.contains("care_followups"), "实际表=" + tables);
        assertTrue(tables.contains("risk_alert_daily_stats"), "实际表=" + tables);
    }

    @Test
    @DisplayName("alert_id 唯一键是发布幂等的物理保证")
    void alertIdIsUnique() {
        assertTrue(normalize(migration).contains("UNIQUE KEY uk_risk_alerts_alert_id (alert_id)"),
                "缺唯一键时，同一消息重投会写出两行预警");
    }

    @Test
    @DisplayName("消费幂等表用 (alert_id, consumer) 联合主键")
    void consumptionUsesCompositePrimaryKey() {
        assertTrue(normalize(migration).contains("PRIMARY KEY (alert_id, consumer)"),
                "联合主键是消费端幂等的物理保证；只有单列主键时不同消费者会互相覆盖");
    }

    @Test
    @DisplayName("统计表用 (stat_date, source) 联合主键，UPSERT 才有唯一键可撞")
    void dailyStatUsesCompositePrimaryKey() {
        assertTrue(normalize(migration).contains("PRIMARY KEY (stat_date, source)"));
    }

    @Test
    @DisplayName("回访任务按 alert_id 唯一，避免同一事件产生两条回访")
    void followupIsUniquePerAlert() {
        assertTrue(normalize(migration).contains("UNIQUE KEY uk_care_followups_alert (alert_id)"));
    }

    @Test
    @DisplayName("schema.sql 必须包含迁移脚本里的全部新表，否则新环境建库缺表")
    void schemaContainsEveryMigratedTable() {
        Set<String> inMigration = tableNames(migration);
        Set<String> inSchema = tableNames(schema);

        Set<String> missing = new LinkedHashSet<>(inMigration);
        missing.removeAll(inSchema);

        assertTrue(missing.isEmpty(),
                "schema.sql 缺表：" + missing + "（只改迁移文件、忘了同步全量脚本会导致新环境缺表）");
    }

    private static Set<String> tableNames(String script) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = CREATE_TABLE.matcher(script);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static String normalize(String script) {
        return script.replaceAll("\\s+", " ");
    }

    private static String read(String resource) throws Exception {
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
