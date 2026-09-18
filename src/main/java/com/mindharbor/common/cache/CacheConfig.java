package com.mindharbor.common.cache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Redis 基础配置，使用 JSON 字符串避免 JDK 序列化和类型耦合。 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {
}
