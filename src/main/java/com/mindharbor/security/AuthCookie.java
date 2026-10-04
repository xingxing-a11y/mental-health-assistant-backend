package com.mindharbor.security;

/**
 * 认证 Cookie 的跨端契约：下发端（{@link JwtCookieWriter}）与解析端（{@link JwtTokenResolver}）
 * 共用同一个名字，避免两处字面量各自漂移 —— 那种不一致不会报错，只会让所有用户登录后立即掉线。
 *
 * <p>改名时编译器帮不上忙，需手工同步的副本共 4 处：docs/openapi.yaml 的
 * securitySchemes.cookieAuth.name、docs/API.md 的鉴权说明，以及 JwtCookieWriterTest
 * 里两条刻意硬编码的断言（它正是用来卡住本常量被误改的，别改成引用本常量）。
 */
public final class AuthCookie {

    public static final String NAME = "mh_token";

    private AuthCookie() {
    }
}
