package com.mindharbor.common.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 计数流的契约测试。
 *
 * <p>这个类存在的意义只有一个：头像大小上限必须无法被绕过。计数流是唯一的把关点，
 * 而它有三个容易被漏掉的行为——批量读取、skip()、mark()/reset()——任何一个漏掉都能
 * 让客户端在正常上传路径之外写入超大文件。这些路径不走常规流程，不测就发现不了。
 */
@DisplayName("计数流：头像大小上限的唯一把关点")
class CountingInputStreamTest {

    @Test
    @DisplayName("单字节与批量读取都计入累计字节数")
    void countsSingleAndBatchReads() throws IOException {
        byte[] data = "0123456789".getBytes(StandardCharsets.UTF_8);
        try (CountingInputStream stream =
                     new CountingInputStream(new ByteArrayInputStream(data), 1024)) {
            assertEquals(0, stream.getCount(), "初始计数应为 0");

            int first = stream.read();
            assertEquals('0', first, "单字节读取应返回首字节");
            assertEquals(1, stream.getCount(), "单字节读取后计数应为 1");

            byte[] buffer = new byte[4];
            int read = stream.read(buffer, 0, 4);
            assertEquals(4, read, "批量读取应返回实际读取长度");
            assertEquals(5, stream.getCount(), "批量读取后计数应为 5");
            assertEquals("1234", new String(buffer, StandardCharsets.UTF_8), "批量读取内容应连续");
        }
    }

    @Test
    @DisplayName("读取超过上限时抛出 IOException，且报错信息可读")
    void rejectsOversizeRead() {
        byte[] data = new byte[64];
        try (CountingInputStream stream =
                     new CountingInputStream(new ByteArrayInputStream(data), 16)) {
            IOException exception = assertThrows(IOException.class, stream::readAllBytes,
                    "超限读取必须抛出 IOException，否则 2MB 上限形同虚设");
            assertTrue(exception.getMessage().contains("超过大小限制"),
                    "报错信息应说明是超过大小限制，实际：" + exception.getMessage());
        } catch (IOException exception) {
            throw new AssertionError("关闭计数流不应抛异常", exception);
        }
    }

    @Test
    @DisplayName("skip() 超过上限被拒绝")
    void skipBeyondLimitIsRejected() {
        byte[] data = new byte[64];
        try (CountingInputStream stream =
                     new CountingInputStream(new ByteArrayInputStream(data), 16)) {
            assertThrows(IOException.class, () -> stream.skip(64),
                    "skip 必须计入累计字节数，否则跳过流即可绕过上限");
        } catch (IOException exception) {
            throw new AssertionError("关闭计数流不应抛异常", exception);
        }
    }

    @Test
    @DisplayName("未超限的 skip() 正常计入累计字节数")
    void skipWithinLimitIsCounted() throws IOException {
        byte[] data = new byte[64];
        try (CountingInputStream stream =
                     new CountingInputStream(new ByteArrayInputStream(data), 100)) {
            long skipped = stream.skip(10);
            assertEquals(10, skipped, "应跳过 10 字节");
            assertEquals(10, stream.getCount(), "跳过的字节必须计入累计数");
        }
    }

    @Test
    @DisplayName("不支持 mark，且 reset() 被拒绝")
    void markIsNotSupportedAndResetIsRejected() throws IOException {
        try (CountingInputStream stream =
                     new CountingInputStream(new ByteArrayInputStream(new byte[8]), 1024)) {
            assertFalse(stream.markSupported(), "计数流不应支持 mark");

            stream.mark(4);
            assertEquals(0, stream.getCount(), "mark 后不应产生未计数的回退");

            assertThrows(IOException.class, stream::reset,
                    "reset 会回退流位置却不减少计数，必须拒绝");
        }
    }
}
