package com.mindharbor.modules.system.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.system.service.SystemSettingService;
import com.mindharbor.modules.system.vo.SystemPolicyVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开的系统说明接口，不包含任何用户信息。 */
@RestController
@RequestMapping("/system")
public class SystemPolicyController {

    private static final Logger log = LoggerFactory.getLogger(SystemPolicyController.class);

    private final SystemSettingService systemSettingService;

    public SystemPolicyController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping("/policy")
    public ApiResponse<SystemPolicyVO> policy() {
        log.info("Load system policy");
        return ApiResponse.ok(systemSettingService.policy());
    }
}
