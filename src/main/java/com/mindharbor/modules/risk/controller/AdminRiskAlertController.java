package com.mindharbor.modules.risk.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.common.PageResult;
import com.mindharbor.modules.risk.dto.RiskAlertHandleRequest;
import com.mindharbor.modules.risk.service.AdminRiskAlertService;
import com.mindharbor.modules.risk.vo.RiskAlertDailyStatVO;
import com.mindharbor.modules.risk.vo.RiskAlertVO;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端高危预警接口。
 *
 * <p>路径在 {@code /admin/**} 下，由 SecurityConfig 统一要求 ROLE_ADMIN，方法上不再重复标注。
 */
@RestController
@RequestMapping("/admin/risk-alerts")
public class AdminRiskAlertController {

    private static final Logger log = LoggerFactory.getLogger(AdminRiskAlertController.class);

    private final AdminRiskAlertService adminRiskAlertService;

    public AdminRiskAlertController(AdminRiskAlertService adminRiskAlertService) {
        this.adminRiskAlertService = adminRiskAlertService;
    }

    @GetMapping
    public ApiResponse<PageResult<RiskAlertVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("List risk alerts, page={}, size={}", page, size);
        return ApiResponse.ok(adminRiskAlertService.list(page, size));
    }

    @GetMapping("/stats")
    public ApiResponse<List<RiskAlertDailyStatVO>> stats(
            @RequestParam(defaultValue = "30") int days) {
        log.info("Risk alert stats, days={}", days);
        return ApiResponse.ok(adminRiskAlertService.stats(days));
    }

    /** 人工处理：记录处理方式与备注。重复调用只更新结论，不覆盖首次处理时间。 */
    @PutMapping("/{id}/handle")
    public ApiResponse<Void> handle(
            @PathVariable Long id, @Valid @RequestBody RiskAlertHandleRequest request) {
        log.info("Handle risk alert, id={}, result={}", id, request.getHandleResult());
        adminRiskAlertService.handle(id, request);
        return ApiResponse.ok(null);
    }
}
