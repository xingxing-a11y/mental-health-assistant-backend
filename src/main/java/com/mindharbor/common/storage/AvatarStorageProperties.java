package com.mindharbor.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 头像存储参数，storage.type 决定装配本地磁盘还是对象存储实现。 */
@ConfigurationProperties(prefix = "storage")
public record AvatarStorageProperties(String type, Local local, Oss oss) {

    public AvatarStorageProperties {
        // 配置块缺失时给出空对象，让缺失项由各自的校验统一报错，而不是抛空指针。
        local = local == null ? new Local(null) : local;
        oss = oss == null ? new Oss(null, null, null, null, null) : oss;
    }

    /** 本地磁盘存储参数，相对路径按后端进程启动目录解析。 */
    public record Local(String directory) {
        public Local {
            directory = directory == null || directory.isBlank() ? "./uploads/avatars" : directory.trim();
        }
    }

    /** 阿里云 OSS 参数；密钥只从环境变量注入，不落到代码仓库里。 */
    public record Oss(
            String endpoint,
            String bucket,
            String accessKeyId,
            String accessKeySecret,
            String keyPrefix) {
        public Oss {
            String normalized = keyPrefix == null ? "" : keyPrefix.strip().replaceAll("^/+|/+$", "");
            keyPrefix = normalized.isEmpty() ? "avatars" : normalized;
        }
    }
}
