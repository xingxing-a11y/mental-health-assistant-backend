package com.mindharbor.modules.diary.service;

import com.mindharbor.modules.diary.dto.DiaryRequest;
import com.mindharbor.modules.diary.vo.DiaryVO;
import com.mindharbor.modules.diary.vo.EmotionTrendPointVO;
import com.mindharbor.modules.user.vo.PageResult;
import java.time.LocalDate;
import java.util.List;

/** 情绪日记业务接口：查询一律按当前登录用户隔离，单条操作先校验归属。 */
public interface DiaryService {

    /** 最近 2 条，供首页情绪卡片。 */
    List<DiaryVO> listRecent();

    /** 起止日期含首尾两天，size 超过 50 按 50 处理。 */
    PageResult<DiaryVO> listAll(int page, int size, LocalDate startDate, LocalDate endDate);

    DiaryVO create(DiaryRequest request);

    DiaryVO get(Long id);

    DiaryVO update(Long id, DiaryRequest request);

    void delete(Long id);

    /** 调用 AI 生成调节建议，不做医学诊断。 */
    String analyze(Long id);

    /** 缺省返回最近 30 天，起止日期含首尾两天。 */
    List<EmotionTrendPointVO> trend(LocalDate startDate, LocalDate endDate);
}
