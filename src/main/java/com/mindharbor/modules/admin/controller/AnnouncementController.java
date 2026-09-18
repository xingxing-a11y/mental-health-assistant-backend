package com.mindharbor.modules.admin.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.admin.service.AnnouncementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开公告查询接口，供普通用户首页读取，无需登录。 */
@RestController
public class AnnouncementController {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementController.class);

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/announcements")
    public ApiResponse<?> list() {
        log.info("List public announcements");
        return ApiResponse.ok(announcementService.list());
    }
}
