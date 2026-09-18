package com.mindharbor.common.cache;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 缓存专用 JSON 编解码工厂。
 *
 * <p>缓存内容统一写成 JSON 字符串，不使用 JDK 序列化：可读、跨版本兼容，也不会因为实体字段变化
 * 产生无法反序列化的二进制缓存。这里单独创建实例而不注册成全局 {@link ObjectMapper} Bean，
 * 避免改变 Web 层的请求与响应序列化规则。
 *
 * <p>注册 Java 时间模块并把日期写成 ISO 字符串，{@code Instant}、{@code LocalDateTime} 都能安全往返；
 * 枚举按名称写入；反序列化忽略未知字段，实体新增字段后旧缓存仍可读取。
 */
final class CacheJsonMapper {

    private CacheJsonMapper() {
    }

    static ObjectMapper create() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
