package com.mindharbor.modules.user.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.user.dto.UserSettingsRequest;
import com.mindharbor.modules.user.service.UserSettingsService;
import com.mindharbor.modules.user.vo.UserSettingsVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 个人偏好设置接口，只返回当前登录用户自己的设置。 */
@RestController
@RequestMapping("/users/me/settings")
public class UserSettingsController {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsController.class);

    private final UserSettingsService userSettingsService;

    public UserSettingsController(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    @GetMapping
    public ApiResponse<UserSettingsVO> current() {
        log.info("Load current user settings");
        return ApiResponse.ok(userSettingsService.current());
    }

    @PutMapping
    public ApiResponse<UserSettingsVO> update(@RequestBody UserSettingsRequest request) {
        log.info("Update current user settings");
        return ApiResponse.ok(userSettingsService.update(request));
    }
}
