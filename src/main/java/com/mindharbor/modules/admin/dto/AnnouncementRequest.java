package com.mindharbor.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AnnouncementRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 200, message = "公告标题不能超过 200 个字符")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 10000, message = "公告内容不能超过 10000 个字符")
    private String content;
}
