package com.mindharbor.modules.admin.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.admin.dto.AnnouncementRequest;
import com.mindharbor.modules.admin.entity.Announcement;
import com.mindharbor.modules.admin.service.AdminOperationsService;
import com.mindharbor.modules.admin.service.AnnouncementService;
import com.mindharbor.modules.admin.vo.HighRiskAuditVO;
import com.mindharbor.modules.admin.vo.StatisticsOverviewVO;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理后台运营接口，只负责参数接收与响应封装，业务逻辑统一放在 service 层。 */
@RestController
@RequestMapping("/admin")
public class AdminOperationsController {

    private static final Logger log = LoggerFactory.getLogger(AdminOperationsController.class);

    private final AdminOperationsService adminOperationsService;
    private final AnnouncementService announcementService;

    public AdminOperationsController(
            AdminOperationsService adminOperationsService,
            AnnouncementService announcementService) {
        this.adminOperationsService = adminOperationsService;
        this.announcementService = announcementService;
    }

    @GetMapping("/audits/high-risk")
    public ApiResponse<List<HighRiskAuditVO>> highRisk() {
        log.info("List high risk chat session audits");
        return ApiResponse.ok(adminOperationsService.highRiskAudits());
    }

    @GetMapping("/statistics/overview")
    public ApiResponse<StatisticsOverviewVO> statistics() {
        log.info("Load administrator statistics overview");
        return ApiResponse.ok(adminOperationsService.statisticsOverview());
    }

    @GetMapping("/announcements")
    public ApiResponse<List<Announcement>> listAnnouncements() {
        log.info("List announcements for administrator");
        return ApiResponse.ok(announcementService.list());
    }

    @PostMapping("/announcements")
    public ApiResponse<Announcement> createAnnouncement(
            @Valid @RequestBody AnnouncementRequest request) {
        log.info("Create announcement");
        return ApiResponse.ok(announcementService.create(request));
    }

    @PutMapping("/announcements/{announcementId}")
    public ApiResponse<Announcement> updateAnnouncement(
            @PathVariable Long announcementId,
            @Valid @RequestBody AnnouncementRequest request) {
        log.info("Update announcement, announcementId={}", announcementId);
        return ApiResponse.ok(announcementService.update(announcementId, request));
    }

    @DeleteMapping("/announcements/{announcementId}")
    public ApiResponse<Void> deleteAnnouncement(@PathVariable Long announcementId) {
        log.info("Delete announcement, announcementId={}", announcementId);
        announcementService.delete(announcementId);
        return ApiResponse.ok(null);
    }
}
