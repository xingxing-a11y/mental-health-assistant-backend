package com.mindharbor.modules.diary.service.impl;

import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.modules.chat.service.AiConversationService;
import com.mindharbor.modules.diary.dto.DiaryRequest;
import com.mindharbor.modules.diary.entity.Diary;
import com.mindharbor.modules.diary.mapper.DiaryMapper;
import com.mindharbor.modules.diary.service.DiaryService;
import com.mindharbor.modules.diary.vo.DiaryVO;
import com.mindharbor.modules.diary.vo.EmotionTrendPointVO;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.modules.user.vo.PageResult;
import com.mindharbor.security.CurrentUserProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DiaryServiceImpl implements DiaryService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_PAGE_SIZE = 50;

    private final DiaryMapper diaryMapper;
    private final UserMapper userMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AiConversationService aiConversationService;

    public DiaryServiceImpl(
            DiaryMapper diaryMapper,
            UserMapper userMapper,
            CurrentUserProvider currentUserProvider,
            AiConversationService aiConversationService) {
        this.diaryMapper = diaryMapper;
        this.userMapper = userMapper;
        this.currentUserProvider = currentUserProvider;
        this.aiConversationService = aiConversationService;
    }

    @Override
    public List<DiaryVO> listRecent() {
        return diaryMapper.findRecentByUserId(currentUserProvider.getUserId(), 2)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public PageResult<DiaryVO> listAll(int page, int size, LocalDate startDate, LocalDate endDate) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        LocalDateTime start = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime end = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        Long userId = currentUserProvider.getUserId();

        long total = diaryMapper.countByUserId(userId, start, end);
        List<Diary> items = total == 0
                ? List.of()
                : diaryMapper.findPageByUserId(userId, (long) safePage * safeSize, safeSize, start, end);
        int totalPages = (int) ((total + safeSize - 1) / safeSize);

        return new PageResult<>(
                items.stream().map(this::toView).toList(),
                safePage,
                safeSize,
                total,
                totalPages);
    }

    @Override
    public DiaryVO get(Long id) {
        return toView(owned(id));
    }

    @Override
    public DiaryVO create(DiaryRequest request) {
        User user = userMapper.findById(currentUserProvider.getUserId())
                .orElseThrow(() -> new IllegalStateException("当前没有可用用户"));
        Diary diary = new Diary();
        diary.setUserId(user.getId());
        diary.setContent(request.getContent());
        diary.setEmotion(request.getEmotion());
        diaryMapper.insert(diary);
        return toView(diary);
    }

    @Override
    public DiaryVO update(Long id, DiaryRequest request) {
        Diary diary = owned(id);
        diary.setContent(request.getContent());
        diary.setEmotion(request.getEmotion());
        diaryMapper.updateById(diary);
        return toView(diary);
    }

    @Override
    public void delete(Long id) {
        diaryMapper.deleteById(owned(id).getId());
    }

    @Override
    public String analyze(Long id) {
        Diary diary = owned(id);
        return aiConversationService.generate(
                "请基于以下情绪日记给出简短、温和的自我调节建议，不做医学诊断：\n"
                        + diary.getContent(),
                "情绪标签：" + diary.getEmotion());
    }

    @Override
    public List<EmotionTrendPointVO> trend(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(APP_ZONE);
        LocalDate start = startDate == null ? today.minusDays(30) : startDate;
        LocalDate end = endDate == null ? today : endDate;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        return diaryMapper.findByUserIdOrderByCreatedAtDesc(currentUserProvider.getUserId()).stream()
                .filter(diary -> {
                    LocalDate date = diary.getCreatedAt().atZone(APP_ZONE).toLocalDate();
                    return !date.isBefore(start) && !date.isAfter(end);
                })
                .map(this::toTrendPoint)
                .toList();
    }

    private Diary owned(Long id) {
        Diary diary = diaryMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("日记不存在"));
        if (!currentUserProvider.getUserId().equals(diary.getUserId())) {
            throw new SecurityException("无权访问该日记");
        }
        return diary;
    }

    private DiaryVO toView(Diary diary) {
        DiaryVO view = new DiaryVO();
        view.setId(diary.getId());
        view.setUserId(diary.getUserId());
        view.setContent(diary.getContent());
        view.setEmotion(emotionName(diary));
        view.setCreatedAt(diary.getCreatedAt());
        return view;
    }

    private EmotionTrendPointVO toTrendPoint(Diary diary) {
        EmotionTrendPointVO point = new EmotionTrendPointVO();
        point.setDate(diary.getCreatedAt());
        point.setEmotion(emotionName(diary));
        return point;
    }

    private String emotionName(Diary diary) {
        return diary.getEmotion() == null ? null : diary.getEmotion().name();
    }
}
