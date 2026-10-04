package com.mindharbor.modules.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
public class ProfileUpdateRequest {

    @Size(max = 64, message = "昵称长度不能超过 64 个字符")
    private String nickname;

    @Pattern(regexp = "^(?:男|女|不愿透露)?$", message = "性别只能是 男、女 或 不愿透露")
    private String gender;

    @Min(value = 1, message = "年龄必须大于 0")
    @Max(value = 150, message = "年龄不能超过 150")
    private Integer age;
}
