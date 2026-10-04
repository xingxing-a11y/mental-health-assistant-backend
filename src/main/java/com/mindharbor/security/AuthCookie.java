package com.mindharbor.security;

/**
 * 认证 Cookie 的跨端契约：下发端（{@link JwtCookieWriter}）与解析端（{@link JwtTokenResolver}）
 * 共用同一个名字，避免两处字面量各自漂移 —— 那种不一致不会报错，只会让所有用户登录后立即掉线。
 * 这个名字同时出现在 docs/API.md 的鉴权说明里，改名需同步文档。
 */
public final class AuthCookie {

    public static final String NAME = "mh_token";

    private AuthCookie() {
    }
}
