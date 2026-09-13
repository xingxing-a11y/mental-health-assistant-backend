package com.mindharbor.modules.diary.service;

import com.mindharbor.modules.diary.dto.DiaryRequest;
import java.time.LocalDate;
import java.util.List;

public interface DiaryService {
    List<?> list();
    Object get(Long id);
    Object create(DiaryRequest request);
    void delete(Long id);
    String analyze(Long id);
    List<?> trend(LocalDate startDate, LocalDate endDate);
}
