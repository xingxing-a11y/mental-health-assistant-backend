package com.mindharbor.common.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aliyun.oss.OSS;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * 条件装配：storage.type 决定用本地磁盘还是对象存储。
 *
 * <p>这里刻意用手工 {@link AnnotationConfigApplicationContext} 而不是 @SpringBootTest —— 后者会拉起
 * 完整上下文，连带要求 MySQL、Redis、Chroma 都在跑，`mvn test` 就不再是「随时可跑」了。
 * 本类只注册 {@link AvatarStorageConfig}，因此不依赖任何外部服务。
 */
@DisplayName("存储装配：storage.type 决定实现")
class AvatarStorageWiringTest {

    @Test
    @DisplayName("未配置 storage.type 时默认装配本地存储")
    void localIsDefaultStorage() {
        try (AnnotationConfigApplicationContext context = context(Map.of())) {
            assertInstanceOf(LocalAvatarStorage.class, context.getBean(AvatarStorage.class),
                    "缺省必须是本地存储，保证开箱即用");

            AvatarStorageProperties properties = context.getBean(AvatarStorageProperties.class);
            assertEquals("./uploads/avatars", properties.local().directory(), "本地目录应有可用缺省值");
            assertEquals("avatars", properties.oss().keyPrefix(), "OSS 前缀应有可用缺省值");
        }
    }

    @Test
    @DisplayName("storage.type=oss 时装配 OSS 存储，本地实现不再存在")
    void ossTypeSelectsOssStorage() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("storage.type", "oss");
        properties.put("storage.oss.endpoint", "https://oss-cn-hangzhou.aliyuncs.com");
        properties.put("storage.oss.bucket", "mindharbor-test");
        properties.put("storage.oss.access-key-id", "test-access-key");
        properties.put("storage.oss.access-key-secret", "test-access-secret");

        try (AnnotationConfigApplicationContext context = context(properties)) {
            assertInstanceOf(OssAvatarStorage.class, context.getBean(AvatarStorage.class));
            assertNotNull(context.getBean(OSS.class), "OSS 客户端应由容器管理并负责释放连接池");
            assertTrue(context.getBeansOfType(LocalAvatarStorage.class).isEmpty(),
                    "两种实现必须互斥，同时存在会让注入结果取决于 Bean 注册顺序");
        }
    }

    @Test
    @DisplayName("OSS 参数不全时启动即报错，并点明缺哪几项")
    void missingOssConfigFailsFast() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            try (AnnotationConfigApplicationContext context = context(Map.of("storage.type", "oss"))) {
                context.getBean(AvatarStorage.class);
            }
        });

        String message = rootMessage(exception);
        assertTrue(message.contains("对象存储配置缺失"),
                "配置不全必须启动即失败，不能退回本地存储后让人以为在写 OSS。实际：" + message);
        assertTrue(message.contains("storage.oss.endpoint") && message.contains("storage.oss.access-key-secret"),
                "应列出缺失项的完整配置名，实际：" + message);
    }

    @Test
    @DisplayName("storage.type 写成非法值时给出可读报错，而不是「找不到构造参数」")
    void unmatchedTypeIsRejectedBySentinel() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            try (AnnotationConfigApplicationContext context = context(Map.of("storage.type", "osss"))) {
                context.getBean(AvatarStorage.class);
            }
        });

        String message = rootMessage(exception);
        assertTrue(message.contains("仅支持 local 或 oss"),
                "应说明合法取值，暴露真实原因是拼写错误。实际：" + message);
        assertTrue(message.contains("osss"), "应回显非法取值便于排查，实际：" + message);
    }

    @Test
    @DisplayName("endpoint 推导不出 region 时启动即报错（V4 签名必需，不能等到首次请求才炸）")
    void unparseableEndpointRegionFailsFast() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("storage.type", "oss");
        properties.put("storage.oss.endpoint", "https://storage.example.com");
        properties.put("storage.oss.bucket", "mindharbor-test");
        properties.put("storage.oss.access-key-id", "test-access-key");
        properties.put("storage.oss.access-key-secret", "test-access-secret");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            try (AnnotationConfigApplicationContext context = context(properties)) {
                context.getBean(OSS.class);
            }
        });

        String message = rootMessage(exception);
        assertTrue(message.contains("region"),
                "应说明失败与 region 推导有关。实际：" + message);
        assertTrue(message.contains("https://storage.example.com"),
                "应回显当前 endpoint 便于定位。实际：" + message);
    }

    @Test
    @DisplayName("regionFromEndpoint：标准域名、内网域名取同一 region，非 OSS 域名报错")
    void regionFromEndpointCoversStandardAndInternal() {
        assertEquals("cn-hangzhou", AvatarStorageConfig.regionFromEndpoint("https://oss-cn-hangzhou.aliyuncs.com"));
        assertEquals("cn-hangzhou",
                AvatarStorageConfig.regionFromEndpoint("https://oss-cn-hangzhou-internal.aliyuncs.com"));
        assertEquals("cn-beijing", AvatarStorageConfig.regionFromEndpoint("oss-cn-beijing.aliyuncs.com"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> AvatarStorageConfig.regionFromEndpoint("https://storage.example.com"));
        assertTrue(exception.getMessage().contains("region"), "报错应点明 region 推导失败");
    }

    private static AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addLast(new MapPropertySource("test", properties));
        context.register(AvatarStorageConfig.class);
        context.refresh();
        return context;
    }

    /** Spring 会把 @Bean 里抛出的异常包成 BeanCreationException，需要取到根因消息才能断言。 */
    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? "" : message;
    }
}
