package com.mindharbor.modules.relax.service.impl;

import com.mindharbor.modules.relax.dto.RelaxRecordRequest;
import com.mindharbor.modules.relax.dto.StressLogRequest;
import com.mindharbor.modules.relax.entity.RelaxRecord;
import com.mindharbor.modules.relax.entity.StressLog;
import com.mindharbor.modules.relax.mapper.RelaxRecordMapper;
import com.mindharbor.modules.relax.mapper.StressLogMapper;
import com.mindharbor.modules.relax.service.RelaxService;
import com.mindharbor.modules.relax.vo.RelaxMeditationVO;
import com.mindharbor.modules.relax.vo.RelaxOverviewVO;
import com.mindharbor.modules.relax.vo.RelaxRecordVO;
import com.mindharbor.modules.relax.vo.StressLogVO;
import com.mindharbor.modules.relax.vo.StressTrendPointVO;
import com.mindharbor.modules.user.vo.PageResult;
import com.mindharbor.security.CurrentUserProvider;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

/** 放松小工具业务实现：分页与统计口径都在 service 决定，mapper 只做数据访问。 */
@Service
public class RelaxServiceImpl implements RelaxService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 50;

    private static final int DEFAULT_TREND_DAYS = 30;

    private static final int MAX_TREND_DAYS = 90;

    private static final long WEEK_DAYS = 7L;

    private static final List<RelaxMeditationVO> MEDITATIONS = List.of(
            new RelaxMeditationVO(
                    "breath-3",
                    "3 分钟呼吸安定",
                    3,
                    "适合课间或工作间隙的短练习，快速回到当下。",
                    List.of(
                            "请找一个可以坐稳的位置，让肩膀自然下沉。",
                            "用鼻子慢慢吸气四秒，感受腹部轻轻鼓起。",
                            "屏住呼吸七秒，不需要用力，只是安静地停留。",
                            "用嘴巴缓缓呼气八秒，把一天的紧绷一起送出去。",
                            "重复三轮，最后自然呼吸，感受此刻的身体。")),
            new RelaxMeditationVO(
                    "body-5",
                    "5 分钟身体扫描",
                    5,
                    "从头顶到脚趾的放松扫描，缓解躯体化紧张。",
                    List.of(
                            "闭上眼睛，把注意力放在头顶，感受那里的状态。",
                            "注意力缓慢移动到额头、眼睛、下颌，让它们一点点松开。",
                            "继续向下到肩膀与手臂，允许它们变沉、变暖。",
                            "把注意力放到胸口与腹部，随着呼吸起伏，不评判任何感受。",
                            "最后感受双腿与脚掌接触地面，慢慢睁开眼睛。")),
            new RelaxMeditationVO(
                    "emotion-10",
                    "10 分钟情绪安放",
                    10,
                    "适合睡前或情绪起伏较大时，练习与情绪共处。",
                    List.of(
                            "先做三次深长的呼吸，把注意力从外界收回身体。",
                            "回想今天最明显的情绪，给它起一个名字，比如焦虑、疲惫或委屈。",
                            "不急着赶走它，只是允许它在那里，像看云一样看着它。",
                            "把注意力放在呼吸上，一次一次地回来，情绪会自己变化。",
                            "感受身体与座椅、床铺的接触，让重量被支撑住。",
                            "最后告诉自己：此刻我已经做了能做的那一部分。")));

    private final RelaxRecordMapper relaxRecordMapper;
    private final StressLogMapper stressLogMapper;
    private final CurrentUserProvider currentUserProvider;

    public RelaxServiceImpl(
            RelaxRecordMapper relaxRecordMapper,
            StressLogMapper stressLogMapper,
            CurrentUserProvider currentUserProvider) {
        this.relaxRecordMapper = relaxRecordMapper;
        this.stressLogMapper = stressLogMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public RelaxOverviewVO overview() {
        Long userId = currentUserProvider.getUserId();
        Instant weekStart = Instant.now().minus(WEEK_DAYS, ChronoUnit.DAYS);
        StressLog latest = stressLogMapper.findLatestByUserId(userId).orElse(null);
        return new RelaxOverviewVO(
                relaxRecordMapper.countSince(userId, weekStart),
                stressLogMapper.averageLevelSince(userId, weekStart),
                relaxRecordMapper.countByUserId(userId, null),
                latest == null ? null : latest.getLevel(),
                latest == null ? null : latest.getCreatedAt());
    }

    @Override
    public List<RelaxMeditationVO> meditations() {
        return MEDITATIONS;
    }

    @Override
    public PageResult<RelaxRecordVO> records(int page, int size, String type) {
        Long userId = currentUserProvider.getUserId();
        String normalizedType = normalizeType(type);
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        long total = relaxRecordMapper.countByUserId(userId, normalizedType);
        List<RelaxRecordVO> items = relaxRecordMapper
                .findByUserId(userId, normalizedType, safeSize, safePage * safeSize)
                .stream()
                .map(record -> new RelaxRecordVO(
                        record.getId(),
                        record.getType().name(),
                        record.getDurationSeconds(),
                        record.getRounds(),
                        record.getCreatedAt()))
                .toList();
        return new PageResult<>(items, safePage, safeSize, total, totalPages(total, safeSize));
    }

    @Override
    public RelaxRecordVO createRecord(RelaxRecordRequest request) {
        RelaxRecord record = new RelaxRecord();
        record.setUserId(currentUserProvider.getUserId());
        record.setType(request.getType());
        record.setDurationSeconds(request.getDurationSeconds());
        record.setRounds(request.getRounds());
        relaxRecordMapper.insert(record);
        return new RelaxRecordVO(
                record.getId(),
                record.getType().name(),
                record.getDurationSeconds(),
                record.getRounds(),
                record.getCreatedAt());
    }

    @Override
    public PageResult<StressLogVO> stressLogs(int page, int size) {
        Long userId = currentUserProvider.getUserId();
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        long total = stressLogMapper.countByUserId(userId);
        List<StressLogVO> items = stressLogMapper
                .findByUserId(userId, safeSize, safePage * safeSize)
                .stream()
                .map(log -> new StressLogVO(
                        log.getId(),
                        log.getLevel(),
                        log.getNote(),
                        log.getCreatedAt()))
                .toList();
        return new PageResult<>(items, safePage, safeSize, total, totalPages(total, safeSize));
    }

    @Override
    public StressLogVO createStressLog(StressLogRequest request) {
        StressLog log = new StressLog();
        log.setUserId(currentUserProvider.getUserId());
        log.setLevel(request.getLevel());
        log.setNote(normalizeNote(request.getNote()));
        stressLogMapper.insert(log);
        return new StressLogVO(log.getId(), log.getLevel(), log.getNote(), log.getCreatedAt());
    }

    @Override
    public List<StressTrendPointVO> stressTrend(int days) {
        int safeDays = days <= 0 ? DEFAULT_TREND_DAYS : Math.min(days, MAX_TREND_DAYS);
        Instant since = Instant.now().minus(safeDays, ChronoUnit.DAYS);
        return stressLogMapper.findTrendSince(currentUserProvider.getUserId(), since);
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return RelaxRecord.Type.valueOf(type.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("练习类型只能是 BREATHE 或 MEDITATION");
        }
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private int totalPages(long total, int size) {
        return (int) ((total + size - 1) / size);
    }
}
