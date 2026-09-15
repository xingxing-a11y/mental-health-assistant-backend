package com.mindharbor.modules.diary.service.impl;

import com.mindharbor.modules.diary.dto.DiaryRequest;
import com.mindharbor.modules.diary.entity.Diary;
import com.mindharbor.modules.diary.mapper.DiaryMapper;
import com.mindharbor.modules.diary.service.DiaryService;
import com.mindharbor.modules.chat.service.AiConversationService;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.security.CurrentUserProvider;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DiaryServiceImpl implements DiaryService {
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
    public List<?> list() {
        return mapper.findByUserIdOrderByCreatedAtDesc(currentUser.getUserId());
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
        diary.setUser(user);
        diary.setContent(request.getContent());
        diary.setEmotion(request.getEmotion());
        mapper.insert(diary);
        return diary;
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
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
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        return mapper.findByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .filter(diary -> {
                    LocalDate date = diary.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
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
