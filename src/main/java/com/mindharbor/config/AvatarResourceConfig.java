package com.mindharbor.config;

import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 头像静态资源映射，访问路径受 JWT 拦截器保护。 */
@Configuration
public class AvatarResourceConfig implements WebMvcConfigurer {

    private final String avatarLocation;

    public AvatarResourceConfig(
            @Value("${user.avatar-upload-dir:./uploads/avatars}") String avatarUploadDirectory) {
        avatarLocation = Paths.get(avatarUploadDirectory)
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations(avatarLocation);
    }
}
