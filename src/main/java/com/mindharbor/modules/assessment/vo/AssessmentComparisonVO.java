package com.mindharbor.modules.assessment.vo;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 同一量表的历次结果，用于对比分数变化。 */
@Getter
@AllArgsConstructor
public class AssessmentComparisonVO {

    private Long resultId;

    private Integer totalScore;

    private Double standardScore;

    private String rangeDescription;

    private Instant createdAt;
}
