package com.mindharbor.modules.diary.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.diary.dto.DiaryRequest;
import com.mindharbor.modules.diary.service.DiaryService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class DiaryController {
    private final DiaryService service;

    public DiaryController(DiaryService service) {
        this.service = service;
    }

    @GetMapping("/diaries")
    public ApiResponse<?> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping("/diaries")
    public ApiResponse<?> create(@Valid @RequestBody DiaryRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping("/diaries/{id}")
    public ApiResponse<?> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @DeleteMapping("/diaries/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/diaries/{id}/analysis")
    public ApiResponse<?> analyze(@PathVariable Long id) {
        return ApiResponse.ok(Map.of("analysis", service.analyze(id)));
    }

    @GetMapping("/emotions/trend")
    public ApiResponse<?> trend(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return ApiResponse.ok(service.trend(startDate, endDate));
    }
}
