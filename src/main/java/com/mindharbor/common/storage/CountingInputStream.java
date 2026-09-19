package com.mindharbor.common.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 只统计已读字节数、不缓存内容的包装流，用于在把数据交给存储层之前确认真实长度。
 *
 * <p>必须同时重写 {@code read} 与 {@code skip}：{@link FilterInputStream#skip(long)} 的默认实现
 * 直接委托底层流、不经过 {@code read}，只重写 {@code read} 会让被跳过的字节不计入总数，
 * 上限校验形同虚设。同时不提供标记回退能力（{@code markSupported} 为 false），
 * 避免用 reset 把同一段字节重复计数。
 */
public class CountingInputStream extends FilterInputStream {

    private final long limit;

    private long count;

    public CountingInputStream(InputStream source, long limit) {
        super(source);
        this.limit = limit;
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value != -1) {
            addCount(1);
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = super.read(buffer, offset, length);
        if (read > 0) {
            addCount(read);
        }
        return read;
    }

    @Override
    public long skip(long requested) throws IOException {
        long skipped = super.skip(requested);
        if (skipped > 0) {
            addCount(skipped);
        }
        return skipped;
    }

    /** 不允许用标记回退，否则同一段字节可能被重复计数或绕过计数。 */
    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public synchronized void mark(int readLimit) {
        // 不支持标记，保持与 markSupported 返回 false 一致。
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("CountingInputStream 不支持 reset");
    }

    /** 已读字节总数。 */
    public long getCount() {
        return count;
    }

    private void addCount(long delta) throws IOException {
        count += delta;
        if (count > limit) {
            throw new IOException("头像文件超过大小限制");
        }
    }
}
