package com.mindharbor.security;

import com.mindharbor.modules.user.service.UserStatusService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 把合法 JWT 转换为 Spring Security 身份，使接口鉴权由安全框架统一裁决。
 * 令牌缺失或非法时不写入身份，交由授权规则返回 401。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 请求属性名：令牌对应的账号是否可用，供 MVC 拦截器复用，避免同一请求重复查询数据库。 */
    public static final String ACTIVE_ACCOUNT_ATTRIBUTE = "authenticatedAccountActive";

    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtTokenService tokenService;

    private final JwtTokenResolver tokenResolver;

    private final UserStatusService userStatusService;

    public JwtAuthenticationFilter(
            JwtTokenService tokenService,
            JwtTokenResolver tokenResolver,
            UserStatusService userStatusService) {
        this.tokenService = tokenService;
        this.tokenResolver = tokenResolver;
        this.userStatusService = userStatusService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = tokenResolver.resolve(request);
        //todo 48,57,68 处签名验证算了三次
        if (token != null && !token.isBlank() && tokenService.isValid(token)) {
            authenticate(request, token);
        }
        filterChain.doFilter(request, response);
    }

    /** 令牌合法但账号已禁用时不写入身份，使旧令牌在过期前立即失效。 */
    private void authenticate(HttpServletRequest request, String token) {
        try {
            Long userId = tokenService.getUserId(token);
            //获取账号状态
            boolean active = userStatusService.isActive(userId);
            //将状态写进请求中
            request.setAttribute(ACTIVE_ACCOUNT_ATTRIBUTE, active);
            //判断账号是否被禁用，被禁用则将jwt令牌进行清空
            if (!active) {
                SecurityContextHolder.clearContext();
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            //这个null不能省
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    resolveAuthorities(tokenService.getRole(token))));
            SecurityContextHolder.setContext(context);
        } catch (IllegalArgumentException exception) {
            // 令牌载荷异常时保持匿名身份，避免伪造内容影响鉴权结果
            SecurityContextHolder.clearContext();
        }
    }

    private List<SimpleGrantedAuthority> resolveAuthorities(String role) {
        if (role == null || role.isBlank()) {
            return List.of();
        }
        //在role前加ROLE_，可以让hasRole识别
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role));
    }
}
