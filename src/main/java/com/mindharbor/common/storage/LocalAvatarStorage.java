package com.mindharbor.common.storage;

import com.mindharbor.common.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

/** 本地磁盘存储：头像写入配置目录，适用于本地开发与单机部署。 */
public class LocalAvatarStorage implements AvatarStorage {

    private final Path directory;

    public LocalAvatarStorage(String directory) {
        this.directory = Paths.get(directory).toAbsolutePath().normalize();
    }

    @Override
    public String save(InputStream content, long contentLength, String filename, String contentType) {
        Path target = resolve(filename);
        try {
            Files.createDirectories(directory);
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("头像保存失败", exception);
        }
        return URL_PREFIX + "/" + filename;
    }

    @Override
    public void delete(String filename) {
        try {
            Files.deleteIfExists(resolve(filename));
        } catch (IllegalArgumentException | IOException exception) {
            // 旧头像清理失败不影响新头像使用。
        }
    }

    @Override
    public AvatarContent load(String filename) {
        Path target;
        try {
            target = resolve(filename);
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException("头像不存在");
        }

        if (!Files.isRegularFile(target)) {
            throw new ResourceNotFoundException("头像不存在");
        }

        try {
            return new AvatarContent(
                    Files.newInputStream(target),
                    Files.size(target),
                    contentMediaType(filename));
        } catch (IOException exception) {
            throw new IllegalStateException("头像读取失败", exception);
        }
    }

    /** 只允许访问存储目录内的文件，避免请求参数拼接出目录穿越路径。 */
    private Path resolve(String filename) {
        Path target = directory.resolve(filename).normalize();
        if (!directory.equals(target.getParent())) {
            throw new IllegalArgumentException("头像文件路径无效");
        }
        return target;
    }

    private MediaType contentMediaType(String filename) {
        return MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }
}
