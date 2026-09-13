package com.mindharbor.modules.knowledge.dto;

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
public class KnowledgeDocumentRequest {

    @NotBlank(message = "文档标题不能为空")
    @Size(max = 200, message = "文档标题不能超过 200 个字符")
    private String title;

    @NotBlank(message = "知识库正文不能为空")
    @Size(max = 200000, message = "知识库正文不能超过 200000 个字符")
    private String content;
}
