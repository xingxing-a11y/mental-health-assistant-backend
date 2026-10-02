package com.mindharbor.modules.diary.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.common.PageResult;
import com.mindharbor.modules.diary.dto.DiaryRequest;
import com.mindharbor.modules.diary.service.DiaryService;
import com.mindharbor.modules.diary.vo.DiaryAnalysisVO;
import com.mindharbor.modules.diary.vo.DiaryVO;
import com.mindharbor.modules.diary.vo.EmotionTrendPointVO;
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

/** 情绪日记与情绪趋势接口，全部按当前登录用户隔离；单条操作越权返回 403。 */
@RestController
public class DiaryController {

    private final DiaryService diaryService;

    public DiaryController(DiaryService diaryService) {
        this.diaryService = diaryService;
    }

    /** 最近 2 条，供首页情绪卡片。 */
    @GetMapping("/diaries/recent")
    public ApiResponse<List<DiaryVO>> listRecent() {
        return ApiResponse.ok(diaryService.listRecent());
    }

    /** 分页查询，起止日期含首尾两天，size 超过 50 按 50 处理。 */
    @GetMapping("/diaries")
    public ApiResponse<PageResult<DiaryVO>> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "startDate", required = false) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        return ApiResponse.ok(diaryService.listAll(page, size, startDate, endDate));
    }

    @PostMapping("/diaries")
    public ApiResponse<DiaryVO> create(@Valid @RequestBody DiaryRequest request) {
        return ApiResponse.ok(diaryService.create(request));
    }

    @GetMapping("/diaries/{diaryId}")
    public ApiResponse<DiaryVO> get(@PathVariable Long diaryId) {
        return ApiResponse.ok(diaryService.get(diaryId));
    }

    @PutMapping("/diaries/{diaryId}")
    public ApiResponse<DiaryVO> update(
            @PathVariable Long diaryId,
            @Valid @RequestBody DiaryRequest request) {
        return ApiResponse.ok(diaryService.update(diaryId, request));
    }

    @DeleteMapping("/diaries/{diaryId}")
    public ApiResponse<Void> delete(@PathVariable Long diaryId) {
        diaryService.delete(diaryId);
        return ApiResponse.ok(null);
    }

    /** 调用 AI 生成调节建议，不做医学诊断，耗时明显长于其他接口。 */
    @PostMapping("/diaries/{diaryId}/analysis")
    public ApiResponse<DiaryAnalysisVO> analyze(@PathVariable Long diaryId) {
        return ApiResponse.ok(new DiaryAnalysisVO(diaryService.analyze(diaryId)));
    }

    /** 缺省返回最近 30 天，起止日期含首尾两天。 */
    @GetMapping("/emotions/trend")
    public ApiResponse<List<EmotionTrendPointVO>> trend(
            @RequestParam(name = "startDate", required = false) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) LocalDate endDate) {
        return ApiResponse.ok(diaryService.trend(startDate, endDate));
    }
}
