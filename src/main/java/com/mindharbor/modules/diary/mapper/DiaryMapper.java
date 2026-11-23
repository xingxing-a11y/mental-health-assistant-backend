package com.mindharbor.modules.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.diary.entity.Diary;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 情绪日记数据访问层，语句写在 resources/mapper/diary/DiaryMapper.xml。 */
@Mapper
public interface DiaryMapper extends BaseMapper<Diary> {

    Optional<Diary> findById(@Param("id") Long id);

    List<Diary> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    List<Diary> findRecentByUserId(
            @Param("userId") Long userId,
            @Param("limit") int limit);

    /**
     * 最近有日记的用户编号（去重）。
     *
     * <p>连击扫描只关心"这段时间动过笔的人"，全表扫用户会做无用功。
     */
    @Select("SELECT DISTINCT user_id FROM diaries WHERE created_at >= #{since} ORDER BY user_id")
    List<Long> findUserIdsWithDiarySince(@Param("since") Instant since);

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