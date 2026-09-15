package com.mindharbor.modules.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("assessment_options")
public class AssessmentOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    @TableField(exist = false)
    @JsonIgnore
    private AssessmentQuestion question;

    private String label;

    private Integer score;
}
