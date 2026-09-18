package com.mindharbor.modules.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.assessment.entity.AssessmentFavorite;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 量表收藏数据访问层，只声明查询与删除语句，不承载业务判断。 */
@Mapper
public interface AssessmentFavoriteMapper extends BaseMapper<AssessmentFavorite> {

    @Select("SELECT * FROM assessment_favorites WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<AssessmentFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Select("SELECT * FROM assessment_favorites WHERE user_id = #{userId} AND scale_id = #{scaleId} LIMIT 1")
    Optional<AssessmentFavorite> findByUserIdAndScaleId(
            @Param("userId") Long userId,
            @Param("scaleId") Long scaleId);

    @Delete("DELETE FROM assessment_favorites WHERE user_id = #{userId} AND scale_id = #{scaleId}")
    int deleteByUserIdAndScaleId(
            @Param("userId") Long userId,
            @Param("scaleId") Long scaleId);
}
