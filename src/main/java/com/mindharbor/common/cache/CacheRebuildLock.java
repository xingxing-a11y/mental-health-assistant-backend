package com.mindharbor.common.cache;

/**
 * 缓存重建锁的分布式实现入口。
 *
 * <p>返回 {@code null} 表示分布式锁不可用（未启用或 Redis 异常），调用方应降级到本地锁；
 * 返回句柄但 {@link CacheRebuildLockHandle#isAcquired()} 为 false 表示锁被其他实例持有，
 * 调用方必须降级请求，不能回源数据库。两者语义不同，不能合并处理。
 */
public interface CacheRebuildLock {

    /**
     * 尝试获取某个缓存 key 的重建锁。
     *
     * @param cacheKey   缓存键（实现内部会加锁前缀）
     * @param waitMillis 最长等待毫秒数
     * @return 锁句柄；{@code null} 表示分布式锁不可用，调用方走本地锁
     * @throws InterruptedException 等待期间被中断
     */
    CacheRebuildLockHandle tryAcquire(String cacheKey, long waitMillis) throws InterruptedException;
}
