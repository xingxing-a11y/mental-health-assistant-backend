package com.mindharbor.modules.risk.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.modules.risk.event.RiskAlertEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

/**
 * MQ 拓扑参数断言。
 *
 * <p>核心是一条不变量：<strong>每个业务队列都必须配死信</strong>。
 * 漏配时 {@code default-requeue-rejected=false} 会让失败消息直接丢弃，回访会静默消失。
 */
class RiskMqConfigTest {

    private static final long DAY_MILLIS = 86_400_000L;

    private final RiskMqConfig config = new RiskMqConfig(DAY_MILLIS);

    @Test
    @DisplayName("业务交换机是持久化的 topic")
    void alertExchangeIsDurableTopic() {
        TopicExchange exchange = config.riskExchange();
        assertNotNull(exchange);
        assertEquals(RiskMqConfig.EXCHANGE, exchange.getName());
        assertEquals("topic", exchange.getType());
        assertTrue(exchange.isDurable());
    }

    @Test
    @DisplayName("死信交换机必须与业务交换机分离，否则死信会转回原队列形成环")
    void deadLetterExchangeIsSeparate() {
        TopicExchange dlx = config.riskDlx();
        assertNotNull(dlx);
        assertEquals(RiskMqConfig.DLX, dlx.getName());
        assertTrue(dlx.isDurable());
        assertTrue(!RiskMqConfig.EXCHANGE.equals(dlx.getName()));
    }

    @Test
    @DisplayName("不变量：每一个业务队列都配了死信交换机与死信路由键")
    void everyBusinessQueueHasDeadLetter() {
        assertTrue(RiskMqConfig.BUSINESS_QUEUES.size() >= 3, "业务队列清单不应少于 3 个");

        for (String queueName : RiskMqConfig.BUSINESS_QUEUES.keySet()) {
            Queue queue = config.businessQueue(queueName);
            assertNotNull(queue, "队列 " + queueName + " 未声明");
            assertEquals(queueName, queue.getName());
            assertTrue(queue.isDurable(), queueName + " 必须持久化");

            Map<String, Object> args = queue.getArguments();
            assertEquals(RiskMqConfig.DLX, args.get("x-dead-letter-exchange"),
                    queueName + " 未配死信交换机，失败消息会被直接丢弃");
            Object deadRk = args.get("x-dead-letter-routing-key");
            assertNotNull(deadRk, queueName + " 未配死信路由键");
            assertTrue(String.valueOf(deadRk).startsWith(RiskMqConfig.RK_DEAD_PREFIX),
                    queueName + " 的死信路由键应落在 " + RiskMqConfig.RK_DEAD_PREFIX + " 下，实际=" + deadRk);
        }
    }

    @Test
    @DisplayName("未登记的队列名不能静默生成一条「无死信」的队列")
    void unregisteredQueueNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> config.businessQueue("q.risk.unknown"));
    }

    @Test
    @DisplayName("延迟队列：TTL 等于配置值，到期转入 followup.due")
    void followupDelayQueueUsesConfiguredTtl() {
        Queue queue = config.riskFollowupDelayQueue();
        assertNotNull(queue);
        assertEquals(RiskMqConfig.FOLLOWUP_DELAY_QUEUE, queue.getName());
        assertTrue(queue.isDurable());

        Map<String, Object> args = queue.getArguments();
        assertEquals(DAY_MILLIS, ((Number) args.get("x-message-ttl")).longValue());
        assertEquals(RiskMqConfig.DLX, args.get("x-dead-letter-exchange"));
        assertEquals(RiskMqConfig.RK_FOLLOWUP_DUE, args.get("x-dead-letter-routing-key"));
    }

    @Test
    @DisplayName("TTL 用 long 传递：超过 int 上限不能被截断成负数（否则消息立刻过期）")
    void ttlBeyondIntRangeIsNotTruncated() {
        long thirtyDays = 2_592_000_000L;   // > Integer.MAX_VALUE
        RiskMqConfig longTtl = new RiskMqConfig(thirtyDays);

        Map<String, Object> args = longTtl.riskFollowupDelayQueue().getArguments();
        assertEquals(thirtyDays, ((Number) args.get("x-message-ttl")).longValue());
    }

    @Test
    @DisplayName("TTL 配成 0 或负数属于配置错误，构造时就要失败")
    void nonPositiveTtlIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RiskMqConfig(0L));
        assertThrows(IllegalArgumentException.class, () -> new RiskMqConfig(-1L));
    }

    @Test
    @DisplayName("死信队列自身不再配死信，否则失败消息会在两个交换机之间打转")
    void dlqHasNoDeadLetter() {
        Queue dlq = config.riskDlq();
        assertNotNull(dlq);
        assertEquals(RiskMqConfig.DLQ, dlq.getName());
        assertNull(dlq.getArguments().get("x-dead-letter-exchange"), "死信队列不能再配死信交换机");
    }

    @Test
    @DisplayName("站内信与统计共用 risk.alert.* 绑定，一条事件扇出给两个下游")
    void notifyAndStatsShareWildcardBinding() {
        for (Binding binding : new Binding[] {
                config.riskNotifyBinding(), config.riskStatsBinding()}) {
            assertNotNull(binding);
            assertEquals(RiskMqConfig.EXCHANGE, binding.getExchange());
            assertEquals(RiskMqConfig.RK_ALERT_ALL, binding.getRoutingKey());
        }
    }

    @Test
    @DisplayName("延迟队列只绑测评与日记：对话高危不建回访，进队列只会白跑一趟")
    void followupDelayQueueOnlyBindsSourcesWithFollowup() {
        Binding assessment = config.riskFollowupDelayAssessmentBinding();
        Binding diary = config.riskFollowupDelayDiaryBinding();

        for (Binding binding : new Binding[] {assessment, diary}) {
            assertEquals(RiskMqConfig.EXCHANGE, binding.getExchange());
            assertEquals(RiskMqConfig.FOLLOWUP_DELAY_QUEUE, binding.getDestination());
        }
        assertEquals(RiskMqConfig.RK_ASSESSMENT, assessment.getRoutingKey());
        assertEquals(RiskMqConfig.RK_DIARY, diary.getRoutingKey());

        // 通配符会把对话事件也带进来，正是这次要修的问题
        for (String key : RiskMqConfig.FOLLOWUP_DELAY_ROUTING_KEYS) {
            assertTrue(!key.contains("*") && !key.contains("#"), "延迟队列绑定了通配键：" + key);
            assertTrue(!RiskMqConfig.RK_CHAT.equals(key), "对话高危不该进延迟队列");
        }
        assertEquals(java.util.List.of(RiskMqConfig.RK_ASSESSMENT, RiskMqConfig.RK_DIARY),
                RiskMqConfig.FOLLOWUP_DELAY_ROUTING_KEYS);
    }

    @Test
    @DisplayName("到期队列与死信队列各自绑定到死信交换机")
    void dlxBindings() {
        Binding due = config.riskFollowupDueBinding();
        assertNotNull(due);
        assertEquals(RiskMqConfig.DLX, due.getExchange());
        assertEquals(RiskMqConfig.RK_FOLLOWUP_DUE, due.getRoutingKey());
        assertEquals(RiskMqConfig.FOLLOWUP_DUE_QUEUE, due.getDestination());

        Binding dead = config.riskDlqBinding();
        assertNotNull(dead);
        assertEquals(RiskMqConfig.DLX, dead.getExchange());
        assertEquals(RiskMqConfig.RK_DEAD_ALL, dead.getRoutingKey());
        assertEquals(RiskMqConfig.DLQ, dead.getDestination());
    }

    @Test
    @DisplayName("消息转换器必须是 JSON，不能用默认的 Java 序列化（跨版本/跨语言会炸）")
    void messageConverterIsJacksonJson() {
        MessageConverter converter = config.riskMessageConverter();
        assertTrue(converter instanceof Jackson2JsonMessageConverter,
                "实际转换器=" + (converter == null ? "null" : converter.getClass().getName()));
    }

    @Test
    @DisplayName("事件完整往返 JSON：Instant 不能丢、枚举不能退化成字符串")
    void eventSurvivesJsonRoundTrip() {
        MessageConverter converter = config.riskMessageConverter();

        RiskAlertEvent event = new RiskAlertEvent();
        event.setAlertId("chat:9");
        event.setUserId(7L);
        event.setSource(RiskAlertEvent.Source.CHAT_HIGH);
        event.setLevel(RiskAlertEvent.Level.HIGH);
        event.setSummary("对话命中高危词");
        event.setRefs(Map.of("messageId", "9"));
        event.setOccurredAt(Instant.parse("2026-11-23T02:00:00Z"));

        Message message = converter.toMessage(event, new MessageProperties());
        Object parsed = converter.fromMessage(message);

        assertTrue(parsed instanceof RiskAlertEvent,
                "反序列化类型=" + (parsed == null ? "null" : parsed.getClass().getName()));
        RiskAlertEvent restored = (RiskAlertEvent) parsed;
        assertEquals("chat:9", restored.getAlertId());
        assertEquals(Long.valueOf(7L), restored.getUserId());
        assertEquals(RiskAlertEvent.Source.CHAT_HIGH, restored.getSource());
        assertEquals(RiskAlertEvent.Level.HIGH, restored.getLevel());
        assertEquals(Instant.parse("2026-11-23T02:00:00Z"), restored.getOccurredAt(),
                "Instant 丢失通常是没注册 JavaTimeModule");
        assertEquals(Map.of("messageId", "9"), restored.getRefs());
    }
}
