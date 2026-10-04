package com.mindharbor.modules.user.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "账号不能为空")
    @Size(min = 6, max=32, message = "账号长度必须为 6 到 32 位")
    @Pattern(regexp = "^(?=.*[A-Za-z0-9])[A-Za-z0-9@._-]*$",
            message = "账号只能包含字母、数字和 @ . _ -，且至少含一个字母或数字")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max=32, message = "密码长度必须为 6 到 32 位")
    @Pattern(regexp = "^[!-~]*$", message = "密码只能包含英文字母、数字和常见符号")
    private String password;

    @NotBlank(message = "请确认密码")
    private String confirmPassword;

    private String nickname;

    @Pattern(regexp = "^(?:男|女|不愿透露)?$", message = "性别只能是 男、女 或 不愿透露")
    private String gender;

    private Integer age;
}
