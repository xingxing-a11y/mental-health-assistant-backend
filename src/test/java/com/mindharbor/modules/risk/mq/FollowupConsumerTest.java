package com.mindharbor.modules.risk.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mindharbor.modules.notification.service.NotificationService;
import com.mindharbor.modules.risk.entity.CareFollowup;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.CareFollowupMapper;
import com.mindharbor.modules.risk.mapper.RiskAlertConsumptionMapper;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 24h 回访消费者：队列只是闹钟，是否真的要发由 care_followups 的状态决定。
 */
class FollowupConsumerTest {

    private final List<String> pushTargets = new ArrayList<>();

    private final List<String> transitions = new ArrayList<>();

    private int insertResult = 1;

    private CareFollowup followup;

    private User user;

    private FollowupConsumer consumer;

    @BeforeEach
    void setUp() {
        followup = new CareFollowup();
        followup.setId(5L);
        followup.setAlertId("assessment:7");
        followup.setUserId(9L);
        followup.setStatus(CareFollowup.Status.PENDING);

        user = new User();
        user.setId(9L);
        user.setStatus(User.Status.ACTIVE);

        consumer = new FollowupConsumer(
                fakeConsumptionMapper(), fakeFollowupMapper(), fakeUserMapper(), fakeNotificationService());
    }

    @Test
    @DisplayName("PENDING + 用户正常：发关怀通知并标记 SENT")
    void pendingActiveUserGetsFollowup() {
        consumer.onMessage(event());

        assertEquals(1, pushTargets.size(), "应当发出一条关怀通知");
        assertEquals(List.of("markSent"), transitions);
    }

    @Test
    @DisplayName("任务已被处理过：不再发，避免延迟消息与兜底扫描各发一条")
    void alreadySentIsSkipped() {
        followup.setStatus(CareFollowup.Status.SENT);

        consumer.onMessage(event());

        assertEquals(0, pushTargets.size());
        assertEquals(0, transitions.size(), "已处理的任务不该再改状态");
    }

    @Test
    @DisplayName("用户已注销：标记 SKIPPED 而不是一直挂着")
    void inactiveUserIsMarkedSkipped() {
        user.setStatus(User.Status.CLOSED);

        consumer.onMessage(event());

        assertEquals(0, pushTargets.size());
        assertEquals(List.of("markSkipped"), transitions,
                "不标记的话，兜底扫描会永远捞到这条任务");
    }

    @Test
    @DisplayName("用户记录不存在：同样跳过")
    void missingUserIsMarkedSkipped() {
        user = null;

        consumer.onMessage(event());

        assertEquals(List.of("markSkipped"), transitions);
    }

    @Test
    @DisplayName("任务行不存在：不发也不改状态，只留告警")
    void missingFollowupRowDoesNothing() {
        followup = null;

        consumer.onMessage(event());

        assertEquals(0, pushTargets.size());
        assertEquals(0, transitions.size());
    }

    @Test
    @DisplayName("重复消费被幂等记录挡住")
    void duplicateIsIgnored() {
        insertResult = 0;

        consumer.onMessage(event());

        assertEquals(0, pushTargets.size());
        assertEquals(0, transitions.size());
    }

    private static RiskAlertEvent event() {
        RiskAlertEvent event = new RiskAlertEvent();
        event.setAlertId("assessment:7");
        event.setUserId(9L);
        event.setSource(RiskAlertEvent.Source.ASSESSMENT_HIGH);
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
                    return "insertIgnore".equals(method.getName()) ? insertResult : null;
                });
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
                        case "findByAlertId" -> Optional.ofNullable(followup);
                        case "markSent" -> {
                            transitions.add("markSent");
                            yield 1;
                        }
                        case "markSkipped" -> {
                            transitions.add("markSkipped");
                            yield 1;
                        }
                        default -> null;
                    };
                });
    }

    private UserMapper fakeUserMapper() {
        return (UserMapper) Proxy.newProxyInstance(
                UserMapper.class.getClassLoader(),
                new Class<?>[] {UserMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("findById".equals(method.getName())) {
                        return Optional.ofNullable(user);
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
                        pushTargets.add(String.valueOf(args[0]));
                    }
                    return null;
                });
    }
}
