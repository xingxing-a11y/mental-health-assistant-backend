package com.mindharbor.modules.risk.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.modules.diary.entity.Diary;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 连续负面日记判定：边界主要在两处 —— 「自然日」的时区，以及哪些情绪算负面。 */
class DiaryStreakDetectorTest {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    @Test
    @DisplayName("连续三天都是负面情绪 → 成立")
    void threeConsecutiveNegativeDays() {
        List<Diary> diaries = List.of(
                diary("2026-11-23T02:00:00Z", Diary.Emotion.LOW),
                diary("2026-11-22T02:00:00Z", Diary.Emotion.ANXIOUS),
                diary("2026-11-21T02:00:00Z", Diary.Emotion.LOW));

        assertTrue(DiaryStreakDetector.isNegativeStreak(diaries, APP_ZONE, TODAY, 3));
    }

    @Test
    @DisplayName("中间断了一天 → 不成立")
    void gapBreaksStreak() {
        List<Diary> diaries = List.of(
                diary("2026-11-23T02:00:00Z", Diary.Emotion.LOW),
                diary("2026-11-21T02:00:00Z", Diary.Emotion.LOW));

        assertFalse(DiaryStreakDetector.isNegativeStreak(diaries, APP_ZONE, TODAY, 3));
    }

    @Test
    @DisplayName("STRESSED 不算负面 → 三天里有一天是它就断")
    void stressedIsNotNegative() {
        List<Diary> diaries = List.of(
                diary("2026-11-23T02:00:00Z", Diary.Emotion.LOW),
                diary("2026-11-22T02:00:00Z", Diary.Emotion.STRESSED),
                diary("2026-11-21T02:00:00Z", Diary.Emotion.ANXIOUS));

        assertFalse(DiaryStreakDetector.isNegativeStreak(diaries, APP_ZONE, TODAY, 3));
    }

    @Test
    @DisplayName("CALM 显然不算负面")
    void calmIsNotNegative() {
        List<Diary> diaries = List.of(
                diary("2026-11-23T02:00:00Z", Diary.Emotion.CALM),
                diary("2026-11-22T02:00:00Z", Diary.Emotion.ANXIOUS),
                diary("2026-11-21T02:00:00Z", Diary.Emotion.ANXIOUS));

        assertFalse(DiaryStreakDetector.isNegativeStreak(diaries, APP_ZONE, TODAY, 3));
    }

    @Test
    @DisplayName("同一天写了多条，只要有一条负面就算当天命中")
    void sameDayRecordsAreFolded() {
        List<Diary> diaries = List.of(
                diary("2026-11-23T02:00:00Z", Diary.Emotion.CALM),
                diary("2026-11-23T10:00:00Z", Diary.Emotion.LOW),
                diary("2026-11-22T02:00:00Z", Diary.Emotion.ANXIOUS),
                diary("2026-11-21T02:00:00Z", Diary.Emotion.ANXIOUS));

        assertTrue(DiaryStreakDetector.isNegativeStreak(diaries, APP_ZONE, TODAY, 3));
    }

    @Test
    @DisplayName("时区边界：UTC 16:30 属于北京时间次日 00:30，应算今天")
    void utcEveningCountsAsNextDayInShanghai() {
        List<Diary> diaries = List.of(
                // 北京时间 2026-11-23 00:30
                diary("2026-11-22T16:30:00Z", Diary.Emotion.LOW));

        assertTrue(DiaryStreakDetector.isNegativeStreak(diaries, APP_ZONE, TODAY, 1));
    }

    @Test
    @DisplayName("时区边界：UTC 15:30 属于北京时间当日 23:30，应算前一天")
    void utcAfternoonStaysPreviousDayInShanghai() {
        List<Diary> diaries = List.of(
                // 北京时间 2026-11-22 23:30
                diary("2026-11-22T15:30:00Z", Diary.Emotion.LOW));

        assertFalse(DiaryStreakDetector.isNegativeStreak(diaries, APP_ZONE, TODAY, 1),
                "这条日记属于 22 号，不该满足「今天有负面」");
    }

    @Test
    @DisplayName("今天没写日记 → 不成立（不拿历史数据凑数）")
    void missingTodayIsNotAStreak() {
        List<Diary> diaries = List.of(
                diary("2026-11-22T02:00:00Z", Diary.Emotion.LOW),
                diary("2026-11-21T02:00:00Z", Diary.Emotion.LOW),
                diary("2026-11-20T02:00:00Z", Diary.Emotion.LOW));

        assertFalse(DiaryStreakDetector.isNegativeStreak(diaries, APP_ZONE, TODAY, 3),
                "连续区间必须以今天结尾，历史三天连续不算当前告警");
    }

    @Test
    @DisplayName("requiredDays 小于 1 属于非法参数，直接不成立")
    void invalidRequiredDays() {
        List<Diary> diaries = List.of(diary("2026-11-23T02:00:00Z", Diary.Emotion.LOW));

        assertFalse(DiaryStreakDetector.isNegativeStreak(diaries, APP_ZONE, TODAY, 0));
        assertFalse(DiaryStreakDetector.isNegativeStreak(diaries, APP_ZONE, TODAY, -1));
    }

    @Test
    @DisplayName("空列表 / null 都不能抛异常")
    void nullAndEmptyAreSafe() {
        assertFalse(DiaryStreakDetector.isNegativeStreak(List.of(), APP_ZONE, TODAY, 3));
        assertFalse(DiaryStreakDetector.isNegativeStreak(null, APP_ZONE, TODAY, 3));
    }

    @Test
    @DisplayName("缺失 createdAt 或 emotion 的脏数据不能误判成命中")
    void dirtyRowsAreIgnored() {
        List<Diary> diaries = new ArrayList<>();
        Diary noTime = new Diary();
        noTime.setEmotion(Diary.Emotion.LOW);
        noTime.setCreatedAt(null);
        diaries.add(noTime);

        Diary noEmotion = new Diary();
        noEmotion.setCreatedAt(Instant.parse("2026-11-23T02:00:00Z"));
        noEmotion.setEmotion(null);
        diaries.add(noEmotion);

        assertFalse(DiaryStreakDetector.isNegativeStreak(diaries, APP_ZONE, TODAY, 1));
    }

    private static Diary diary(String instant, Diary.Emotion emotion) {
        Diary diary = new Diary();
        diary.setUserId(1L);
        diary.setCreatedAt(Instant.parse(instant));
        diary.setEmotion(emotion);
        return diary;
    }
}
