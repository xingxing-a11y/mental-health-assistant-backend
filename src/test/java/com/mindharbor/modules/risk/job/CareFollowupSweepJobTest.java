package com.mindharbor.modules.risk.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.modules.risk.entity.CareFollowup;
import com.mindharbor.modules.risk.entity.RiskAlert;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.CareFollowupMapper;
import com.mindharbor.modules.risk.mapper.RiskAlertMapper;
import com.mindharbor.modules.risk.mq.RiskMqConfig;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;

/**
 * 兜底扫描：只补发「已到期但仍是 PENDING」的任务，并把没救的结掉。
 */
class CareFollowupSweepJobTest {

    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    private final List<String> dispatched = new ArrayList<>();

    private final List<Long> skipped = new ArrayList<>();

    private final Map<String, RiskAlert> alertsByAlertId = new HashMap<>();

    private List<CareFollowup> dueTasks = new ArrayList<>();

    private boolean alertLookupFails;

    private boolean dispatchFails;

    @Test
    @DisplayName("到期任务补发到回访队列")
    void redispatchesDueTask() {
        givenAlert("assessment:7");
        dueTasks = List.of(followup(1L, "assessment:7", NOW.minusSeconds(60)));

        int count = job().sweepFor(NOW);

        assertEquals(1, count);
        assertEquals(1, dispatched.size(), "应当重新投递一条消息");
    }

    @Test
    @DisplayName("主表记录已不存在（用户注销级联删除）：标记跳过而不是一直重试")
    void missingAlertIsSkipped() {
        dueTasks = List.of(followup(1L, "assessment:7", NOW.minusSeconds(60)));

        job().sweepFor(NOW);

        assertEquals(List.of(1L), skipped);
        assertEquals(0, dispatched.size());
    }

    @Test
    @DisplayName("拖过作废期仍没发出去：标记跳过，不无限重试")
    void expiredTaskIsSkipped() {
        givenAlert("assessment:7");
        dueTasks = List.of(followup(1L, "assessment:7", NOW.minus(30, ChronoUnit.DAYS)));

        job().sweepFor(NOW);

        assertEquals(List.of(1L), skipped);
        assertEquals(0, dispatched.size(), "过期任务不该再发出关怀");
    }

    @Test
    @DisplayName("没有到期任务时什么都不做")
    void nothingDueDoesNothing() {
        dueTasks = List.of();

        assertEquals(0, job().sweepFor(NOW));
        assertEquals(0, dispatched.size());
    }

    @Test
    @DisplayName("单条投递失败不影响其他任务")
    void singleDispatchFailureDoesNotStopOthers() {
        givenAlert("assessment:7");
        givenAlert("assessment:8");
        dueTasks = List.of(
                followup(1L, "assessment:7", NOW.minusSeconds(60)),
                followup(2L, "assessment:8", NOW.minusSeconds(60)));
        dispatchFails = true;

        int count = job().sweepFor(NOW);

        assertEquals(0, count, "全部投递失败时计数为 0，但不应抛异常");
        assertEquals(0, skipped.size(), "投递失败不等于任务作废，要留给下一轮");
    }

    @Test
    @DisplayName("取到期任务失败时安静返回，不影响后续轮次")
    void loadFailureIsSwallowed() {
        jobWithFailingLoad().sweepFor(NOW);

        assertTrue(dispatched.isEmpty());
    }

    @Test
    @DisplayName("投递的消息带齐消费者需要的字段")
    void dispatchedEventCarriesSource() {
        givenAlert("assessment:7");
        dueTasks = List.of(followup(1L, "assessment:7", NOW.minusSeconds(60)));
        List<RiskAlertEvent> sent = new ArrayList<>();

        new CareFollowupSweepJob(
                fakeFollowupMapper(), fakeAlertMapper(), capturingTemplate(sent), 50, 7)
                .sweepFor(NOW);

        assertEquals(1, sent.size());
        assertEquals("assessment:7", sent.get(0).getAlertId());
        assertEquals(Long.valueOf(9L), sent.get(0).getUserId());
        assertEquals(RiskAlertEvent.Source.ASSESSMENT_HIGH, sent.get(0).getSource(),
                "消费者按来源决定文案，source 不能丢");
    }

    private void givenAlert(String alertId) {
        RiskAlert alert = new RiskAlert();
        alert.setAlertId(alertId);
        alert.setUserId(9L);
        alert.setSource(RiskAlertEvent.Source.ASSESSMENT_HIGH);
        alert.setLevel(RiskAlertEvent.Level.HIGH);
        alert.setSummary("测试");
        alert.setOccurredAt(NOW.minus(1, ChronoUnit.DAYS));
        alertsByAlertId.put(alertId, alert);
    }

    private static CareFollowup followup(long id, String alertId, Instant dueAt) {
        CareFollowup followup = new CareFollowup();
        followup.setId(id);
        followup.setAlertId(alertId);
        followup.setUserId(9L);
        followup.setDueAt(dueAt);
        followup.setStatus(CareFollowup.Status.PENDING);
        return followup;
    }

    private CareFollowupSweepJob job() {
        return new CareFollowupSweepJob(
                fakeFollowupMapper(), fakeAlertMapper(), fakeTemplate(), 50, 7);
    }

    private CareFollowupSweepJob jobWithFailingLoad() {
        CareFollowupMapper failing = (CareFollowupMapper) Proxy.newProxyInstance(
                CareFollowupMapper.class.getClassLoader(),
                new Class<?>[] {CareFollowupMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("findDue".equals(method.getName())) {
                        throw new IllegalStateException("db down");
                    }
                    return null;
                });
        return new CareFollowupSweepJob(failing, fakeAlertMapper(), fakeTemplate(), 50, 7);
    }

    private CareFollowupMapper fakeFollowupMapper() {
        return (CareFollowupMapper) Proxy.newProxyInstance(
                CareFollowupMapper.class.getClassLoader(),
                new Class<?>[] {CareFollowupMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    return switch (method.getName()) {
                        case "findDue" -> dueTasks;
                        case "markSkipped" -> {
                            skipped.add((Long) args[0]);
                            yield 1;
                        }
                        default -> null;
                    };
                });
    }

    private RiskAlertMapper fakeAlertMapper() {
        return (RiskAlertMapper) Proxy.newProxyInstance(
                RiskAlertMapper.class.getClassLoader(),
                new Class<?>[] {RiskAlertMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("findByAlertId".equals(method.getName())) {
                        if (alertLookupFails) {
                            throw new IllegalStateException("db down");
                        }
                        return Optional.ofNullable(alertsByAlertId.get((String) args[0]));
                    }
                    return null;
                });
    }

    private AmqpTemplate fakeTemplate() {
        return (AmqpTemplate) Proxy.newProxyInstance(
                AmqpTemplate.class.getClassLoader(),
                new Class<?>[] {AmqpTemplate.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("convertAndSend".equals(method.getName()) && args != null && args.length >= 2) {
                        if (dispatchFails) {
                            throw new AmqpException("broker down");
                        }
                        dispatched.add((String) args[1]);
                    }
                    return null;
                });
    }

    private AmqpTemplate capturingTemplate(List<RiskAlertEvent> sink) {
        return (AmqpTemplate) Proxy.newProxyInstance(
                AmqpTemplate.class.getClassLoader(),
                new Class<?>[] {AmqpTemplate.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("convertAndSend".equals(method.getName()) && args != null && args.length >= 3) {
                        sink.add((RiskAlertEvent) args[2]);
                    }
                    return null;
                });
    }
}
