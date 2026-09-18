package com.mindharbor.modules.notification.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.notification.dto.NotificationPushRequest;
import com.mindharbor.modules.notification.service.NotificationService;
import com.mindharbor.modules.notification.vo.NotificationVO;
import com.mindharbor.modules.user.vo.PageResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员通知推送接口。 */
@RestController
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationController.class);

    private final NotificationService service;

    public AdminNotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<NotificationVO> push(@Valid @RequestBody NotificationPushRequest request) {
        log.info("Push notification, scope={}", request.getScope());
        return ApiResponse.ok(service.push(request));
    }

    @GetMapping
    public ApiResponse<PageResult<NotificationVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("List pushed notifications, page={}, size={}", page, size);
        return ApiResponse.ok(service.adminList(page, size));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> revoke(@PathVariable long id) {
        log.info("Revoke notification, notificationId={}", id);
        service.revoke(id);
        return ApiResponse.ok(null);
    }
}
