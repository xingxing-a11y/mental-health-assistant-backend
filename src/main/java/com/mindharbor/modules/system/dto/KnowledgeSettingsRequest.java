package com.mindharbor.modules.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 知识库分片参数，修改后对后续向量化生效。 */
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeSettingsRequest {

    @NotNull(message = "分片大小不能为空")
    @Min(value = 100, message = "分片大小不能小于 100")
    @Max(value = 4000, message = "分片大小不能大于 4000")
    private Integer chunkSize;

    @NotNull(message = "重叠长度不能为空")
    @Min(value = 0, message = "重叠长度不能小于 0")
    @Max(value = 1000, message = "重叠长度不能大于 1000")
    private Integer chunkOverlap;
}
