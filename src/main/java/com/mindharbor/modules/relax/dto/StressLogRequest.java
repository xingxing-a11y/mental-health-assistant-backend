package com.mindharbor.modules.relax.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 压力打卡请求，等级 1-10。 */
@Getter
@Setter
@NoArgsConstructor
public class StressLogRequest {

    @NotNull(message = "压力等级不能为空")
    @Min(value = 1, message = "压力等级必须在 1-10 之间")
    @Max(value = 10, message = "压力等级必须在 1-10 之间")
    private Integer level;

    @Size(max = 200, message = "备注不能超过 200 字")
    private String note;
}
