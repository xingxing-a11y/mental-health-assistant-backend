package com.mindharbor.modules.diary.service;

import com.mindharbor.modules.diary.dto.DiaryRequest;
import com.mindharbor.modules.user.vo.PageResult;
import java.time.LocalDate;
import java.util.List;

public interface DiaryService {

    List<?> listRecent();

    PageResult<?> listAll(int page, int size, LocalDate startDate, LocalDate endDate);

    Object get(Long id);

    Object create(DiaryRequest request);

    Object update(Long id, DiaryRequest request);

    void delete(Long id);

    String analyze(Long id);

    List<?> trend(LocalDate startDate, LocalDate endDate);
}
