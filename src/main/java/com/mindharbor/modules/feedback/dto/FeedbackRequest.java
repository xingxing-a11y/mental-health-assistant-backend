package com.mindharbor.modules.feedback.dto;

import com.mindharbor.modules.feedback.entity.Feedback;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 提交反馈请求，联系方式可选。 */
@Getter
@Setter
@NoArgsConstructor
public class FeedbackRequest {

    @NotNull(message = "反馈类型不能为空")
    private Feedback.Type type;

    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 2000, message = "反馈内容长度不能超过 2000 字")
    private String content;

    @Size(max = 200, message = "联系方式长度不能超过 200 字")
    private String contact;
}
