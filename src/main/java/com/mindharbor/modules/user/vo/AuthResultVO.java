package com.mindharbor.modules.user.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 登录与注册结果：令牌通过 HttpOnly Cookie 下发，响应体只承载用户资料。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthResultVO {

    private UserVO user;
}
