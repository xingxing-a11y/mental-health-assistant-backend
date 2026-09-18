package com.mindharbor.modules.relax.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.relax.dto.RelaxRecordRequest;
import com.mindharbor.modules.relax.dto.StressLogRequest;
import com.mindharbor.modules.relax.service.RelaxService;
import jakarta.validation.Valid;
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

    private final RelaxService service;

    public RelaxController(RelaxService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<?> overview() {
        log.info("Load relax overview");
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/meditations")
    public ApiResponse<?> meditations() {
        log.info("List meditation scripts");
        return ApiResponse.ok(service.meditations());
    }

    @GetMapping("/records")
    public ApiResponse<?> records(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type) {
        log.info("List relax records, type={}", type);
        return ApiResponse.ok(service.records(page, size, type));
    }

    @PostMapping("/records")
    public ApiResponse<?> createRecord(@Valid @RequestBody RelaxRecordRequest request) {
        log.info("Create relax record, type={}", request.getType());
        return ApiResponse.ok(service.createRecord(request));
    }

    @GetMapping("/stress-logs")
    public ApiResponse<?> stressLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("List stress logs");
        return ApiResponse.ok(service.stressLogs(page, size));
    }

    @PostMapping("/stress-logs")
    public ApiResponse<?> createStressLog(@Valid @RequestBody StressLogRequest request) {
        log.info("Create stress log, level={}", request.getLevel());
        return ApiResponse.ok(service.createStressLog(request));
    }

    @GetMapping("/stress-trend")
    public ApiResponse<?> stressTrend(@RequestParam(defaultValue = "30") int days) {
        log.info("Load stress trend, days={}", days);
        return ApiResponse.ok(service.stressTrend(days));
    }
}
