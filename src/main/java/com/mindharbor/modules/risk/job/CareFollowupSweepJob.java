package com.mindharbor.modules.risk.job;

import com.mindharbor.modules.risk.entity.CareFollowup;
import com.mindharbor.modules.risk.entity.RiskAlert;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.CareFollowupMapper;
import com.mindharbor.modules.risk.mapper.RiskAlertMapper;
import com.mindharbor.modules.risk.mq.RiskMqConfig;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 回访任务兜底扫描。
 *
 * <p>为什么需要它：24h 延迟消息是"闹钟"，但闹钟会丢——broker 在消息 TTL 期间重启、
 * 队列被误删、延迟消息被拒。任务的真源是 {@code care_followups}，
 * 这里按 {@code status=PENDING AND due_at <= now} 把漏掉的重新投进队列。
 *
 * <p>不直接发通知：投递到 {@code q.risk.followup.due} 让消费者统一处理状态流转，
 * 避免这里和消费者各写一套"查用户 → 发通知 → 标记"的逻辑。
 */
@Component
@ConditionalOnProperty(
        name = "risk.alert.followup-sweep.enabled", havingValue = "true", matchIfMissing = true)
public class CareFollowupSweepJob {

    private static final Logger log = LoggerFactory.getLogger(CareFollowupSweepJob.class);

    private static final String LOG_PREFIX = "[risk-followup-sweep]";

    private final CareFollowupMapper careFollowupMapper;

    private final RiskAlertMapper riskAlertMapper;

    private final AmqpTemplate amqpTemplate;

    private final int batchSize;

    /** 超过这个天数还没结掉的任务直接作废，避免永远重试。 */
    private final int expireDays;

    public CareFollowupSweepJob(
            CareFollowupMapper careFollowupMapper,
            RiskAlertMapper riskAlertMapper,
            AmqpTemplate amqpTemplate,
            @Value("${risk.alert.followup-sweep.batch-size:50}") int batchSize,
            @Value("${risk.alert.followup-sweep.expire-days:7}") int expireDays) {
        this.careFollowupMapper = careFollowupMapper;
        this.riskAlertMapper = riskAlertMapper;
        this.amqpTemplate = amqpTemplate;
        this.batchSize = Math.max(batchSize, 1);
        this.expireDays = Math.max(expireDays, 1);
    }

    @Scheduled(cron = "${risk.alert.followup-sweep.cron:0 */5 * * * *}")
    public void sweep() {
        sweepFor(Instant.now());
    }

    /** 抽成可传入当前时间的方法，单测不依赖真实时钟。 */
    int sweepFor(Instant now) {
        List<CareFollowup> due;
        try {
            due = careFollowupMapper.findDue(now, batchSize);
        } catch (RuntimeException exception) {
            log.warn("{} failed to load due followups", LOG_PREFIX, exception);
            return 0;
        }
        if (due == null || due.isEmpty()) {
            return 0;
        }

        Instant expireBefore = now.minus(expireDays, ChronoUnit.DAYS);
        int redispatched = 0;
        for (CareFollowup followup : due) {
            try {
                if (followup.getDueAt().isBefore(expireBefore)) {
                    // 拖了这么久还没发出去，多半是用户已注销或数据异常；结掉，别永远重试。
                    careFollowupMapper.markSkipped(followup.getId());
                    log.warn("{} expired, skipped, alertId={}", LOG_PREFIX, followup.getAlertId());
                    continue;
                }
                RiskAlert alert = riskAlertMapper.findByAlertId(followup.getAlertId()).orElse(null);
                if (alert == null) {
                    // 主表没了（用户注销级联删除），任务没有意义。
                    careFollowupMapper.markSkipped(followup.getId());
                    log.warn("{} alert missing, skipped, alertId={}",
                            LOG_PREFIX, followup.getAlertId());
                    continue;
                }
                amqpTemplate.convertAndSend(
                        RiskMqConfig.EXCHANGE, RiskMqConfig.RK_FOLLOWUP_DUE, eventOf(alert));
                redispatched++;
            } catch (RuntimeException exception) {
                log.warn("{} failed, alertId={}", LOG_PREFIX, followup.getAlertId(), exception);
            }
        }
        if (redispatched > 0) {
            log.info("{} redispatched={}", LOG_PREFIX, redispatched);
        }
        return redispatched;
    }

    static RiskAlertEvent eventOf(RiskAlert alert) {
        RiskAlertEvent event = new RiskAlertEvent();
        event.setAlertId(alert.getAlertId());
        event.setUserId(alert.getUserId());
        event.setSource(alert.getSource());
        event.setLevel(alert.getLevel());
        event.setSummary(alert.getSummary());
        event.setOccurredAt(alert.getOccurredAt());
        // 回访消费者不看 refs，省掉一次 JSON 解析。
        event.setRefs(Map.of());
        return event;
    }
}
