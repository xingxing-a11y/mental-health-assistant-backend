package com.mindharbor.modules.assessment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
public class ScaleRequest {

    @NotBlank(message = "量表名称不能为空")
    @Size(max = 120, message = "量表名称不能超过 120 个字符")
    private String name;

    @Size(max = 2000, message = "量表简介不能超过 2000 个字符")
    private String description;

    @Size(max = 200, message = "适用场景不能超过 200 个字符")
    private String applicableScene;
}
