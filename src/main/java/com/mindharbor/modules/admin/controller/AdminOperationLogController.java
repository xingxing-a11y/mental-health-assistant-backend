package com.mindharbor.modules.admin.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.admin.service.AdminOperationLogService;
import com.mindharbor.modules.admin.vo.AdminOperationLogVO;
import com.mindharbor.modules.user.vo.PageResult;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员操作审计查询接口。 */
@RestController
@RequestMapping("/admin/operation-logs")
public class AdminOperationLogController {

    private static final Logger log = LoggerFactory.getLogger(AdminOperationLogController.class);

    private final AdminOperationLogService adminOperationLogService;

    public AdminOperationLogController(AdminOperationLogService adminOperationLogService) {
        this.adminOperationLogService = adminOperationLogService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminOperationLogVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("List admin operation logs, page={}, size={}", page, size);
        return ApiResponse.ok(adminOperationLogService.list(page, size, type, startDate, endDate));
    }
}
