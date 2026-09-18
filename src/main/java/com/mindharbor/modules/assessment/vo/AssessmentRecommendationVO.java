package com.mindharbor.modules.assessment.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 量表推荐结果：reason 说明推荐依据，priority 越小越优先。 */
@Getter
@AllArgsConstructor
public class AssessmentRecommendationVO {

    private Long scaleId;

    private String name;

    private String description;

    private String reason;

    private int priority;
}
