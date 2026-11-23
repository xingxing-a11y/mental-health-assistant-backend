package com.mindharbor.modules.risk.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.modules.diary.entity.Diary;
import com.mindharbor.modules.diary.mapper.DiaryMapper;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.RiskAlertMapper;
import com.mindharbor.modules.risk.service.RiskAlertService;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 连续负面日记扫描：判定、冷却、单用户失败隔离。 */
class DiaryNegativeScanJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    private static final int NEGATIVE_DAYS = 3;

    private static final int COOLDOWN_DAYS = 7;

    private final List<String> published = new ArrayList<>();

    private final Map<Long, List<Diary>> diariesByUser = new HashMap<>();

    private final Set<Long> coolingDownUsers = new HashSet<>();

    private final Set<Long> failingUsers = new HashSet<>();

    @Test
    @DisplayName("连续三天负面且不在冷却期：投递一次")
    void alertsConsecutiveNegativeUser() {
        diariesByUser.put(1L, threeNegativeDays());

        jobWith(List.of(1L)).scanFor(TODAY);

        assertEquals(List.of("diary:1:20261123"), published);
    }

    @Test
    @DisplayName("冷却期内已有同源告警：跳过，避免持续低落的人被天天提醒")
    void skipsUserInCooldown() {
        diariesByUser.put(1L, threeNegativeDays());
        coolingDownUsers.add(1L);

        jobWith(List.of(1L)).scanFor(TODAY);

        assertEquals(0, published.size());
    }

    @Test
    @DisplayName("中间断了一天：不告警")
    void gapBreaksStreak() {
        diariesByUser.put(1L, List.of(
                diary("2026-11-23T02:00:00Z", Diary.Emotion.LOW),
                diary("2026-11-21T02:00:00Z", Diary.Emotion.ANXIOUS)));

        jobWith(List.of(1L)).scanFor(TODAY);

        assertEquals(0, published.size());
    }

    @Test
    @DisplayName("只有 CARE 级情绪（有压力）不算负面")
    void stressedIsNotNegative() {
        diariesByUser.put(1L, List.of(
                diary("2026-11-23T02:00:00Z", Diary.Emotion.LOW),
                diary("2026-11-22T02:00:00Z", Diary.Emotion.STRESSED),
                diary("2026-11-21T02:00:00Z", Diary.Emotion.LOW)));

        jobWith(List.of(1L)).scanFor(TODAY);

        assertEquals(0, published.size());
    }

    @Test
    @DisplayName("单个用户判定失败不影响其他人")
    void singleFailureDoesNotStopOthers() {
        diariesByUser.put(1L, threeNegativeDays());
        diariesByUser.put(2L, threeNegativeDays());
        failingUsers.add(1L);

        jobWith(List.of(1L, 2L)).scanFor(TODAY);

        assertEquals(List.of("diary:2:20261123"), published);
    }

    @Test
    @DisplayName("没有候选人时不做任何事")
    void emptyCandidates() {
        jobWith(List.of()).scanFor(TODAY);

        assertEquals(0, published.size());
    }

    @Test
    @DisplayName("判定只取最近若干条，不会把整本日记读进内存")
    void readsBoundedRecordCount() {
        diariesByUser.put(1L, threeNegativeDays());
        List<Integer> limits = new ArrayList<>();

        new DiaryNegativeScanJob(
                fakeDiaryMapper(limits), fakeAlertMapper(), fakeService(),
                NEGATIVE_DAYS, COOLDOWN_DAYS, 7)
                .scanFor(TODAY);

        assertTrue(limits.get(0) >= NEGATIVE_DAYS * 3,
                "取数上限要能覆盖 N 天，实际=" + limits.get(0));
        assertTrue(limits.get(0) <= 100, "也不该一次拉回上百条，实际=" + limits.get(0));
    }

    private List<Diary> threeNegativeDays() {
        return List.of(
                diary("2026-11-23T02:00:00Z", Diary.Emotion.LOW),
                diary("2026-11-22T02:00:00Z", Diary.Emotion.ANXIOUS),
                diary("2026-11-21T02:00:00Z", Diary.Emotion.LOW));
    }

    private DiaryNegativeScanJob jobWith(List<Long> userIds) {
        return new DiaryNegativeScanJob(
                fakeDiaryMapper(new ArrayList<>(), userIds),
                fakeAlertMapper(),
                fakeService(),
                NEGATIVE_DAYS,
                COOLDOWN_DAYS,
                7);
    }

    private static Diary diary(String instant, Diary.Emotion emotion) {
        Diary diary = new Diary();
        diary.setUserId(1L);
        diary.setCreatedAt(Instant.parse(instant));
        diary.setEmotion(emotion);
        return diary;
    }

    private DiaryMapper fakeDiaryMapper(List<Integer> limits) {
        return fakeDiaryMapper(limits, List.of(1L));
    }

    private DiaryMapper fakeDiaryMapper(List<Integer> limits, List<Long> userIds) {
        return (DiaryMapper) Proxy.newProxyInstance(
                DiaryMapper.class.getClassLoader(),
                new Class<?>[] {DiaryMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    return switch (method.getName()) {
                        case "findUserIdsWithDiarySince" -> userIds;
                        case "findRecentByUserId" -> {
                            limits.add((Integer) args[1]);
                            yield diariesByUser.getOrDefault((Long) args[0], List.of());
                        }
                        default -> null;
                    };
                });
    }

    private RiskAlertMapper fakeAlertMapper() {
        return (RiskAlertMapper) Proxy.newProxyInstance(
                RiskAlertMapper.class.getClassLoader(),
                new Class<?>[] {RiskAlertMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("countRecentBySource".equals(method.getName())) {
                        return coolingDownUsers.contains((Long) args[0]) ? 1L : 0L;
                    }
                    return null;
                });
    }

    private RiskAlertService fakeService() {
        return (RiskAlertService) Proxy.newProxyInstance(
                RiskAlertService.class.getClassLoader(),
                new Class<?>[] {RiskAlertService.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("publishDiaryStreak".equals(method.getName())) {
                        Long userId = (Long) args[0];
                        if (failingUsers.contains(userId)) {
                            throw new IllegalStateException("db down");
                        }
                        published.add("diary:" + userId + ":" + args[1].toString().replace("-", ""));
                        return true;
                    }
                    return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                });
    }
}
