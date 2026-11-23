package com.mindharbor.modules.risk.job;

import com.mindharbor.modules.diary.entity.Diary;
import com.mindharbor.modules.diary.mapper.DiaryMapper;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.RiskAlertMapper;
import com.mindharbor.modules.risk.service.DiaryStreakDetector;
import com.mindharbor.modules.risk.service.RiskAlertService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 连续负面日记扫描。
 *
 * <p>每天凌晨扫一次"这段时间动过笔"的用户，判断最近 N 个自然日是否每天都记录了负面情绪。
 * 同一用户在同一冷却期内只告警一次——持续低落的人不该天天收到提醒，那会让人关掉通知。
 */
@Component
@ConditionalOnProperty(
        name = "risk.alert.diary.enabled", havingValue = "true", matchIfMissing = true)
public class DiaryNegativeScanJob {

    private static final Logger log = LoggerFactory.getLogger(DiaryNegativeScanJob.class);

    private static final String LOG_PREFIX = "[risk-diary]";

    /** 判定自然日用的时区，与统计消费者保持同一口径。 */
    static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    /** 每人最多取多少条日记参与判定：一天写 10 条已属极端，再多也覆盖得了 N 天。 */
    private static final int DAILY_RECORDS_UPPER_BOUND = 10;

    private final DiaryMapper diaryMapper;

    private final RiskAlertMapper riskAlertMapper;

    private final RiskAlertService riskAlertService;

    private final int negativeDays;

    private final int cooldownDays;

    private final int lookbackDays;

    public DiaryNegativeScanJob(
            DiaryMapper diaryMapper,
            RiskAlertMapper riskAlertMapper,
            RiskAlertService riskAlertService,
            @Value("${risk.alert.diary.negative-days:3}") int negativeDays,
            @Value("${risk.alert.diary.cooldown-days:7}") int cooldownDays,
            @Value("${risk.alert.diary.lookback-days:7}") int lookbackDays) {
        this.diaryMapper = diaryMapper;
        this.riskAlertMapper = riskAlertMapper;
        this.riskAlertService = riskAlertService;
        this.negativeDays = Math.max(negativeDays, 1);
        this.cooldownDays = Math.max(cooldownDays, 0);
        this.lookbackDays = Math.max(lookbackDays, this.negativeDays);
    }

    @Scheduled(cron = "${risk.alert.diary.cron:0 0 3 * * *}")
    public void scan() {
        LocalDate today = LocalDate.now(APP_ZONE);
        int alerted = scanFor(today);
        if (alerted > 0) {
            log.info("{} finished, alerted={}", LOG_PREFIX, alerted);
        }
    }

    /** 抽成可传入日期的方法，单测才能固定"今天"而不依赖真实时钟。 */
    int scanFor(LocalDate today) {
        Instant since = today.minusDays(lookbackDays).atStartOfDay(APP_ZONE).toInstant();
        List<Long> userIds;
        try {
            userIds = diaryMapper.findUserIdsWithDiarySince(since);
        } catch (RuntimeException exception) {
            log.warn("{} failed to load candidate users", LOG_PREFIX, exception);
            return 0;
        }
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }

        int alerted = 0;
        for (Long userId : userIds) {
            try {
                if (shouldAlert(userId, today)) {
                    riskAlertService.publishDiaryStreak(userId, today, negativeDays);
                    alerted++;
                }
            } catch (RuntimeException exception) {
                // 单个用户判定失败不能影响其余用户。
                log.warn("{} failed, userId={}", LOG_PREFIX, userId, exception);
            }
        }
        return alerted;
    }

    boolean shouldAlert(Long userId, LocalDate today) {
        List<Diary> diaries = diaryMapper.findRecentByUserId(
                userId, Math.max(negativeDays * DAILY_RECORDS_UPPER_BOUND, 30));
        if (!DiaryStreakDetector.isNegativeStreak(diaries, APP_ZONE, today, negativeDays)) {
            return false;
        }
        // 冷却期：同一用户在该窗口内已有同源告警时不再重复。
        Instant cooldownStart = today.minusDays(cooldownDays).atStartOfDay(APP_ZONE).toInstant();
        return riskAlertMapper.countRecentBySource(
                userId, RiskAlertEvent.Source.DIARY_STREAK, cooldownStart) == 0;
    }
}
