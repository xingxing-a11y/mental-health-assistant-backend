package com.mindharbor.modules.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("assessment_scales")
public class AssessmentScale {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String applicableScene;
}


