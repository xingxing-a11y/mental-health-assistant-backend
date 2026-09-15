package com.mindharbor.modules.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.diary.entity.Diary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Optional;

@Mapper
public interface DiaryMapper extends BaseMapper<Diary> {
    @Select("SELECT * FROM diaries WHERE id = #{id} LIMIT 1")
    Optional<Diary> findById(Long id);
    @Select("SELECT * FROM diaries WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Diary> findByUserIdOrderByCreatedAtDesc(Long userId);
}
