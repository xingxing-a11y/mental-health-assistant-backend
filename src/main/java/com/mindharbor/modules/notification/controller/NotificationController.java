package com.mindharbor.modules.notification.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.notification.service.NotificationService;
import com.mindharbor.modules.notification.vo.NotificationVO;
import com.mindharbor.modules.notification.vo.UnreadCountVO;
import com.mindharbor.modules.user.vo.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户侧通知接口，只返回当前用户可见且未过期的通知。 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<PageResult<NotificationVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("List notifications, page={}, size={}", page, size);
        return ApiResponse.ok(notificationService.list(page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountVO> unreadCount() {
        log.info("Count unread notifications");
        return ApiResponse.ok(new UnreadCountVO(notificationService.unreadCount()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable long id) {
        log.info("Mark notification read, notificationId={}", id);
        notificationService.markRead(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        log.info("Mark all notifications read");
        notificationService.markAllRead();
        return ApiResponse.ok(null);
    }
}
