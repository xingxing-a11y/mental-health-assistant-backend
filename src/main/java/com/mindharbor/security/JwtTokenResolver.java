package com.mindharbor.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** 统一解析请求携带的 JWT：优先取 mh_token Cookie，其次退回 Authorization 请求头。 */
@Component
public class JwtTokenResolver {

    private static final String TOKEN_COOKIE_NAME = "mh_token";

    private static final String BEARER_PREFIX = "Bearer ";

    /** 返回原始令牌，请求未携带令牌时返回 null。 */
    public String resolve(HttpServletRequest request) {
        String cookieToken = resolveFromCookie(request);
        if (cookieToken != null) {
            return cookieToken;
        }
        return resolveFromAuthorizationHeader(request);
    }

    private String resolveFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String resolveFromAuthorizationHeader(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length());
    }
}
