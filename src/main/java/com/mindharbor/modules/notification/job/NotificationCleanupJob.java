package com.mindharbor.modules.notification.job;

import com.mindharbor.modules.notification.mapper.NotificationMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知归档清理任务：删除过期已满保留期的通知，已读记录由外键级联删除。
 * 与会话清理任务完全独立，互不影响。
 */
@Component
@ConditionalOnProperty(
        name = "job.notification-cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupJob.class);

    private static final String LOG_PREFIX = "[notification-cleanup]";

    /** 单轮最多清理的批次数，避免历史数据过多时任务长期占用调度线程。 */
    private static final int MAX_BATCHES_PER_RUN = 100;

    private final NotificationMapper mapper;

    private final long retentionDays;

    private final int batchSize;

    public NotificationCleanupJob(
            NotificationMapper mapper,
            @Value("${job.notification-cleanup.retention-days:180}") long retentionDays,
            @Value("${job.notification-cleanup.batch-size:200}") int batchSize) {
        this.mapper = mapper;
        this.retentionDays = retentionDays;
        this.batchSize = Math.max(batchSize, 1);
    }

    @Scheduled(cron = "${job.notification-cleanup.cron:30 0 3 * * *}")
    public void cleanupExpiredNotifications() {
        Instant before = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int removed = 0;
        for (int round = 0; round < MAX_BATCHES_PER_RUN; round++) {
            int affected;
            try {
                List<Long> expiredIds = mapper.findExpiredIds(before, batchSize);
                if (expiredIds.isEmpty()) {
                    break;
                }
                affected = mapper.deleteByIds(expiredIds);
            } catch (RuntimeException exception) {
                log.error("{} failed to delete expired notifications, stop this round", LOG_PREFIX, exception);
                break;
            }
            removed += affected;
            if (affected < batchSize) {
                break;
            }
        }
        log.info("{} finished, removedNotifications={}", LOG_PREFIX, removed);
    }
}
