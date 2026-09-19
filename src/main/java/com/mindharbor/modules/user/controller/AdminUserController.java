package com.mindharbor.modules.user.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.user.dto.UserStatusRequest;
import com.mindharbor.modules.user.service.UserService;
import com.mindharbor.modules.user.vo.PageResult;
import com.mindharbor.modules.user.vo.UserVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员用户管理接口，ADMIN 角色由 JWT 拦截器强制校验。 */
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final UserService service;

    public AdminUserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<UserVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String nickname) {
        log.info("List users for administrator");
        return ApiResponse.ok(service.listUsers(page, size, nickname));
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<UserVO> updateStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UserStatusRequest request) {
        log.info("Update user status, userId={}", userId);
        return ApiResponse.ok(service.updateStatus(userId, request));
    }
}
