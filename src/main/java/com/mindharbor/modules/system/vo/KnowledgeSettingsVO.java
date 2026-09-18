package com.mindharbor.modules.system.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 知识库分片参数视图。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSettingsVO {

    private int chunkSize;

    private int chunkOverlap;
}
