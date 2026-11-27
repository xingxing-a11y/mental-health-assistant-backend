package com.mindharbor.common.cache;

/** 同一个缓存 key 正在被其他请求重建，本次请求降级，不回源数据库。 */
public class CacheRebuildInProgressException extends RuntimeException {

    public CacheRebuildInProgressException(String cacheKey) {
        super("cache rebuild in progress: " + cacheKey);
    }
}
