package com.mindharbor.modules.diary.service;

import com.mindharbor.modules.diary.dto.DiaryRequest;
import com.mindharbor.modules.diary.vo.DiaryVO;
import com.mindharbor.modules.diary.vo.EmotionTrendPointVO;
import com.mindharbor.modules.user.vo.PageResult;
import java.time.LocalDate;
import java.util.List;

public interface DiaryService {

    List<DiaryVO> listRecent();

    PageResult<DiaryVO> listAll(int page, int size, LocalDate startDate, LocalDate endDate);

    DiaryVO get(Long id);

    DiaryVO create(DiaryRequest request);

    DiaryVO update(Long id, DiaryRequest request);

    void delete(Long id);

    String analyze(Long id);

    List<EmotionTrendPointVO> trend(LocalDate startDate, LocalDate endDate);
}
