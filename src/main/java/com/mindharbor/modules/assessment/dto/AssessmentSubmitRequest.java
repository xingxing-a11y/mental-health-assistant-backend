package com.mindharbor.modules.assessment.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentSubmitRequest {

    private Long scaleId;

    private List<Answer> answers;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Answer {

        private Long questionId;

        private Long optionId;

        private List<Long> optionIds;

        private String textAnswer;

        private String otherAnswer;

        private java.util.Map<String, Long> matrixAnswers;
    }
}
