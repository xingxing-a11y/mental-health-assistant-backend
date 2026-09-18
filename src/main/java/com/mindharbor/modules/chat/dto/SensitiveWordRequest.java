package com.mindharbor.modules.chat.dto;

import com.mindharbor.modules.chat.entity.SensitiveWord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 敏感词维护请求。 */
@Getter
@Setter
@NoArgsConstructor
public class SensitiveWordRequest {

    @NotBlank(message = "敏感词不能为空")
    @Size(max = 64, message = "敏感词长度不能超过 64 字")
    private String word;

    @NotNull(message = "敏感级别不能为空")
    private SensitiveWord.Level level;

    /** 为空时按启用处理。 */
    private Boolean enabled;
}
