package com.mindharbor.modules.feedback.dto;

import com.mindharbor.modules.feedback.entity.Feedback;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 管理员处理反馈请求。 */
@Getter
@Setter
@NoArgsConstructor
public class FeedbackHandleRequest {

    @NotNull(message = "处理状态不能为空")
    private Feedback.Status status;

    @Size(max = 500, message = "处理备注长度不能超过 500 字")
    private String remark;
}
