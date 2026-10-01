package com.mindharbor.config;

import com.mindharbor.modules.user.service.UserStatusService;
import com.mindharbor.security.JwtAuthenticationFilter;
import com.mindharbor.security.JwtTokenResolver;
import com.mindharbor.security.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册登录限流与 JWT 上下文拦截器。
 * 接口鉴权策略由 SecurityConfig 裁决，这里负责安全响应头、令牌与账号状态二次校验、当前用户信息。
 */
@Configuration
public class JwtWebConfig implements WebMvcConfigurer {

    private final JwtTokenService tokenService;

    private final JwtTokenResolver tokenResolver;

    private final UserStatusService userStatusService;

    public JwtWebConfig(
            JwtTokenService tokenService,
            JwtTokenResolver tokenResolver,
            UserStatusService userStatusService) {
        this.tokenService = tokenService;
        this.tokenResolver = tokenResolver;
        this.userStatusService = userStatusService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginRateLimitInterceptor())
                .addPathPatterns("/auth/login");

        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    Object handler) throws IOException {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Frame-Options", "DENY");
                response.setHeader("Referrer-Policy", "no-referrer");
                response.setHeader("Cache-Control", "no-store");

                String token = tokenResolver.resolve(request);
                if (token == null || !tokenService.isValid(token)) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或 JWT 无效");
                    return false;
                }

                Long userId = tokenService.getUserId(token);
                if (!isActiveAccount(request, userId)) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "账号已被禁用，请联系管理员");
                    return false;
                }

                request.setAttribute("authenticatedUserId", userId);

                if (request.getRequestURI().contains("/admin/")
                        && !"ADMIN".equals(tokenService.getRole(token))) {
                    writeError(response, HttpServletResponse.SC_FORBIDDEN, "无权限访问该资源");
                    return false;
                }

                return true;
            }

            /**
             * 复用安全过滤器写入的账号状态，属性缺失时再查一次，
             * 保证禁用账号的旧令牌在拦截器侧同样被拦住。
             */
            private boolean isActiveAccount(HttpServletRequest request, Long userId) {
                Object cached = request.getAttribute(JwtAuthenticationFilter.ACTIVE_ACCOUNT_ATTRIBUTE);
                if (cached instanceof Boolean) {
                    return (Boolean) cached;
                }

                boolean active = userStatusService.isActive(userId);
                request.setAttribute(JwtAuthenticationFilter.ACTIVE_ACCOUNT_ATTRIBUTE, active);
                return active;
            }

            /** 报错信息格式书写 */
            private void writeError(
                    HttpServletResponse response,
                    int status,
                    String message) throws IOException {
                response.setStatus(status);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        String.format(
                                "{\"code\":%d,\"message\":\"%s\",\"data\":null}",
                                status,
                                message));
            }
        }).addPathPatterns(
                "/users/**",
                "/chat/**",
                "/assessments/**",
                "/diaries/**",
                "/emotions/**",
                "/feedbacks/**",
                "/notifications/**",
                "/relax/**",
                "/uploads/**",
                "/admin/**")
                .excludePathPatterns(
                        "/auth/**",
                        "/assessments/scales",
                        "/assessments/scales/*",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html");
    }
}
