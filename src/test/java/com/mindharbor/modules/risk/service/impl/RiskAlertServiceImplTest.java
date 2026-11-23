package com.mindharbor.modules.risk.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.modules.risk.entity.CareFollowup;
import com.mindharbor.modules.risk.entity.RiskAlert;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.CareFollowupMapper;
import com.mindharbor.modules.risk.mapper.RiskAlertMapper;
import com.mindharbor.modules.risk.mq.RiskMqConfig;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 发布端的三条硬约束：
 * <ol>
 *   <li>先写库、<strong>提交后</strong>才发消息（否则写库回滚会留下幽灵事件）；</li>
 *   <li>写入撞唯一键时视为重复事件，不重发；</li>
 *   <li>发送失败只标记状态，绝不把异常抛给业务调用方（对话 HIGH 分支不能被 MQ 拖垮）。</li>
 * </ol>
 */
class RiskAlertServiceImplTest {

    private static final long DELAY_MILLIS = 60_000L;

    private final List<RiskAlert> insertedAlerts = new ArrayList<>();

    private final List<CareFollowup> insertedFollowups = new ArrayList<>();

    private final Map<String, String> statusUpdates = new HashMap<>();

    private final List<String> sentRoutingKeys = new ArrayList<>();

    private boolean duplicateKey;

    private boolean sendFails;

    private RiskAlertServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RiskAlertServiceImpl(
                fakeAlertMapper(), fakeFollowupMapper(), fakeTemplate(), DELAY_MILLIS);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("新事件：先写库，事务提交后才投递")
    void writesBeforeSendingAfterCommit() {
        boolean fresh = service.publish(event(RiskAlertEvent.Source.ASSESSMENT_HIGH));

        assertTrue(fresh);
        assertEquals(1, insertedAlerts.size());
        assertEquals(1, insertedFollowups.size());
        assertEquals(0, sentRoutingKeys.size(), "事务还没提交就发消息，会造成「消息已发、库回滚」的幽灵事件");

        commit();

        assertEquals(List.of(RiskMqConfig.RK_ASSESSMENT), sentRoutingKeys);
    }

    @Test
    @DisplayName("重复事件：唯一键冲突即幂等返回，既不写库也不重发")
    void duplicateEventIsIgnored() {
        duplicateKey = true;

        boolean fresh = service.publish(event(RiskAlertEvent.Source.CHAT_HIGH));

        assertFalse(fresh);
        assertEquals(0, insertedAlerts.size());
        assertEquals(0, insertedFollowups.size());

        commit();

        assertEquals(0, sentRoutingKeys.size(), "重复事件不能再发一次消息");
    }

    @Test
    @DisplayName("对话高危不产生回访任务：当场已给危机提示与热线，24h 后再推是打扰")
    void chatHighRiskSkipsFollowup() {
        service.publish(event(RiskAlertEvent.Source.CHAT_HIGH));

        assertEquals(1, insertedAlerts.size());
        assertEquals(0, insertedFollowups.size());
    }

    @Test
    @DisplayName("发送失败不外抛，只把状态标成 FAILED 交给补偿任务")
    void sendFailureIsSwallowed() {
        sendFails = true;

        service.publish(event(RiskAlertEvent.Source.DIARY_STREAK));

        commit();

        assertEquals("FAILED", statusUpdates.get("diary:9:20261123"),
                "发送失败必须留下 FAILED 状态，否则补偿任务永远扫不到它");
    }

    @Test
    @DisplayName("补偿重投只发送、不写库：调 publish 会撞唯一键直接返回，消息永远发不出去")
    void republishOnlySends() {
        service.republish(event(RiskAlertEvent.Source.CHAT_HIGH));

        assertEquals(0, insertedAlerts.size(), "补偿路径不能重复写事件表");
        assertEquals(0, insertedFollowups.size());
        assertEquals(List.of(RiskMqConfig.RK_CHAT), sentRoutingKeys);
    }

    @Test
    @DisplayName("三个来源路由到各自的 routing key")
    void routingKeyFollowsSource() {
        service.republish(event(RiskAlertEvent.Source.CHAT_HIGH));
        service.republish(event(RiskAlertEvent.Source.ASSESSMENT_HIGH));
        service.republish(event(RiskAlertEvent.Source.DIARY_STREAK));

        assertEquals(
                List.of(RiskMqConfig.RK_CHAT, RiskMqConfig.RK_ASSESSMENT, RiskMqConfig.RK_DIARY),
                sentRoutingKeys);
    }

    @Test
    @DisplayName("落库实体带上幂等键与描述，且不包含用户原文")
    void entityCarriesKeyFields() {
        RiskAlert entity = RiskAlertServiceImpl.toEntity(event(RiskAlertEvent.Source.DIARY_STREAK));

        assertEquals("diary:9:20261123", entity.getAlertId());
        assertEquals(Long.valueOf(9L), entity.getUserId());
        assertEquals(RiskAlertEvent.Source.DIARY_STREAK, entity.getSource());
        assertEquals(RiskAlert.PublishStatus.PENDING, entity.getPublishStatus());
        assertEquals("连续 3 天记录负面情绪", entity.getSummary());
    }

    @Test
    @DisplayName("回访任务的到期时间 = 事件时间 + 配置的延迟")
    void followupDueAtUsesConfiguredDelay() {
        RiskAlertEvent event = event(RiskAlertEvent.Source.ASSESSMENT_HIGH);

        CareFollowup followup = RiskAlertServiceImpl.toFollowup(event, DELAY_MILLIS);

        assertEquals(event.getOccurredAt().plusMillis(DELAY_MILLIS), followup.getDueAt());
        assertEquals(CareFollowup.Status.PENDING, followup.getStatus());
        assertEquals("assessment:7", followup.getAlertId());
    }

    private void commit() {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static RiskAlertEvent event(RiskAlertEvent.Source source) {
        RiskAlertEvent event = new RiskAlertEvent();
        event.setUserId(9L);
        event.setSource(source);
        event.setLevel(RiskAlertEvent.Level.HIGH);
        event.setOccurredAt(Instant.parse("2026-11-23T02:00:00Z"));
        event.setSummary("连续 3 天记录负面情绪");
        event.setRefs(Map.of("messageId", "1"));
        event.setAlertId(switch (source) {
            case CHAT_HIGH -> "chat:1";
            case ASSESSMENT_HIGH -> "assessment:7";
            case DIARY_STREAK -> "diary:9:20261123";
        });
        return event;
    }

    private RiskAlertMapper fakeAlertMapper() {
        return (RiskAlertMapper) Proxy.newProxyInstance(
                RiskAlertMapper.class.getClassLoader(),
                new Class<?>[] {RiskAlertMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    switch (method.getName()) {
                        case "insert" -> {
                            if (duplicateKey) {
                                throw new DuplicateKeyException("uk_risk_alerts_alert_id");
                            }
                            insertedAlerts.add((RiskAlert) args[0]);
                            return 1;
                        }
                        case "markPublishStatus" -> {
                            statusUpdates.put((String) args[0], String.valueOf(args[1]));
                            return 1;
                        }
                        case "incrementPublishAttempts" -> {
                            return 1;
                        }
                        default -> {
                            return null;
                        }
                    }
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
                    if ("insertIgnore".equals(method.getName())) {
                        insertedFollowups.add((CareFollowup) args[0]);
                        return 1;
                    }
                    if ("insert".equals(method.getName())) {
                        insertedFollowups.add((CareFollowup) args[0]);
                        return 1;
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
                        if (sendFails) {
                            throw new AmqpException("broker down");
                        }
                        sentRoutingKeys.add((String) args[1]);
                    }
                    return null;
                });
    }
}
