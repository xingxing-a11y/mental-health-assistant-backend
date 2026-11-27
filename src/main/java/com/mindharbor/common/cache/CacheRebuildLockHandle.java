package com.mindharbor.common.cache;

/**
 * 一次缓存重建锁的获取结果。
 *
 * <p>无论 {@link #isAcquired()} 是 true 还是 false，调用方都必须调用 {@link #release()}；
 * 实现必须保证 {@code release()} 幂等，避免 finally 与异常路径重复释放。
 */
public interface CacheRebuildLockHandle {

    /** 是否真正拿到了锁；false 表示锁被其他实例持有，调用方必须降级，不能回源。 */
    boolean isAcquired();

    /** 释放本次获取尝试；同一句柄重复调用只生效一次。 */
    void release();
}
