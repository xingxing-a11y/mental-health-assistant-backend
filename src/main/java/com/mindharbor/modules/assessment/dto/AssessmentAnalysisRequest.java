package com.mindharbor.modules.assessment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AssessmentAnalysisRequest {

    @NotNull(message = "测评结果不能为空")
    private Long resultId;
}
