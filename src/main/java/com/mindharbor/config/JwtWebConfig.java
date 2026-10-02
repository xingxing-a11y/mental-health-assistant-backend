package com.mindharbor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册登录接口限流拦截器
 */
@Configuration
public class JwtWebConfig implements WebMvcConfigurer {

    /** 对频繁请求进行拦截 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginRateLimitInterceptor())
                .addPathPatterns("/auth/login");
    }
}
