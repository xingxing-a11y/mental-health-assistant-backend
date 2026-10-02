package com.mindharbor.modules.system.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.system.dto.KnowledgeSettingsRequest;
import com.mindharbor.modules.system.service.SystemSettingService;
import com.mindharbor.modules.system.vo.KnowledgeSettingsVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 知识库分片参数全局配置接口。 */
@RestController
@RequestMapping("/admin/knowledge-settings")
public class AdminSystemSettingController {

    private static final Logger log = LoggerFactory.getLogger(AdminSystemSettingController.class);

    private final SystemSettingService systemSettingService;

    public AdminSystemSettingController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping
    public ApiResponse<KnowledgeSettingsVO> knowledgeSettings() {
        log.info("Load knowledge chunk settings");
        return ApiResponse.ok(systemSettingService.knowledgeSettings());
    }

    @PutMapping
    public ApiResponse<KnowledgeSettingsVO> updateKnowledgeSettings(
            @Valid @RequestBody KnowledgeSettingsRequest request) {
        log.info("Update knowledge chunk settings");
        return ApiResponse.ok(systemSettingService.updateKnowledgeSettings(request));
    }
}
