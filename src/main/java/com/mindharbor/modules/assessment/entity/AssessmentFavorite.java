package com.mindharbor.modules.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 用户收藏的量表，同一用户对同一量表只会有一条记录。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("assessment_favorites")
public class AssessmentFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long scaleId;

    private Instant createdAt = Instant.now();
}
