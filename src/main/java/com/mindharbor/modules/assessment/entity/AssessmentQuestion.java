package com.mindharbor.modules.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("assessment_questions")
public class AssessmentQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scaleId;

    @TableField(exist = false)
    @JsonIgnore
    private AssessmentScale scale;

    private Integer questionOrder;

    private String content;

    private String type = "SINGLE";

    private String matrixRows;

    private String matrixColumns;

    @TableField(exist = false)
    private List<AssessmentOption> options = new ArrayList<>();
}
