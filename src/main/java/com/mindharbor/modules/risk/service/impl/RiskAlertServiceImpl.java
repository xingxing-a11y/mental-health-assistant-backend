package com.mindharbor.modules.risk.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindharbor.modules.risk.entity.CareFollowup;
import com.mindharbor.modules.risk.entity.RiskAlert;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.event.RiskAlertIds;
import com.mindharbor.modules.risk.mapper.CareFollowupMapper;
import com.mindharbor.modules.risk.mapper.RiskAlertMapper;
import com.mindharbor.modules.risk.mq.RiskMqConfig;
import com.mindharbor.modules.risk.service.RiskAlertService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 风险事件发布实现。 */
@Service
public class RiskAlertServiceImpl implements RiskAlertService {

    private static final Logger log = LoggerFactory.getLogger(RiskAlertServiceImpl.class);

    /** refs 只有 String→String，不需要注册时间模块。 */
    private static final ObjectMapper REFS_MAPPER = new ObjectMapper();

    private final RiskAlertMapper riskAlertMapper;

    private final CareFollowupMapper careFollowupMapper;

    private final AmqpTemplate amqpTemplate;

    private final long followupDelayMillis;

    public RiskAlertServiceImpl(
            RiskAlertMapper riskAlertMapper,
            CareFollowupMapper careFollowupMapper,
            AmqpTemplate amqpTemplate,
            @Value("${risk.alert.followup.delay-millis:86400000}") long followupDelayMillis) {
        this.riskAlertMapper = riskAlertMapper;
        this.careFollowupMapper = careFollowupMapper;
        this.amqpTemplate = amqpTemplate;
        this.followupDelayMillis = followupDelayMillis;
    }

    @Override
    @Transactional
    public boolean publish(RiskAlertEvent event) {
        try {
            riskAlertMapper.insert(toEntity(event));
        } catch (DuplicateKeyException duplicate) {
            // 唯一键冲突 = 这条事件已经发过，幂等返回。
            return false;
        } catch (RuntimeException failure) {
            // 写库失败也不外抛：调用方是对话与测评的主流程，
            // 预警是尽力而为，绝不能因为预警写不进去而丢用户的核心数据。
            log.warn("[risk-mq] persist failed, alertId={}", event.getAlertId(), failure);
            return false;
        }

        // 对话高危当场已经给了危机提示与热线，24h 再推一条属于重复打扰，所以不建回访任务。
        if (event.getSource() != RiskAlertEvent.Source.CHAT_HIGH) {
            careFollowupMapper.insertIgnore(toFollowup(event, followupDelayMillis));
        }

        sendAfterCommit(event);
        return true;
    }

    @Override
    public void republish(RiskAlertEvent event) {
        // 补偿路径不能走 publish：那边第一句 insert 会撞唯一键然后直接返回，消息永远发不出去。
        riskAlertMapper.incrementPublishAttempts(event.getAlertId());
        sendQuietly(event);
    }

    @Override
    @Transactional
    public boolean publishChatHighRisk(Long userId, long messageId, long sessionId) {
        RiskAlertEvent event = baseEvent(userId, RiskAlertEvent.Source.CHAT_HIGH);
        event.setAlertId(RiskAlertIds.chat(messageId));
        // 摘要只描述事件本身，绝不写入用户原话。
        event.setSummary("对话内容命中高危词");
        event.setRefs(Map.of(
                "messageId", String.valueOf(messageId),
                "sessionId", String.valueOf(sessionId)));
        return publish(event);
    }

    @Override
    @Transactional
    public boolean publishAssessmentHighRisk(Long userId, long resultId, int standardScore) {
        RiskAlertEvent event = baseEvent(userId, RiskAlertEvent.Source.ASSESSMENT_HIGH);
        event.setAlertId(RiskAlertIds.assessment(resultId));
        event.setSummary("测评标准分 " + standardScore + "，达到关注阈值");
        event.setRefs(Map.of("resultId", String.valueOf(resultId)));
        return publish(event);
    }

    @Override
    @Transactional
    public boolean publishDiaryStreak(Long userId, LocalDate day, int negativeDays) {
        RiskAlertEvent event = baseEvent(userId, RiskAlertEvent.Source.DIARY_STREAK);
        event.setAlertId(RiskAlertIds.diary(userId, day));
        event.setSummary("连续 " + negativeDays + " 天记录负面情绪");
        event.setRefs(Map.of("day", day.toString()));
        return publish(event);
    }

    private static RiskAlertEvent baseEvent(Long userId, RiskAlertEvent.Source source) {
        RiskAlertEvent event = new RiskAlertEvent();
        event.setUserId(userId);
        event.setSource(source);
        event.setLevel(RiskAlertEvent.Level.HIGH);
        event.setOccurredAt(Instant.now());
        return event;
    }

    /** 事务提交后再投递，避免"消息已发、写库回滚"的幽灵事件。 */
    private void sendAfterCommit(RiskAlertEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendQuietly(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendQuietly(event);
            }
        });
    }

    /**
     * 投递事件，任何失败都只记状态、不外抛。
     *
     * <p>调用方是对话的 HIGH 分支（SSE 路径）与定时扫描任务，MQ 故障绝不能变成用户侧的报错。
     */
    void sendQuietly(RiskAlertEvent event) {
        try {
            // 用 AmqpTemplate 的三参重载：CorrelationData 是 RabbitTemplate 类的特有 API，
            // 接口上没有对应重载，而注入接口才能用动态代理做单测。
            // 代价是 confirm 回调拿不到 alertId，只能记日志；状态以同步发送结果为准，
            // 真正的兜底是补偿任务按 PENDING/FAILED 重投。
            amqpTemplate.convertAndSend(
                    RiskMqConfig.EXCHANGE,
                    routingKeyOf(event.getSource()),
                    event);
            markStatus(event.getAlertId(), RiskAlert.PublishStatus.SENT);
        } catch (RuntimeException exception) {
            log.warn("[risk-mq] publish failed, alertId={}", event.getAlertId(), exception);
            markStatus(event.getAlertId(), RiskAlert.PublishStatus.FAILED);
        }
    }

    private void markStatus(String alertId, RiskAlert.PublishStatus status) {
        try {
            riskAlertMapper.markPublishStatus(alertId, status);
        } catch (RuntimeException exception) {
            // 标记状态本身失败不能影响主流程，补偿任务会按 PENDING/FAILED 兜底。
            log.warn("[risk-mq] mark {} failed, alertId={}", status, alertId, exception);
        }
    }

    static String routingKeyOf(RiskAlertEvent.Source source) {
        return switch (source) {
            case CHAT_HIGH -> RiskMqConfig.RK_CHAT;
            case ASSESSMENT_HIGH -> RiskMqConfig.RK_ASSESSMENT;
            case DIARY_STREAK -> RiskMqConfig.RK_DIARY;
        };
    }

    static RiskAlert toEntity(RiskAlertEvent event) {
        RiskAlert entity = new RiskAlert();
        entity.setAlertId(event.getAlertId());
        entity.setUserId(event.getUserId());
        entity.setSource(event.getSource());
        entity.setLevel(event.getLevel());
        entity.setSummary(event.getSummary());
        entity.setRefs(refsToJson(event.getRefs()));
        entity.setOccurredAt(event.getOccurredAt());
        entity.setPublishStatus(RiskAlert.PublishStatus.PENDING);
        entity.setPublishAttempts(0);
        return entity;
    }

    static CareFollowup toFollowup(RiskAlertEvent event, long delayMillis) {
        CareFollowup followup = new CareFollowup();
        followup.setAlertId(event.getAlertId());
        followup.setUserId(event.getUserId());
        followup.setDueAt(event.getOccurredAt().plusMillis(delayMillis));
        followup.setStatus(CareFollowup.Status.PENDING);
        return followup;
    }

    private static String refsToJson(Map<String, String> refs) {
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        try {
            return REFS_MAPPER.writeValueAsString(refs);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
