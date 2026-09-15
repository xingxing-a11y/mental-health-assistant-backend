package com.mindharbor.config;

import com.mindharbor.security.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;
import java.io.IOException;

@Configuration
public class JwtWebConfig implements WebMvcConfigurer {

    private final JwtTokenService tokenService;

    public JwtWebConfig(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginRateLimitInterceptor())
                .addPathPatterns("/auth/login");
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Frame-Options", "DENY");
                response.setHeader("Referrer-Policy", "no-referrer");
                response.setHeader("Cache-Control", "no-store");

                String token = extractToken(request);
                if (token == null || !tokenService.isValid(token)) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或 JWT 无效");
                    return false;
                }

                request.setAttribute("authenticatedUserId", tokenService.getUserId(token));

                if (request.getRequestURI().contains("/admin/")
                        && !"ADMIN".equals(tokenService.getRole(token))) {
                    writeError(response, HttpServletResponse.SC_FORBIDDEN, "无权限访问该资源");
                    return false;
                }

                return true;
            }

            private String  extractToken(HttpServletRequest request) {
                Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        if ("mh_token".equals(cookie.getName())) {
                            return cookie.getValue();
                        }
                    }
                }
                String authorization = request.getHeader("Authorization");
                if (authorization != null && authorization.startsWith("Bearer ")) {
                    return authorization.substring(7);
                }
                return null;
            }

            private void writeError(
                    HttpServletResponse response,
                    int status,
                    String message) throws IOException {
                response.setStatus(status);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null}", status, message));
            }
        }).addPathPatterns(
                "/users/**",
                "/chat/**",
                "/assessments/**",
                "/diaries/**",
                "/emotions/**",
                "/admin/**")
                .excludePathPatterns(
                        "/auth/**",
                        "/assessments/scales",
                        "/assessments/scales/*",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/doc.html");
    }
}
