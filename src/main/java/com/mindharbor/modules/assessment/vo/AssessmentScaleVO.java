package com.mindharbor.modules.assessment.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 量表列表视图：在量表基础字段上补充当前用户的收藏状态。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentScaleVO {

    private Long id;

    private String name;

    private String description;

    private String applicableScene;

    private Boolean published;

    private boolean favorited;
}
