package com.mindharbor.modules.risk.service;

import com.mindharbor.modules.diary.entity.Diary;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 连续负面日记判定。
 *
 * <p>纯函数：时区与「自然日」口径在这里定死，不依赖数据库会话时区，便于单测覆盖边界。
 */
public final class DiaryStreakDetector {

    /** 强负面情绪；STRESSED（有压力）不算高危，避免误报。 */
    public static final Set<Diary.Emotion> NEGATIVE_EMOTIONS =
            Set.of(Diary.Emotion.ANXIOUS, Diary.Emotion.LOW);

    private DiaryStreakDetector() {
    }

    /**
     * 判断最近 {@code requiredDays} 个自然日（含 {@code today}）是否每天都有负面日记。
     *
     * @param diaries      该用户的日记，可含任意时间段，方法内部自行分组
     * @param zone         判定自然日所用的时区，固定 Asia/Shanghai
     * @param today        今天
     * @param requiredDays 需要的连续天数，小于 1 时返回 false
     */
    public static boolean isNegativeStreak(
            List<Diary> diaries, ZoneId zone, LocalDate today, int requiredDays) {
        if (diaries == null || diaries.isEmpty() || zone == null || today == null || requiredDays < 1) {
            return false;
        }
        Set<LocalDate> negativeDays = new HashSet<>();
        for (Diary diary : diaries) {
            if (diary == null || diary.getCreatedAt() == null || diary.getEmotion() == null) {
                continue;   // 脏数据不参与判定，也不抛异常
            }
            if (!NEGATIVE_EMOTIONS.contains(diary.getEmotion())) {
                continue;
            }
            negativeDays.add(diary.getCreatedAt().atZone(zone).toLocalDate());
        }
        // 连续区间必须以今天结尾：历史三天连续但今天没写，不算当前告警。
        for (int offset = 0; offset < requiredDays; offset++) {
            if (!negativeDays.contains(today.minusDays(offset))) {
                return false;
            }
        }
        return true;
    }
}
