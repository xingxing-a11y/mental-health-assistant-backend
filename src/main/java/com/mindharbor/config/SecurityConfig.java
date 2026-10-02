package com.mindharbor.config;

import com.mindharbor.modules.user.service.UserStatusService;
import com.mindharbor.security.JwtAuthenticationFilter;
import com.mindharbor.security.JwtTokenResolver;
import com.mindharbor.security.JwtTokenService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Spring Security 负责接口鉴权策略，JWT 校验结果由 JwtAuthenticationFilter 写入安全上下文。
 * MVC 拦截器只补充登录限流，不再单独决定接口是否可访问。
 */
@Configuration
public class SecurityConfig {

    /** 无需登录即可访问的接口。 */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/auth/**",
            "/articles/**",
            "/announcements",
            "/assessments/scales",
            "/assessments/scales/*",
            "/system/policy",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    private final JwtTokenService jwtTokenService;

    private final JwtTokenResolver jwtTokenResolver;

    private final UserStatusService userStatusService;

    public SecurityConfig(
            JwtTokenService jwtTokenService,
            JwtTokenResolver jwtTokenResolver,
            UserStatusService userStatusService) {
        this.jwtTokenService = jwtTokenService;
        this.jwtTokenResolver = jwtTokenResolver;
        this.userStatusService = userStatusService;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 令牌只存放在 HttpOnly + SameSite=Lax 的 Cookie 中，接口无会话状态，因此不需要 CSRF 令牌
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenService, jwtTokenResolver, userStatusService),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        // 流式响应和错误页会以 ASYNC/ERROR 方式二次派发，此时不重复鉴权
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated());
        return http.build();
    }

    /** 未登录统一返回 401 JSON。 */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, exception) ->
                writeJson(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        resolveUnauthorizedMessage(request));
    }

    /** 令牌合法但账号被禁用时给出明确提示，其余情况保持统一的未登录提示。 */
    private String resolveUnauthorizedMessage(HttpServletRequest request) {
        Object active = request.getAttribute(JwtAuthenticationFilter.ACTIVE_ACCOUNT_ATTRIBUTE);
        if (Boolean.FALSE.equals(active)) {
            return "账号已被禁用，请联系管理员";
        }
        return "未登录或 JWT 无效";
    }

    /** 已登录但角色不足时返回 403 JSON，不暴露后端权限细节。 */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) ->
                writeJson(response, HttpServletResponse.SC_FORBIDDEN, "无权限访问该资源");
    }

    /** 返回的json格式 */
    private void writeJson(
            HttpServletResponse response,
            int status,
            String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
