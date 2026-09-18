package com.mindharbor.modules.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.diary.entity.Diary;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 情绪日记数据访问层，只声明查询语句，不承载业务判断。 */
@Mapper
public interface DiaryMapper extends BaseMapper<Diary> {

    String DATE_FILTER =
            "<if test='start != null'> AND created_at &gt;= #{start}</if>"
                    + "<if test='end != null'> AND created_at &lt; #{end}</if>";

    @Select("SELECT * FROM diaries WHERE id = #{id} LIMIT 1")
    Optional<Diary> findById(Long id);

    @Select("SELECT * FROM diaries WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Diary> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Select("SELECT * FROM diaries WHERE user_id = #{userId} "
            + "ORDER BY created_at DESC LIMIT #{limit}")
    List<Diary> findRecentByUserId(
            @Param("userId") Long userId,
            @Param("limit") int limit);

    @Select("<script>"
            + "SELECT * FROM diaries WHERE user_id = #{userId}"
            + DATE_FILTER
            + " ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<Diary> findPageByUserId(
            @Param("userId") Long userId,
            @Param("offset") long offset,
            @Param("size") int size,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Select("<script>"
            + "SELECT COUNT(*) FROM diaries WHERE user_id = #{userId}"
            + DATE_FILTER
            + "</script>")
    long countByUserId(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
