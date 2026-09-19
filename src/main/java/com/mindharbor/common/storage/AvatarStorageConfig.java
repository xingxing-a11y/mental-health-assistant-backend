package com.mindharbor.common.storage;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.comm.SignVersion;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 头像存储装配：storage.type=local 写本地磁盘（默认），storage.type=oss 写阿里云 OSS。
 * 两种实现互斥，业务代码只依赖 {@link AvatarStorage}，切换存储不需要改动业务。
 */
@Configuration
@EnableConfigurationProperties(AvatarStorageProperties.class)
public class AvatarStorageConfig {

    /** 头像最大 2MB，正常往返在百毫秒级，超时收到秒级即可，避免慢请求占住 Tomcat 线程。 */
    private static final int CONNECTION_TIMEOUT_MILLIS = 5000;

    private static final int SOCKET_TIMEOUT_MILLIS = 10000;

    private static final int REQUEST_TIMEOUT_MILLIS = 15000;

    /** 单机部署的头像读并发远小于此值，超过则说明上游出了问题，宁可快速失败。 */
    private static final int MAX_CONNECTIONS = 64;

    private static final long CONNECTION_TTL_MILLIS = 60000L;

    @Bean
    @ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "local", matchIfMissing = true)
    public AvatarStorage localAvatarStorage(AvatarStorageProperties properties) {
        return new LocalAvatarStorage(properties.local().directory());
    }

    /** OSS 客户端线程安全，全局共用一个实例，容器关闭时释放连接池。 */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "oss")
    public OSS ossClient(AvatarStorageProperties properties) {
        AvatarStorageProperties.Oss oss = properties.oss();
        requireConfigured(oss);
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
        configuration.setSocketTimeout(SOCKET_TIMEOUT_MILLIS);
        configuration.setRequestTimeout(REQUEST_TIMEOUT_MILLIS);
        configuration.setMaxConnections(MAX_CONNECTIONS);
        configuration.setConnectionTTL(CONNECTION_TTL_MILLIS);
        configuration.setSignatureVersion(SignVersion.V4);
        return new OSSClientBuilder()
                .build(oss.endpoint(), oss.accessKeyId(), oss.accessKeySecret(), configuration);
    }

    @Bean
    @ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "oss")
    public AvatarStorage ossAvatarStorage(OSS ossClient, AvatarStorageProperties properties) {
        AvatarStorageProperties.Oss oss = properties.oss();
        return new OssAvatarStorage(ossClient, oss.bucket(), oss.keyPrefix());
    }

    /**
     * storage.type 写成其它值（大小写不符、拼错）时两个条件装配都不命中，
     * 容器里会没有 AvatarStorage，报错变成「找不到构造参数」而看不出真实原因，因此这里给出明确提示。
     */
    @Bean
    @ConditionalOnMissingBean(AvatarStorage.class)
    public AvatarStorage unmatchedStorageTypeSentinel(AvatarStorageProperties properties) {
        throw new IllegalStateException(
                "storage.type 仅支持 local 或 oss，当前值：" + properties.type());
    }

    /** 配置缺失时立即报错，避免用空值发起请求后得到难以定位的失败。 */
    private void requireConfigured(AvatarStorageProperties.Oss oss) {
        List<String> missing = new ArrayList<>();
        addIfBlank(missing, oss.endpoint(), "storage.oss.endpoint");
        addIfBlank(missing, oss.bucket(), "storage.oss.bucket");
        addIfBlank(missing, oss.accessKeyId(), "storage.oss.access-key-id");
        addIfBlank(missing, oss.accessKeySecret(), "storage.oss.access-key-secret");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("对象存储配置缺失：" + String.join("、", missing));
        }
    }

    private void addIfBlank(List<String> missing, String value, String propertyName) {
        if (value == null || value.isBlank()) {
            missing.add(propertyName);
        }
    }
}
