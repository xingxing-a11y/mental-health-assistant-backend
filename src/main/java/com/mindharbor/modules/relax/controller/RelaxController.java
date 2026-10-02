package com.mindharbor.modules.relax.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.relax.dto.RelaxRecordRequest;
import com.mindharbor.modules.relax.dto.StressLogRequest;
import com.mindharbor.modules.relax.service.RelaxService;
import com.mindharbor.modules.relax.vo.RelaxMeditationVO;
import com.mindharbor.modules.relax.vo.RelaxOverviewVO;
import com.mindharbor.modules.relax.vo.RelaxRecordVO;
import com.mindharbor.modules.relax.vo.StressLogVO;
import com.mindharbor.modules.relax.vo.StressTrendPointVO;
import com.mindharbor.modules.user.vo.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 放松小工具接口，只接收参数与封装响应，业务逻辑在 service 层。 */
@RestController
@RequestMapping("/relax")
public class RelaxController {

    private static final Logger log = LoggerFactory.getLogger(RelaxController.class);

    private final RelaxService relaxService;

    public RelaxController(RelaxService relaxService) {
        this.relaxService = relaxService;
    }

    @GetMapping("/overview")
    public ApiResponse<RelaxOverviewVO> overview() {
        log.info("Load relax overview");
        return ApiResponse.ok(relaxService.overview());
    }

    @GetMapping("/meditations")
    public ApiResponse<List<RelaxMeditationVO>> meditations() {
        log.info("List meditation scripts");
        return ApiResponse.ok(relaxService.meditations());
    }

    @GetMapping("/records")
    public ApiResponse<PageResult<RelaxRecordVO>> records(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type) {
        log.info("List relax records, type={}", type);
        return ApiResponse.ok(relaxService.records(page, size, type));
    }

    @PostMapping("/records")
    public ApiResponse<RelaxRecordVO> createRecord(
            @Valid @RequestBody RelaxRecordRequest request) {
        log.info("Create relax record, type={}", request.getType());
        return ApiResponse.ok(relaxService.createRecord(request));
    }

    @GetMapping("/stress-logs")
    public ApiResponse<PageResult<StressLogVO>> stressLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("List stress logs");
        return ApiResponse.ok(relaxService.stressLogs(page, size));
    }

    @PostMapping("/stress-logs")
    public ApiResponse<StressLogVO> createStressLog(
            @Valid @RequestBody StressLogRequest request) {
        log.info("Create stress log, level={}", request.getLevel());
        return ApiResponse.ok(relaxService.createStressLog(request));
    }

    @GetMapping("/stress-trend")
    public ApiResponse<List<StressTrendPointVO>> stressTrend(
            @RequestParam(defaultValue = "30") int days) {
        log.info("Load stress trend, days={}", days);
        return ApiResponse.ok(relaxService.stressTrend(days));
    }
}
