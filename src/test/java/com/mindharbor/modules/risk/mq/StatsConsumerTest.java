package com.mindharbor.modules.risk.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.RiskAlertConsumptionMapper;
import com.mindharbor.modules.risk.mapper.RiskAlertDailyStatMapper;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 统计消费者：按事件发生日计数，不是按消费日。 */
class StatsConsumerTest {

    private final List<String> countedSources = new ArrayList<>();

    private final List<LocalDate> countedDates = new ArrayList<>();

    private int insertResult = 1;

    private StatsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new StatsConsumer(fakeConsumptionMapper(), fakeStatMapper());
    }

    @Test
    @DisplayName("按事件发生日 + 来源计数")
    void countsByOccurredDateAndSource() {
        consumer.onMessage(event("2026-11-23T02:00:00Z"));

        assertEquals(List.of("DIARY_STREAK"), countedSources);
        assertEquals(List.of(LocalDate.of(2026, 11, 23)), countedDates);
    }

    @Test
    @DisplayName("跨时区边界：UTC 16:30 属于北京时间次日，统计要归到次日")
    void utcEveningCountsAsNextDay() {
        consumer.onMessage(event("2026-11-22T16:30:00Z"));

        assertEquals(LocalDate.of(2026, 11, 23), countedDates.get(0));
    }

    @Test
    @DisplayName("重复消费不重复计数")
    void duplicateIsNotCounted() {
        insertResult = 0;

        consumer.onMessage(event("2026-11-23T02:00:00Z"));

        assertEquals(0, countedSources.size());
    }

    private static RiskAlertEvent event(String occurredAt) {
        RiskAlertEvent event = new RiskAlertEvent();
        event.setAlertId("diary:9:20261123");
        event.setUserId(9L);
        event.setSource(RiskAlertEvent.Source.DIARY_STREAK);
        event.setLevel(RiskAlertEvent.Level.HIGH);
        event.setOccurredAt(Instant.parse(occurredAt));
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
                    return "insertIgnore".equals(method.getName()) ? insertResult : null;
                });
    }

    private RiskAlertDailyStatMapper fakeStatMapper() {
        return (RiskAlertDailyStatMapper) Proxy.newProxyInstance(
                RiskAlertDailyStatMapper.class.getClassLoader(),
                new Class<?>[] {RiskAlertDailyStatMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("increment".equals(method.getName())) {
                        countedDates.add((LocalDate) args[0]);
                        countedSources.add((String) args[1]);
                        return 1;
                    }
                    return null;
                });
    }
}
