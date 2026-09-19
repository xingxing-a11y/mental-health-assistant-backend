package com.mindharbor.common.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.common.ResourceNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * 本地磁盘存储的读写语义，以及目录穿越防护。
 *
 * <p>临时目录用字段注入而非方法参数：在 {@code @ParameterizedTest} 里把 {@code @TempDir}
 * 写成方法参数，会与参数化测试自带的 ParameterResolver 争夺同一个参数位而直接报错。
 */
@DisplayName("本地磁盘存储")
class LocalAvatarStorageTest {

    private static final byte[] PAYLOAD = "fake-png-payload".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    @DisplayName("保存后真实落盘，并返回与读取接口一致的相对路径")
    void saveWritesFileAndReturnsRelativeUrl() {
        String filename = UUID.randomUUID() + ".png";

        String url = storage().save(
                new ByteArrayInputStream(PAYLOAD), PAYLOAD.length, filename, "image/png");

        assertEquals(AvatarStorage.URL_PREFIX + "/" + filename, url,
                "返回值要能直接写进 users.avatar_url，两种存储必须完全一致");
        assertTrue(Files.isRegularFile(directory.resolve(filename)), "文件应真实写入配置目录");
    }

    @Test
    @DisplayName("读取返回与写入一致的字节、长度与内容类型")
    void loadReturnsSameContentAndMetadata() throws IOException {
        String filename = UUID.randomUUID() + ".png";
        storage().save(new ByteArrayInputStream(PAYLOAD), PAYLOAD.length, filename, "image/png");

        AvatarContent content = storage().load(filename);
        try (InputStream stream = content.content()) {
            assertArrayEquals(PAYLOAD, stream.readAllBytes(), "读取内容应与写入字节完全相同");
        }
        assertEquals(PAYLOAD.length, content.contentLength());
        assertEquals(MediaType.IMAGE_PNG, content.contentType(), "内容类型应按扩展名推断");
    }

    @Test
    @DisplayName("删除生效，且重复删除不抛异常")
    void deleteIsIdempotent() {
        String filename = UUID.randomUUID() + ".png";
        AvatarStorage storage = storage();
        storage.save(new ByteArrayInputStream(PAYLOAD), PAYLOAD.length, filename, "image/png");

        storage.delete(filename);

        assertFalse(Files.exists(directory.resolve(filename)), "删除后文件应不存在");
        assertDoesNotThrow(() -> storage.delete(filename), "重复删除必须静默通过，不能影响注销流程");
    }

    @ParameterizedTest(name = "拒绝文件名 {0}")
    @ValueSource(strings = {"../../../windows/win.ini", "sub/other.png"})
    @DisplayName("越出存储目录的文件名被拦截，且不泄露文件是否存在")
    void traversalIsRejected(String filename) {
        assertThrows(ResourceNotFoundException.class, () -> storage().load(filename),
                "目录穿越必须按「头像不存在」处理，返回其它错误码会被用来探测存储布局");
    }

    @Test
    @DisplayName("不存在的头像按「不存在」处理")
    void missingAvatarIsNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> storage().load(UUID.randomUUID() + ".jpg"));
    }

    private AvatarStorage storage() {
        return new LocalAvatarStorage(directory.toString());
    }
}
