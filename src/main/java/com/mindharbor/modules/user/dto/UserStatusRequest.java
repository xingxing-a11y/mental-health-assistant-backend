package com.mindharbor.modules.user.dto;

import com.mindharbor.modules.user.entity.User;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserStatusRequest {

    @NotNull(message = "用户状态不能为空")
    private User.Status status;
}
