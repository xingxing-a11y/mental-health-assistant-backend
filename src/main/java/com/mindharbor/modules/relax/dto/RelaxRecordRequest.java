package com.mindharbor.modules.relax.dto;

import com.mindharbor.modules.relax.entity.RelaxRecord.Type;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 放松练习打卡请求。 */
@Getter
@Setter
@NoArgsConstructor
public class RelaxRecordRequest {

    @NotNull(message = "练习类型不能为空")
    private Type type;

    @Min(value = 1, message = "练习时长必须大于 0 秒")
    @Max(value = 3600, message = "练习时长不能超过 3600 秒")
    private int durationSeconds;

    @Min(value = 1, message = "练习轮次必须大于 0")
    @Max(value = 100, message = "练习轮次不能超过 100")
    private Integer rounds;
}
