package com.mindharbor.modules.admin.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.admin.dto.AnnouncementRequest;
import com.mindharbor.modules.admin.entity.Announcement;
import com.mindharbor.modules.admin.mapper.AnnouncementMapper;
import com.mindharbor.modules.chat.entity.ChatSession;
import com.mindharbor.modules.chat.mapper.ChatSessionMapper;
import com.mindharbor.modules.assessment.mapper.AssessmentResultMapper;
import com.mindharbor.modules.user.mapper.UserMapper;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class AdminOperationsController {
    private final ChatSessionMapper sessions;
    private final UserMapper users;
    private final AssessmentResultMapper results;
    private final AnnouncementMapper announcements;

    public AdminOperationsController(
            ChatSessionMapper sessions,
            UserMapper users,
            AssessmentResultMapper results,
            AnnouncementMapper announcements) {
        this.sessions = sessions;
        this.users = users;
        this.results = results;
        this.announcements = announcements;
    }

    @GetMapping("/audits/high-risk")
    public ApiResponse<?> highRisk() {
        return ApiResponse.ok(sessions.selectList(null).stream()
                .filter(ChatSession::isHighRisk)
                .map(session -> Map.of(
                        "sessionId", session.getId(),
                        "userId", session.getUserId(),
                        "createdAt", session.getLatestMessageAt(),
                        "summary", "该会话已触发高风险干预"))
                .toList());
    }

    @GetMapping("/statistics/overview")
    public ApiResponse<?> statistics() {
        return ApiResponse.ok(Map.of(
                "registeredUsers", users.selectCount(null),
                "assessmentCompletions", results.selectCount(null),
                "chatSessions", sessions.selectCount(null)));
    }

    @GetMapping("/announcements")
    public ApiResponse<?> listAnnouncements() {
        return ApiResponse.ok(announcements.findAllByOrderByCreatedAtDesc());
    }

    @PostMapping("/announcements")
    public ApiResponse<?> createAnnouncement(@Valid @RequestBody AnnouncementRequest request) {
        Announcement announcement = new Announcement();
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcements.insert(announcement);
        return ApiResponse.ok(announcement);
    }

    @PutMapping("/announcements/{id}")
    public ApiResponse<?> updateAnnouncement(
            @PathVariable Long id,
            @Valid @RequestBody AnnouncementRequest request) {
        Announcement announcement = announcements.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("公告不存在"));
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcements.updateById(announcement);
        return ApiResponse.ok(announcement);
    }

    @DeleteMapping("/announcements/{id}")
    public ApiResponse<?> deleteAnnouncement(@PathVariable Long id) {
        announcements.deleteById(id);
        return ApiResponse.ok(null);
    }

}
