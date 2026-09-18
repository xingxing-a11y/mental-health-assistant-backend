package com.mindharbor.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis 缓存参数，TTL 由配置控制，具体缓存键由业务服务决定。 */
@ConfigurationProperties(prefix = "cache")
public record CacheProperties(
        ArticleEntry article,
        Entry announcement,
        Entry sensitiveWord,
        RebuildLock rebuildLock) {

    /** 文章缓存参数：详情查询不到数据时会额外写入短 TTL 空缓存，因此多两个参数。 */
    public record ArticleEntry(int ttlSeconds, int jitterSeconds, int missingTtlSeconds,
                               int missingJitterSeconds) {
        public ArticleEntry {
            ttlSeconds = Math.max(ttlSeconds, 1);
            jitterSeconds = Math.max(jitterSeconds, 0);
            missingTtlSeconds = Math.max(missingTtlSeconds, 1);
            missingJitterSeconds = Math.max(missingJitterSeconds, 0);
        }
    }

    /** 列表类缓存参数：公告与敏感词列表不写空缓存，只保留 TTL 与抖动。 */
    public record Entry(int ttlSeconds, int jitterSeconds) {
        public Entry {
            ttlSeconds = Math.max(ttlSeconds, 1);
            jitterSeconds = Math.max(jitterSeconds, 0);
        }
    }

    public record RebuildLock(long waitMillis) {
        public RebuildLock {
            waitMillis = Math.max(waitMillis, 1);
        }
    }
}
