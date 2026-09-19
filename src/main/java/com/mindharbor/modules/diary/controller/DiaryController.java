package com.mindharbor.modules.diary.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.diary.dto.DiaryRequest;
import com.mindharbor.modules.diary.service.DiaryService;
import com.mindharbor.modules.diary.vo.DiaryAnalysisVO;
import com.mindharbor.modules.diary.vo.DiaryVO;
import com.mindharbor.modules.diary.vo.EmotionTrendPointVO;
import com.mindharbor.modules.user.vo.PageResult;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DiaryController {

    private final DiaryService service;

    public DiaryController(DiaryService service) {
        this.service = service;
    }

    @GetMapping("/diaries/recent")
    public ApiResponse<List<DiaryVO>> listRecent() {
        return ApiResponse.ok(service.listRecent());
    }

    @GetMapping("/diaries")
    public ApiResponse<PageResult<DiaryVO>> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "startDate", required = false) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        return ApiResponse.ok(service.listAll(page, size, startDate, endDate));
    }

    @PostMapping("/diaries")
    public ApiResponse<DiaryVO> create(@Valid @RequestBody DiaryRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping("/diaries/{diaryId}")
    public ApiResponse<DiaryVO> get(@PathVariable Long diaryId) {
        return ApiResponse.ok(service.get(diaryId));
    }

    @PutMapping("/diaries/{diaryId}")
    public ApiResponse<DiaryVO> update(
            @PathVariable Long diaryId,
            @Valid @RequestBody DiaryRequest request) {
        return ApiResponse.ok(service.update(diaryId, request));
    }

    @DeleteMapping("/diaries/{diaryId}")
    public ApiResponse<Void> delete(@PathVariable Long diaryId) {
        service.delete(diaryId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/diaries/{diaryId}/analysis")
    public ApiResponse<DiaryAnalysisVO> analyze(@PathVariable Long diaryId) {
        return ApiResponse.ok(new DiaryAnalysisVO(service.analyze(diaryId)));
    }

    @GetMapping("/emotions/trend")
    public ApiResponse<List<EmotionTrendPointVO>> trend(
            @RequestParam(name = "startDate", required = false) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) LocalDate endDate) {
        return ApiResponse.ok(service.trend(startDate, endDate));
    }
}
