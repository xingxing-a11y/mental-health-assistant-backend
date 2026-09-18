package com.mindharbor.modules.assessment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 量表收藏状态请求：true 表示收藏，false 表示取消收藏。 */
@Getter
@Setter
@NoArgsConstructor
public class FavoriteScaleRequest {

    @NotNull(message = "收藏状态不能为空")
    private Boolean favorite;
}
