package com.mindharbor.modules.assessment.entity;

import com.mindharbor.modules.user.entity.User;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("assessment_results")
public class AssessmentResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    @TableField(exist = false)
    private User user;

    private Long scaleId;

    @TableField(exist = false)
    private AssessmentScale scale;

    private Integer totalScore;

    private Double standardScore;

    private String rangeDescription;

    private String suggestions;

    private Instant createdAt = Instant.now();
}
