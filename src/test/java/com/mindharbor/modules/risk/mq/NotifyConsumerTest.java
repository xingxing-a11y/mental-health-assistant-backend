package com.mindharbor.modules.risk.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.modules.notification.service.NotificationService;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.RiskAlertConsumptionMapper;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 站内信消费者：幂等靠消费记录，措辞随来源变化。 */
class NotifyConsumerTest {

    private final List<String> pushedTitles = new ArrayList<>();

    private final List<String> consumerNames = new ArrayList<>();

    private int insertResult = 1;

    private NotifyConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotifyConsumer(fakeConsumptionMapper(), fakeNotificationService());
    }

    @Test
    @DisplayName("首次消费：写幂等记录并发通知")
    void firstTimePushes() {
        consumer.onMessage(event(RiskAlertEvent.Source.CHAT_HIGH));

        assertEquals(List.of("NOTIFY"), consumerNames);
        assertEquals(1, pushedTitles.size());
    }

    @Test
    @DisplayName("重复消费：幂等记录插不进去就不发，避免同一条事件发两遍")
    void duplicateIsIgnored() {
        insertResult = 0;

        consumer.onMessage(event(RiskAlertEvent.Source.CHAT_HIGH));

        assertEquals(0, pushedTitles.size());
    }

    @Test
    @DisplayName("三种来源各自有标题，且都不为空")
    void titlesDifferBySource() {
        consumer.onMessage(event(RiskAlertEvent.Source.CHAT_HIGH));
        consumer.onMessage(event(RiskAlertEvent.Source.ASSESSMENT_HIGH));
        consumer.onMessage(event(RiskAlertEvent.Source.DIARY_STREAK));

        assertEquals(3, pushedTitles.size());
        assertEquals(3, pushedTitles.stream().distinct().count(), "三种来源的标题应各不相同");
        pushedTitles.forEach(title -> assertFalse(title.isBlank()));
    }

    @Test
    @DisplayName("正文不出现诊断性表述")
    void contentAvoidsDiagnosis() {
        String content = NotifyConsumer.contentOf(event(RiskAlertEvent.Source.ASSESSMENT_HIGH));

        assertTrue(content.contains("不是诊断"), "心理类文案必须明确不做诊断");
        assertFalse(content.contains("抑郁症"), "不能给出疾病名称");
    }

    private RiskAlertEvent event(RiskAlertEvent.Source source) {
        RiskAlertEvent event = new RiskAlertEvent();
        event.setAlertId("chat:1");
        event.setUserId(9L);
        event.setSource(source);
        event.setLevel(RiskAlertEvent.Level.HIGH);
        event.setOccurredAt(Instant.parse("2026-11-23T02:00:00Z"));
        event.setRefs(Map.of());
        return event;
    }

    private RiskAlertConsumptionMapper fakeConsumptionMapper() {
        return (RiskAlertConsumptionMapper) Proxy.newProxyInstance(
                RiskAlertConsumptionMapper.class.getClassLoader(),
                new Class<?>[] {RiskAlertConsumptionMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("insertIgnore".equals(method.getName())) {
                        consumerNames.add((String) args[1]);
                        return insertResult;
                    }
                    return null;
                });
    }

    private NotificationService fakeNotificationService() {
        return (NotificationService) Proxy.newProxyInstance(
                NotificationService.class.getClassLoader(),
                new Class<?>[] {NotificationService.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("pushSystem".equals(method.getName())) {
                        pushedTitles.add((String) args[1]);
                    }
                    return null;
                });
    }
}
