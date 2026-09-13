package com.mindharbor.modules.admin.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.admin.mapper.AnnouncementMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnnouncementController {
    private final AnnouncementMapper announcements;

    public AnnouncementController(AnnouncementMapper announcements) {
        this.announcements = announcements;
    }

    @GetMapping("/announcements")
    public ApiResponse<?> list() {
        return ApiResponse.ok(announcements.findAllByOrderByCreatedAtDesc());
    }
}
