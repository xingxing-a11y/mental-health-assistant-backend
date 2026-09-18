package com.mindharbor.modules.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 发送前预校验请求，只提交待发送内容。 */
@Getter
@Setter
@NoArgsConstructor
public class SensitiveCheckRequest {

    @NotBlank(message = "内容不能为空")
    @Size(max = 2000, message = "内容长度不能超过 2000 字")
    private String content;
}
