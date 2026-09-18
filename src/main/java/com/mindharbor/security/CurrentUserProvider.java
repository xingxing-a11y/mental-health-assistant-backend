package com.mindharbor.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Provides the authenticated user id attached by the JWT interceptor. */
@Component
public class CurrentUserProvider {

    /**
     * 取当前登录用户，未登录时返回空。
     * 供公开接口的可选个性化字段使用，避免匿名访问被当成服务端异常。
     */
    public Optional<Long> findUserId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            Object userId = attributes.getRequest().getAttribute("authenticatedUserId");
            if (userId instanceof Long id) {
                return Optional.of(id);
            }
        }

        // 拦截器未覆盖的接口直接使用安全上下文中的身份，避免因路径清单遗漏导致取不到用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long id) {
            return Optional.of(id);
        }
        return Optional.empty();
    }

    public Long getUserId() {
        return findUserId()
                .orElseThrow(() -> new IllegalStateException("当前请求用户身份无效"));
    }
}
