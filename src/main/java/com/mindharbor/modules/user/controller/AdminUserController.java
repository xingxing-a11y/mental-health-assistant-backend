package com.mindharbor.modules.user.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.user.dto.UserStatusRequest;
import com.mindharbor.modules.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

/** 管理员用户管理接口，生产环境需要额外接入 ADMIN 权限拦截器。 */
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final UserService service;

    public AdminUserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("List users for administrator");
        return ApiResponse.ok(service.listUsers(page, size));
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<?> updateStatus(
            @PathVariable Long userId,
            @RequestBody UserStatusRequest request) {
        log.info("Update user status, userId={}", userId);
        return ApiResponse.ok(service.updateStatus(userId, request));
    }
}
