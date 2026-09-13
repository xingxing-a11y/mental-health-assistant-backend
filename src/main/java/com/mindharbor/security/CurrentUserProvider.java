package com.mindharbor.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Provides the authenticated user id attached by the JWT interceptor. */
@Component
public class CurrentUserProvider {

    public Long getUserId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("当前请求不存在用户身份");
        }

        HttpServletRequest request = attributes.getRequest();
        Object userId = request.getAttribute("authenticatedUserId");
        if (!(userId instanceof Long)) {
            throw new IllegalStateException("当前请求用户身份无效");
        }
        return (Long) userId;
    }
}
