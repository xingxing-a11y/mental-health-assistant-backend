package com.mindharbor.modules.risk.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindharbor.modules.risk.entity.RiskAlert;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.RiskAlertConsumptionMapper;
import com.mindharbor.modules.risk.mapper.RiskAlertMapper;
import com.mindharbor.modules.risk.service.RiskAlertService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 投递补偿任务：把没成功发出去的事件重新投一遍。
 *
 * <p>状态的真源是 {@code risk_alerts.publish_status}，所以在发消息之前进程崩溃、或者 broker
 * 短暂不可用造成的事件，都能被这里捞回来。
 *
 * <p>顺手清理过期的消费幂等记录 —— 那张表只增不减，不清就会变成另一个"无淘汰的 Map"。
 */
@Component
@ConditionalOnProperty(
        name = "risk.alert.republish.enabled", havingValue = "true", matchIfMissing = true)
public class RiskAlertRepublishJob {

    private static final Logger log = LoggerFactory.getLogger(RiskAlertRepublishJob.class);

    private static final String LOG_PREFIX = "[risk-republish]";

    /** refs 只有 String→String。 */
    private static final ObjectMapper REFS_MAPPER = new ObjectMapper();

    private final RiskAlertMapper riskAlertMapper;

    private final RiskAlertConsumptionMapper consumptionMapper;

    private final RiskAlertService riskAlertService;

    private final long minAgeSeconds;

    private final int maxAttempts;

    private final int batchSize;

    private final long consumptionRetentionDays;

    public RiskAlertRepublishJob(
            RiskAlertMapper riskAlertMapper,
            RiskAlertConsumptionMapper consumptionMapper,
            RiskAlertService riskAlertService,
            @Value("${risk.alert.republish.min-age-seconds:30}") long minAgeSeconds,
            @Value("${risk.alert.republish.max-attempts:5}") int maxAttempts,
            @Value("${risk.alert.republish.batch-size:100}") int batchSize,
            @Value("${risk.alert.republish.consumption-retention-days:90}")
                    long consumptionRetentionDays) {
        this.riskAlertMapper = riskAlertMapper;
        this.consumptionMapper = consumptionMapper;
        this.riskAlertService = riskAlertService;
        this.minAgeSeconds = minAgeSeconds;
        this.maxAttempts = maxAttempts;
        this.batchSize = Math.max(batchSize, 1);
        this.consumptionRetentionDays = Math.max(consumptionRetentionDays, 1L);
    }

    @Scheduled(cron = "${risk.alert.republish.cron:0 */1 * * * *}")
    public void republishPending() {
        Instant now = Instant.now();
        Instant before = now.minusSeconds(minAgeSeconds);
        try {
            List<RiskAlert> candidates =
                    riskAlertMapper.findPublishable(before, maxAttempts, batchSize);
            if (candidates == null || candidates.isEmpty()) {
                log.debug("{} nothing to republish", LOG_PREFIX);
            } else {
                int republishedCount = republishAll(candidates);
                log.info("{} finished, candidates={}, republished={}",
                        LOG_PREFIX, candidates.size(), republishedCount);
            }
        } catch (RuntimeException exception) {
            // 取候选失败不影响清理：清理是按时间删，与本次扫描无关。
            log.warn("{} failed to load candidates", LOG_PREFIX, exception);
        }
        purgeExpiredConsumptions(now);
    }

    private int republishAll(List<RiskAlert> candidates) {
        int republishedCount = 0;
        for (RiskAlert alert : candidates) {
            try {
                riskAlertService.republish(fromEntity(alert));
                republishedCount++;
            } catch (RuntimeException exception) {
                // 单条失败不能影响同一批里的其他事件。
                log.warn("{} failed, alertId={}", LOG_PREFIX, alert.getAlertId(), exception);
            }
        }
        return republishedCount;
    }

    /** 消费幂等表只增不减，不清理就会变成另一个"无淘汰的 Map"。 */
    private void purgeExpiredConsumptions(Instant now) {
        try {
            int removed = consumptionMapper.deleteOlderThan(
                    retentionCutoff(now, consumptionRetentionDays));
            if (removed > 0) {
                log.info("{} purged {} expired consumption records", LOG_PREFIX, removed);
            }
        } catch (RuntimeException exception) {
            log.warn("{} failed to purge consumption records", LOG_PREFIX, exception);
        }
    }

    static RiskAlertEvent fromEntity(RiskAlert alert) {
        RiskAlertEvent event = new RiskAlertEvent();
        event.setAlertId(alert.getAlertId());
        event.setUserId(alert.getUserId());
        event.setSource(alert.getSource());
        event.setLevel(alert.getLevel());
        event.setSummary(alert.getSummary());
        event.setRefs(parseRefs(alert.getRefs()));
        event.setOccurredAt(alert.getOccurredAt());
        return event;
    }

    private static Map<String, String> parseRefs(String refs) {
        if (refs == null || refs.isBlank()) {
            return Map.of();
        }
        try {
            return REFS_MAPPER.readValue(refs, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private static Instant retentionCutoff(Instant now, long days) {
        return now.minus(days, ChronoUnit.DAYS);
    }
}
