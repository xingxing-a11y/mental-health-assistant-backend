package com.mindharbor.modules.chat.job;

import com.mindharbor.modules.chat.mapper.ChatMessageMapper;
import com.mindharbor.modules.chat.mapper.ChatSessionMapper;
import com.mindharbor.modules.user.entity.UserSettings;
import com.mindharbor.modules.user.mapper.UserSettingsMapper;
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
 * 会话消息清理任务：只清理选择「30 天后自动清理」的用户，分批删除到期消息与空会话。
 * 与通知清理任务完全独立，互不影响。
 */
@Component
@ConditionalOnProperty(name = "job.session-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class SessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupJob.class);

    /** 日志前缀便于区分两个清理任务的来源。 */
    private static final String LOG_PREFIX = "[session-cleanup]";

    /** 单个用户单轮最多清理的批次数，避免异常数据让任务长期占用调度线程。 */
    private static final int MAX_BATCHES_PER_USER = 50;

    private final UserSettingsMapper userSettingsMapper;

    private final ChatMessageMapper chatMessageMapper;

    private final ChatSessionMapper chatSessionMapper;

    private final long retentionDays;

    private final int batchSize;

    public SessionCleanupJob(
            UserSettingsMapper userSettingsMapper,
            ChatMessageMapper chatMessageMapper,
            ChatSessionMapper chatSessionMapper,
            @Value("${job.session-cleanup.retention-days:30}") long retentionDays,
            @Value("${job.session-cleanup.batch-size:200}") int batchSize) {
        this.userSettingsMapper = userSettingsMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.retentionDays = retentionDays;
        this.batchSize = Math.max(batchSize, 1);
    }

    @Scheduled(cron = "${job.session-cleanup.cron:0 0 3 * * *}")
    public void cleanupExpiredMessages() {
        Instant before = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<Long> userIds = loadCandidateUserIds();
        if (userIds.isEmpty()) {
            log.info("{} finished, no user enabled automatic cleanup", LOG_PREFIX);
            return;
        }

        int removedMessages = 0;
        int removedSessions = 0;
        for (Long userId : userIds) {
            try {
                removedMessages += purgeMessages(userId, before);
            } catch (RuntimeException exception) {
                // 单个用户的消息清理失败不影响同一用户的空会话清理
                log.error("{} failed to clean chat messages, continue with session cleanup", LOG_PREFIX, exception);
            }
            try {
                removedSessions += purgeEmptySessions(userId, before);
            } catch (RuntimeException exception) {
                log.error("{} failed to clean empty sessions, continue with next user", LOG_PREFIX, exception);
            }
        }
        log.info(
                "{} finished, users={}, removedMessages={}, removedSessions={}",
                LOG_PREFIX,
                userIds.size(),
                removedMessages,
                removedSessions);
    }

    private List<Long> loadCandidateUserIds() {
        try {
            return userSettingsMapper.findUserIdsByMessageRetention(
                    UserSettings.MessageRetention.THIRTY_DAYS.name());
        } catch (RuntimeException exception) {
            log.error("{} failed to load candidate users, task skipped for this round", LOG_PREFIX, exception);
            return List.of();
        }
    }

    private int purgeMessages(Long userId, Instant before) {
        int removed = 0;
        for (int round = 0; round < MAX_BATCHES_PER_USER; round++) {
            int affected = chatMessageMapper.deleteExpiredBatch(userId, before, batchSize);
            removed += affected;
            if (affected < batchSize) {
                break;
            }
        }
        return removed;
    }

    private int purgeEmptySessions(Long userId, Instant before) {
        int removed = 0;
        for (int round = 0; round < MAX_BATCHES_PER_USER; round++) {
            int affected = chatSessionMapper.deleteEmptyExpiredBatch(userId, before, batchSize);
            removed += affected;
            if (affected < batchSize) {
                break;
            }
        }
        return removed;
    }
}
