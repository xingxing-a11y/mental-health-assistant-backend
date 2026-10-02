package com.mindharbor.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 从安全上下文读取当前登录用户，供 Service 层获取调用者身份。 */
@Component
public class CurrentUserProvider {

    /**
     * 取当前登录用户，未登录时返回空。
     * 供公开接口的可选个性化字段使用，避免匿名访问被当成服务端异常。
     */
    public Optional<Long> findUserId() {
        // 身份由 JwtAuthenticationFilter 验签通过后写入安全上下文，未登录时此处为空
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long id) {
            return Optional.of(id);
        }
        return Optional.empty();
    }

    /** 从SecurityContextHolder中获取userId提供给service层使用 */
    public Long getUserId() {
        return findUserId()
                .orElseThrow(() -> new IllegalStateException("当前请求用户身份无效"));
    }
}
