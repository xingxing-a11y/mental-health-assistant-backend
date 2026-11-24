package com.mindharbor.modules.risk.dto;

import com.mindharbor.modules.risk.entity.HandleResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 管理员处理一条高危预警时提交的结论。 */
@Getter
@Setter
@NoArgsConstructor
public class RiskAlertHandleRequest {

    @NotNull(message = "请选择处理方式")
    private HandleResult handleResult;

    /** 处理备注，例如联系了谁、对方状态如何。 */
    @Size(max = 200, message = "处理备注最多 200 字")
    private String handleNote;
}
