package com.mindharbor.modules.assessment.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
public class QuestionRequest {

    @NotNull(message = "题目顺序不能为空")
    private Integer order;

    @NotBlank(message = "题目内容不能为空")
    @Size(max = 1000, message = "题目内容不能超过 1000 个字符")
    private String content;

    private String type = "SINGLE";

    private List<String> matrixRows;

    private List<String> matrixColumns;

    @Valid
    private List<OptionRequest> options;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class OptionRequest {

        @NotBlank(message = "选项内容不能为空")
        @Size(max = 500, message = "选项内容不能超过 500 个字符")
        private String label;

        @NotNull(message = "选项分值不能为空")
        private Integer score;
    }
}
