package com.mindharbor.modules.user.dto;

import com.mindharbor.modules.user.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserStatusRequest {

    private User.Status status;
}
