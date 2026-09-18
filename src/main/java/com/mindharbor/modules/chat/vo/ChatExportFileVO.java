package com.mindharbor.modules.chat.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 会话导出结果，只用于响应文件流，不参与 JSON 序列化。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatExportFileVO {

    private String filename;

    private String contentType;

    @JsonIgnore
    private byte[] content;
}
