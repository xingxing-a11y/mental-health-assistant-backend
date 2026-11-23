package com.mindharbor.modules.risk.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** alertId 生成规则：它是全链路幂等的起点，键错一次就会重复告警。 */
class RiskAlertIdsTest {

    @Test
    @DisplayName("对话：同一条消息稳定生成同一个键")
    void chatIdIsStable() {
        assertEquals("chat:42", RiskAlertIds.chat(42L));
        assertEquals(RiskAlertIds.chat(42L), RiskAlertIds.chat(42L));
    }

    @Test
    @DisplayName("对话：不同消息生成不同键")
    void chatIdDiffersByMessage() {
        assertNotEquals(RiskAlertIds.chat(42L), RiskAlertIds.chat(43L));
    }

    @Test
    @DisplayName("测评：同一份结果稳定生成同一个键")
    void assessmentIdIsStable() {
        assertEquals("assessment:7", RiskAlertIds.assessment(7L));
    }

    @Test
    @DisplayName("日记：日期必须是 8 位数字，不能带连字符")
    void diaryIdUsesCompactDate() {
        assertEquals("diary:3:20261123", RiskAlertIds.diary(3L, LocalDate.of(2026, 11, 23)));
    }

    @Test
    @DisplayName("日记：跨年日期也要 8 位，个位数月日要补零")
    void diaryIdPadsMonthAndDay() {
        assertEquals("diary:1:20260105", RiskAlertIds.diary(1L, LocalDate.of(2026, 1, 5)));
    }

    @Test
    @DisplayName("日记：用户或日期任一不同，键就不同")
    void diaryIdDiffersByUserOrDay() {
        LocalDate day = LocalDate.of(2026, 11, 23);
        assertNotEquals(RiskAlertIds.diary(3L, day), RiskAlertIds.diary(4L, day));
        assertNotEquals(RiskAlertIds.diary(3L, day), RiskAlertIds.diary(3L, day.plusDays(1)));
    }

    @Test
    @DisplayName("极端 id 不应溢出成负数或科学计数法")
    void handlesLargeId() {
        String id = RiskAlertIds.chat(Long.MAX_VALUE);
        assertEquals("chat:" + Long.MAX_VALUE, id);
        assertTrue(id.startsWith("chat:92"), "长整型应按十进制完整输出，实际=" + id);
    }
}
