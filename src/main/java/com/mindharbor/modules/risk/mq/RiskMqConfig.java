package com.mindharbor.modules.risk.mq;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 高危信号事件链的 MQ 拓扑声明。
 *
 * <p>队列名、路由键、死信关系全部在这里定死；消费者只按常量引用，不自己拼字符串。
 * 每个业务队列的死信配置由 {@link #businessQueue(String)} 统一生成，避免漏配。
 */
@Configuration
public class RiskMqConfig {

    /** 业务交换机：一条风险事件按 routing key 扇出给多个下游。 */
    public static final String EXCHANGE = "mh.risk";

    /** 死信交换机：同时承担「延迟到期」与「消费失败」两种转发。 */
    public static final String DLX = "mh.risk.dlx";

    public static final String NOTIFY_QUEUE = "q.risk.notify";
    public static final String STATS_QUEUE = "q.risk.stats";
    public static final String FOLLOWUP_DELAY_QUEUE = "q.risk.followup.delay";
    public static final String FOLLOWUP_DUE_QUEUE = "q.risk.followup.due";
    public static final String DLQ = "q.risk.dlq";

    public static final String RK_CHAT = "risk.alert.chat";
    public static final String RK_ASSESSMENT = "risk.alert.assessment";
    public static final String RK_DIARY = "risk.alert.diary";
    public static final String RK_ALERT_ALL = "risk.alert.*";
    public static final String RK_FOLLOWUP_DUE = "risk.followup.due";
    public static final String RK_DEAD_PREFIX = "risk.dead.";
    public static final String RK_DEAD_ALL = "risk.dead.#";

    /** 业务队列 → 死信路由键。新增消费者队列必须在这里登记，否则不变量测试会红。 */
    static final Map<String, String> BUSINESS_QUEUES = Map.of(
            NOTIFY_QUEUE, RK_DEAD_PREFIX + "notify",
            STATS_QUEUE, RK_DEAD_PREFIX + "stats",
            FOLLOWUP_DUE_QUEUE, RK_DEAD_PREFIX + "followup");

    private final long followupDelayMillis;

    public RiskMqConfig(
            @Value("${risk.alert.followup.delay-millis:86400000}") long followupDelayMillis) {
        if (followupDelayMillis <= 0) {
            throw new IllegalArgumentException(
                    "risk.alert.followup.delay-millis 必须为正数，实际=" + followupDelayMillis);
        }
        this.followupDelayMillis = followupDelayMillis;
    }

    @Bean
    public TopicExchange riskExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange riskDlx() {
        return ExchangeBuilder.topicExchange(DLX).durable(true).build();
    }

    @Bean
    public Queue riskNotifyQueue() {
        return businessQueue(NOTIFY_QUEUE);
    }

    @Bean
    public Queue riskStatsQueue() {
        return businessQueue(STATS_QUEUE);
    }

    @Bean
    public Queue riskFollowupDelayQueue() {
        // x-message-ttl 用 long 直接写进参数：QueueBuilder.ttl(int) 在超过约 24.8 天时会溢出成负数，
        // 而负 TTL 会让消息立刻过期，延迟回访彻底失效。
        return QueueBuilder.durable(FOLLOWUP_DELAY_QUEUE)
                .withArgument("x-message-ttl", followupDelayMillis)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(RK_FOLLOWUP_DUE)
                .build();
    }

    @Bean
    public Queue riskFollowupDueQueue() {
        return businessQueue(FOLLOWUP_DUE_QUEUE);
    }

    @Bean
    public Queue riskDlq() {
        // 死信队列是终点：不能再配死信，否则失败消息会在两个交换机之间来回打转。
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding riskNotifyBinding() {
        return BindingBuilder.bind(riskNotifyQueue()).to(riskExchange()).with(RK_ALERT_ALL);
    }

    @Bean
    public Binding riskStatsBinding() {
        return BindingBuilder.bind(riskStatsQueue()).to(riskExchange()).with(RK_ALERT_ALL);
    }

    @Bean
    public Binding riskFollowupDelayBinding() {
        return BindingBuilder.bind(riskFollowupDelayQueue()).to(riskExchange()).with(RK_ALERT_ALL);
    }

    @Bean
    public Binding riskFollowupDueBinding() {
        return BindingBuilder.bind(riskFollowupDueQueue()).to(riskDlx()).with(RK_FOLLOWUP_DUE);
    }

    @Bean
    public Binding riskDlqBinding() {
        return BindingBuilder.bind(riskDlq()).to(riskDlx()).with(RK_DEAD_ALL);
    }

    /**
     * 消息体用 JSON 而不是 JDK 序列化：可读、跨版本兼容，也不会因实体字段变化产生无法解析的二进制消息。
     *
     * <p>必须注册 {@code JavaTimeModule}：事件里有 {@code Instant} 字段，默认 ObjectMapper 不支持
     * Java 8 时间类型，会在发送时直接抛 {@code InvalidDefinitionException}。
     */
    @Bean
    public MessageConverter riskMessageConverter() {
        return new Jackson2JsonMessageConverter(messageObjectMapper());
    }

    /** 与缓存侧同口径：时间写 ISO 字符串、枚举按名称、反序列化忽略未知字段。 */
    static ObjectMapper messageObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /** 统一生成业务队列：死信交换机与路由键在这里一次性配上，调用方只给队列名。 */
    Queue businessQueue(String name) {
        String deadRoutingKey = BUSINESS_QUEUES.get(name);
        if (deadRoutingKey == null) {
            throw new IllegalArgumentException(
                    "未登记的队列：" + name + "，请先在 BUSINESS_QUEUES 中注册死信路由键");
        }
        return QueueBuilder.durable(name)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(deadRoutingKey)
                .build();
    }
}
