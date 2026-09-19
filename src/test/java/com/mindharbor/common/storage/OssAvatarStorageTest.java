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
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 对象存储的读写语义与错误码分类。
 *
 * <p>这里最要紧的一条是「哪些错误可以变成 404」。把配置故障误判成 404，会让线上问题
 * 表现成「用户头像显示不出来」这种正常现象，永远查不到根因，因此有单独的用例守住它。
 */
@DisplayName("对象存储（OSS）")
class OssAvatarStorageTest {

    private static final String BUCKET = "mindharbor-test-bucket";

    private static final String KEY_PREFIX = "avatars";

    @Test
    @DisplayName("保存带上配置的 bucket、前缀与内容元数据")
    void saveUploadsWithConfiguredBucketAndMetadata() {
        FakeOss oss = new FakeOss();
        AvatarStorage storage = new OssAvatarStorage(oss.proxy(), BUCKET, KEY_PREFIX);
        String filename = UUID.randomUUID() + ".jpg";
        byte[] payload = "fake-jpg-payload".getBytes(StandardCharsets.UTF_8);

        String url = storage.save(new ByteArrayInputStream(payload), payload.length, filename, "image/jpeg");

        assertEquals(AvatarStorage.URL_PREFIX + "/" + filename, url);
        assertEquals(BUCKET, oss.lastBucket(), "必须写入配置的 bucket");
        assertEquals(KEY_PREFIX + "/" + filename, oss.lastKey(), "对象键必须带配置的前缀");
        assertEquals("image/jpeg", oss.lastContentType());
        assertEquals(payload.length, oss.lastContentLength(),
                "内容长度必须与实测字节数一致，取值不符会导致对象损坏");
    }

    @Test
    @DisplayName("读取返回对象内容与元数据")
    void loadReadsContentAndMetadata() throws IOException {
        FakeOss oss = new FakeOss();
        AvatarStorage storage = new OssAvatarStorage(oss.proxy(), BUCKET, KEY_PREFIX);
        String filename = UUID.randomUUID() + ".webp";
        byte[] payload = "fake-webp-payload".getBytes(StandardCharsets.UTF_8);
        oss.put(KEY_PREFIX + "/" + filename, payload, "image/webp");

        AvatarContent content = storage.load(filename);
        try (InputStream stream = content.content()) {
            assertArrayEquals(payload, stream.readAllBytes());
        }
        assertEquals(payload.length, content.contentLength(), "长度应取对象元数据");
        assertEquals(MediaType.parseMediaType("image/webp"), content.contentType());
    }

    @Test
    @DisplayName("NoSuchKey 映射为「头像不存在」")
    void missingObjectBecomesNotFound() {
        FakeOss oss = new FakeOss();
        AvatarStorage storage = new OssAvatarStorage(oss.proxy(), BUCKET, KEY_PREFIX);
        oss.markMissing();

        assertThrows(ResourceNotFoundException.class,
                () -> storage.load(UUID.randomUUID() + ".png"));
    }

    @Test
    @DisplayName("AccessDenied 不得伪装成 404")
    void accessDeniedIsNotDisguisedAsNotFound() {
        FakeOss oss = new FakeOss();
        AvatarStorage storage = new OssAvatarStorage(oss.proxy(), BUCKET, KEY_PREFIX);
        oss.failGetWith("AccessDenied");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> storage.load(UUID.randomUUID() + ".png"));

        assertFalse(exception instanceof ResourceNotFoundException,
                "RAM 授权或 bucket 配置出错时返回 404，会让故障被当成正常业务分支永久静默");
        assertTrue(exception.getMessage().contains("RAM"),
                "报错应指向授权排查方向，实际：" + exception.getMessage());
    }

    @Test
    @DisplayName("超过 2MB 的对象被拒绝，且不算「不存在」")
    void oversizeObjectIsRejected() {
        FakeOss oss = new FakeOss();
        AvatarStorage storage = new OssAvatarStorage(oss.proxy(), BUCKET, KEY_PREFIX);
        String filename = UUID.randomUUID() + ".png";
        oss.put(KEY_PREFIX + "/" + filename, new byte[2 * 1024 * 1024 + 2], "image/png");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> storage.load(filename));

        assertFalse(exception instanceof ResourceNotFoundException,
                "对象超限是服务端数据异常，不是客户端「文件不存在」");
    }

    @Test
    @DisplayName("删除失败只记日志，不影响主流程")
    void deleteFailureIsSwallowed() {
        FakeOss oss = new FakeOss();
        AvatarStorage storage = new OssAvatarStorage(oss.proxy(), BUCKET, KEY_PREFIX);
        oss.markDeleteFailing();

        assertDoesNotThrow(() -> storage.delete(UUID.randomUUID() + ".png"),
                "旧头像清理失败不能影响新头像的可用性");
    }
}
