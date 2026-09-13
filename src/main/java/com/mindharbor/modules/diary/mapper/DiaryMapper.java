package com.mindharbor.modules.diary.mapper;

import com.mindharbor.modules.diary.entity.Diary;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryMapper extends JpaRepository<Diary, Long> {
    List<Diary> findByUserIdOrderByCreatedAtDesc(Long userId);
}
