package com.mindharbor.modules.user.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
public class PasswordUpdateRequest {

    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度必须为 6 到 32 位")
    @Pattern(regexp = "^[!-~]*$", message = "新密码只能包含英文字母、数字和常见符号")
    private String newPassword;
}
