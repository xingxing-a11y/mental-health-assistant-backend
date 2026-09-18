package com.mindharbor.modules.assessment.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 测评基础报告视图，只包含结果标识、分值和面向用户的提示文案。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentReportVO {

    private Long resultId;

    private Integer totalScore;

    private Double standardScore;

    private String rangeDescription;

    private List<String> suggestions;
}
