package com.mindharbor.modules.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
public class ProfileUpdateRequest {

    @Size(max = 64, message = "昵称长度不能超过 64 个字符")
    private String nickname;

    @Size(max = 16, message = "性别字段长度不能超过 16 个字符")
    private String gender;

    @Min(value = 1, message = "年龄必须大于 0")
    @Max(value = 150, message = "年龄不能超过 150")
    private Integer age;
}
