package com.mindharbor.modules.diary.service.impl;

import com.mindharbor.modules.chat.service.AiConversationService;
import com.mindharbor.modules.diary.dto.DiaryRequest;
import com.mindharbor.modules.diary.entity.Diary;
import com.mindharbor.modules.diary.mapper.DiaryMapper;
import com.mindharbor.modules.diary.service.DiaryService;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.modules.user.vo.PageResult;
import com.mindharbor.security.CurrentUserProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DiaryServiceImpl implements DiaryService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_PAGE_SIZE = 50;

    private final DiaryMapper mapper;
    private final UserMapper users;
    private final CurrentUserProvider currentUser;
    private final AiConversationService aiService;

    public DiaryServiceImpl(
            DiaryMapper mapper,
            UserMapper users,
            CurrentUserProvider currentUser,
            AiConversationService aiService) {
        this.mapper = mapper;
        this.users = users;
        this.currentUser = currentUser;
        this.aiService = aiService;
    }

    @Override
    public List<?> listRecent() {
        return mapper.findRecentByUserId(currentUser.getUserId(), 2);
    }

    @Override
    public PageResult<?> listAll(int page, int size, LocalDate startDate, LocalDate endDate) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        LocalDateTime start = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime end = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        Long userId = currentUser.getUserId();

        long total = mapper.countByUserId(userId, start, end);
        List<Diary> items = total == 0
                ? List.of()
                : mapper.findPageByUserId(userId, (long) safePage * safeSize, safeSize, start, end);
        int totalPages = (int) ((total + safeSize - 1) / safeSize);

        return new PageResult<>(items, safePage, safeSize, total, totalPages);
    }

    @Override
    public Object get(Long id) {
        return owned(id);
    }

    @Override
    public Object create(DiaryRequest request) {
        User user = users.findById(currentUser.getUserId())
                .orElseThrow(() -> new IllegalStateException("当前没有可用用户"));
        Diary diary = new Diary();
        diary.setUserId(user.getId());
        diary.setContent(request.getContent());
        diary.setEmotion(request.getEmotion());
        mapper.insert(diary);
        return diary;
    }

    @Override
    public Object update(Long id, DiaryRequest request) {
        Diary diary = owned(id);
        diary.setContent(request.getContent());
        diary.setEmotion(request.getEmotion());
        mapper.updateById(diary);
        return diary;
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(owned(id).getId());
    }

    @Override
    public String analyze(Long id) {
        Diary diary = owned(id);
        return aiService.generate(
                "请基于以下情绪日记给出简短、温和的自我调节建议，不做医学诊断：\n"
                        + diary.getContent(),
                "情绪标签：" + diary.getEmotion());
    }

    @Override
    public List<?> trend(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(APP_ZONE);
        LocalDate start = startDate == null ? today.minusDays(30) : startDate;
        LocalDate end = endDate == null ? today : endDate;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        return mapper.findByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .filter(diary -> {
                    LocalDate date = diary.getCreatedAt().atZone(APP_ZONE).toLocalDate();
                    return !date.isBefore(start) && !date.isAfter(end);
                })
                .map(diary -> Map.of(
                        "date", diary.getCreatedAt(),
                        "emotion", diary.getEmotion().name()))
                .toList();
    }

    private Diary owned(Long id) {
        Diary diary = mapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("日记不存在"));
        if (!currentUser.getUserId().equals(diary.getUserId())) {
            throw new SecurityException("无权访问该日记");
        }
        return diary;
    }
}
