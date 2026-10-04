package com.mindharbor.security;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 认证 Cookie 的唯一写入入口：登录/注册下发，登出/注销清除。 */
@Component
public class JwtCookieWriter {

    private final ServletContext servletContext;

    private final boolean secureCookie;

    /** 有效期复用令牌过期时间，避免改了一处、另一处仍留旧值。 */
    private final long maxAgeSeconds;

    public JwtCookieWriter(
            ServletContext servletContext,
            @Value("${security.cookie.secure:false}") boolean secureCookie,
            @Value("${security.jwt.expiration-seconds:7200}") long maxAgeSeconds) {
        this.servletContext = servletContext;
        this.secureCookie = secureCookie;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    /** 下发登录凭证。 */
    public void write(HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", build(token, maxAgeSeconds).toString());
    }

    /**
     * 清除登录凭证：空值 + Max-Age=0，浏览器收到即删除同名 Cookie。
     * Path 必须与写入时一致，否则浏览器认为是另一个 Cookie，删不掉。
     */
    public void clear(HttpServletResponse response) {
        response.addHeader("Set-Cookie", build("", 0).toString());
    }

    private ResponseCookie build(String value, long maxAge) {
        return ResponseCookie.from(AuthCookie.NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path(resolvePath())
                .maxAge(maxAge)
                .build();
    }

    /** 作用域直接取自 context-path，不再手工同步；为空时退回根路径（空串属非法 Cookie Path）。 */
    private String resolvePath() {
        String contextPath = servletContext.getContextPath();
        return contextPath == null || contextPath.isEmpty() ? "/" : contextPath;
    }
}
