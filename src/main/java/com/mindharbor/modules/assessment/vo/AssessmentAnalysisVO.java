package com.mindharbor.modules.assessment.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 测评 AI 解读视图，只返回面向用户的解读文本。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentAnalysisVO {

    private String analysis;
}
