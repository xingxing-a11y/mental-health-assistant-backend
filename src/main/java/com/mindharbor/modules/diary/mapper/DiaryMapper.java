package com.mindharbor.modules.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.diary.entity.Diary;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 情绪日记数据访问层，语句写在 resources/mapper/diary/DiaryMapper.xml。 */
@Mapper
public interface DiaryMapper extends BaseMapper<Diary> {

    Optional<Diary> findById(@Param("id") Long id);

    List<Diary> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    List<Diary> findRecentByUserId(
            @Param("userId") Long userId,
            @Param("limit") int limit);

    /** 与 countByUserId 共用 dateFilter 片段，保证分页结果与总数口径一致。 */
    List<Diary> findPageByUserId(
            @Param("userId") Long userId,
            @Param("offset") long offset,
            @Param("size") int size,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    long countByUserId(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}