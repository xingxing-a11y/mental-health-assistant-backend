package com.mindharbor.modules.risk.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.modules.risk.entity.RiskAlert;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.RiskAlertConsumptionMapper;
import com.mindharbor.modules.risk.mapper.RiskAlertMapper;
import com.mindharbor.modules.risk.service.RiskAlertService;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 补偿任务：只做两件事——重投未成功的事件、清理过期幂等记录。 */
class RiskAlertRepublishJobTest {

    private static final int MAX_ATTEMPTS = 5;

    private static final int BATCH_SIZE = 100;

    private static final long RETENTION_DAYS = 90L;

    private final List<String> republished = new ArrayList<>();

    private final List<Instant> purgeCutoffs = new ArrayList<>();

    private final Set<String> failing = new HashSet<>();

    @Test
    @DisplayName("扫到的每一条都重投，且不重复投")
    void republishesEveryCandidate() {
        jobWith(List.of(alert("chat:1"), alert("assessment:2"))).republishPending();

        assertEquals(List.of("chat:1", "assessment:2"), republished);
    }

    @Test
    @DisplayName("没有候选时不发起任何投递")
    void emptyCandidateListDoesNothing() {
        jobWith(List.of()).republishPending();

        assertEquals(0, republished.size());
    }

    @Test
    @DisplayName("单条失败不影响后续：一条抛异常，其余照常投")
    void singleFailureDoesNotStopOthers() {
        failing.add("chat:1");

        jobWith(List.of(alert("chat:1"), alert("chat:2"))).republishPending();

        assertEquals(List.of("chat:2"), republished, "前一条抛异常不能让后面的被跳过");
    }

    @Test
    @DisplayName("每次都清理过期幂等记录，截止时间是 now - 保留天数")
    void purgesExpiredConsumptions() {
        Instant before = Instant.now();

        jobWith(List.of()).republishPending();

        assertEquals(1, purgeCutoffs.size(), "每轮都应清理一次");
        Instant cutoff = purgeCutoffs.get(0);
        long days = Duration.between(cutoff, Instant.now()).toDays();
        assertTrue(days >= RETENTION_DAYS - 1 && days <= RETENTION_DAYS + 1,
                "截止时间应约为 now - " + RETENTION_DAYS + " 天，实际相差 " + days + " 天");
        assertTrue(cutoff.isBefore(before), "截止时间必须早于当前时间");
    }

    @Test
    @DisplayName("实体转事件：refs 的 JSON 能解析回 Map，字段不丢")
    void entityToEventRoundTrip() {
        RiskAlert alert = alert("diary:9:20261123");
        alert.setRefs("{\"messageId\":\"42\",\"sessionId\":\"7\"}");

        RiskAlertEvent event = RiskAlertRepublishJob.fromEntity(alert);

        assertNotNull(event);
        assertEquals("diary:9:20261123", event.getAlertId());
        assertEquals(Long.valueOf(9L), event.getUserId());
        assertEquals(RiskAlertEvent.Source.DIARY_STREAK, event.getSource());
        assertEquals(RiskAlertEvent.Level.HIGH, event.getLevel());
        assertEquals(Map.of("messageId", "42", "sessionId", "7"), event.getRefs());
        assertEquals(alert.getOccurredAt(), event.getOccurredAt());
    }

    @Test
    @DisplayName("refs 为空或脏数据时返回空 Map，不抛异常")
    void brokenRefsIsSafe() {
        RiskAlert empty = alert("chat:1");
        empty.setRefs(null);
        assertEquals(Map.of(), RiskAlertRepublishJob.fromEntity(empty).getRefs());

        RiskAlert broken = alert("chat:2");
        broken.setRefs("{not-json");
        assertEquals(Map.of(), RiskAlertRepublishJob.fromEntity(broken).getRefs());
    }

    private RiskAlertRepublishJob jobWith(List<RiskAlert> candidates) {
        return new RiskAlertRepublishJob(
                fakeAlertMapper(candidates),
                fakeConsumptionMapper(),
                fakeRiskAlertService(),
                30L,
                MAX_ATTEMPTS,
                BATCH_SIZE,
                RETENTION_DAYS);
    }

    private static RiskAlert alert(String alertId) {
        RiskAlert alert = new RiskAlert();
        alert.setAlertId(alertId);
        alert.setUserId(9L);
        alert.setSource(alertId.startsWith("chat")
                ? RiskAlertEvent.Source.CHAT_HIGH
                : alertId.startsWith("assessment")
                        ? RiskAlertEvent.Source.ASSESSMENT_HIGH
                        : RiskAlertEvent.Source.DIARY_STREAK);
        alert.setLevel(RiskAlertEvent.Level.HIGH);
        alert.setSummary("测试事件");
        alert.setOccurredAt(Instant.parse("2026-11-23T02:00:00Z"));
        alert.setPublishStatus(RiskAlert.PublishStatus.FAILED);
        return alert;
    }

    private RiskAlertMapper fakeAlertMapper(List<RiskAlert> candidates) {
        return (RiskAlertMapper) Proxy.newProxyInstance(
                RiskAlertMapper.class.getClassLoader(),
                new Class<?>[] {RiskAlertMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("findPublishable".equals(method.getName())) {
                        return candidates;
                    }
                    if ("incrementPublishAttempts".equals(method.getName())) {
                        return 1;
                    }
                    return null;
                });
    }

    private RiskAlertConsumptionMapper fakeConsumptionMapper() {
        return (RiskAlertConsumptionMapper) Proxy.newProxyInstance(
                RiskAlertConsumptionMapper.class.getClassLoader(),
                new Class<?>[] {RiskAlertConsumptionMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("deleteOlderThan".equals(method.getName())) {
                        purgeCutoffs.add((Instant) args[0]);
                        return 0;
                    }
                    return null;
                });
    }

    private RiskAlertService fakeRiskAlertService() {
        return (RiskAlertService) Proxy.newProxyInstance(
                RiskAlertService.class.getClassLoader(),
                new Class<?>[] {RiskAlertService.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("republish".equals(method.getName())) {
                        RiskAlertEvent event = (RiskAlertEvent) args[0];
                        if (failing.contains(event.getAlertId())) {
                            throw new IllegalStateException("db down");
                        }
                        republished.add(event.getAlertId());
                        return null;
                    }
                    return null;
                });
    }
}
