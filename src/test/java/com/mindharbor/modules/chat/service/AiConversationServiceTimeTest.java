package com.mindharbor.modules.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 注入给模型的「当前时间」。
 *
 * <p>只测纯函数与模板常量，不碰 Spring 上下文，也不给 {@code ChatClient} 打桩 —— 后者要模拟整条
 * 流式链路，成本远超收益。真正要验证「模型会不会照着时间回答」的部分，靠对模型的真实调用。
 */
@DisplayName("对话时间注入")
class AiConversationServiceTimeTest {

    @Test
    @DisplayName("格式到分钟，星期用中文全称")
    void formatsTimeWithChineseWeekday() {
        assertEquals("2026-09-22 23:11 星期二",
                AiConversationService.formatAppTime(LocalDateTime.of(2026, 9, 22, 23, 11)));
    }

    @Test
    @DisplayName("个位数月日补零，零点不丢位数")
    void padsSingleDigitFields() {
        assertEquals("2026-01-01 00:00 星期四",
                AiConversationService.formatAppTime(LocalDateTime.of(2026, 1, 1, 0, 0)));
    }

    @Test
    @DisplayName("模板三个占位符齐全")
    void templateCarriesEveryPlaceholder() {
        String template = AiConversationService.USER_TEMPLATE;

        assertTrue(template.contains("{time}"), "缺 {time}：模型拿不到当前时间，功能会静默失效");
        assertTrue(template.contains("{context}"), "缺 {context}：业务层检索的参考资料注入不进去");
        assertTrue(template.contains("{question}"), "缺 {question}：用户问题没有位置");
    }

    @Test
    @DisplayName("时间排在参考资料与用户问题之前")
    void templatePutsTimeFirst() {
        String template = AiConversationService.USER_TEMPLATE;
        int time = template.indexOf("{time}");
        int context = template.indexOf("{context}");
        int question = template.indexOf("{question}");

        // 必须先确认三个都在：indexOf 对缺失项返回 -1，会把这个顺序断言变成永真的空断言
        assertTrue(time >= 0 && context >= 0 && question >= 0, "占位符缺失时不能靠 -1 蒙混过关");
        assertTrue(time < context && context < question,
                "顺序必须是 时间 → 参考资料 → 用户问题，时间掉到后面模型更容易忽略");
    }
}
