package com.mindharbor.common.storage;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.mindharbor.common.ResourceNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

/**
 * 阿里云 OSS 存储。
 * bucket 保持私有，头像由后端读取后转发，因此数据库与前端仍只认 /uploads/avatars/&lt;文件名&gt; 相对路径。
 */
public class OssAvatarStorage implements AvatarStorage {

    private static final Logger log = LoggerFactory.getLogger(OssAvatarStorage.class);

    /** 头像上限 2MB，读取时多取 1 字节用于判断对象是否被绕过上传放大。 */
    private static final int MAX_CONTENT_BYTES = 2 * 1024 * 1024 + 1;

    private static final String NO_SUCH_KEY = "NoSuchKey";

    private static final String ACCESS_DENIED = "AccessDenied";

    private final OSS client;
    private final String bucket;
    private final String keyPrefix;

    public OssAvatarStorage(OSS client, String bucket, String keyPrefix) {
        this.client = client;
        this.bucket = bucket;
        this.keyPrefix = keyPrefix + "/";
    }

    @Override
    public String save(InputStream content, long contentLength, String filename, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentLength);
        metadata.setContentType(contentType);
        try {
            client.putObject(new PutObjectRequest(bucket, key(filename), content, metadata));
        } catch (OSSException | ClientException exception) {
            log.error("头像写入对象存储失败，filename={}，errorCode={}，requestId={}",
                    filename, errorCodeOf(exception), requestIdOf(exception), exception);
            throw new IllegalStateException("头像保存失败", exception);
        }
        return URL_PREFIX + "/" + filename;
    }

    @Override
    public void delete(String filename) {
        try {
            client.deleteObject(bucket, key(filename));
        } catch (OSSException | ClientException exception) {
            // 旧头像清理失败不影响新头像使用，但必须留下可回溯的记录。
            log.warn("删除 OSS 头像失败，filename={}，errorCode={}，requestId={}",
                    filename, errorCodeOf(exception), requestIdOf(exception), exception);
        }
    }

    /** 两类异常都提供 errorCode，但只有 OSSException 的 errorCode 代表服务端返回的错误。 */
    private String errorCodeOf(Exception exception) {
        if (exception instanceof OSSException ossException) {
            return ossException.getErrorCode();
        }
        return ((ClientException) exception).getErrorCode();
    }

    private String requestIdOf(Exception exception) {
        if (exception instanceof OSSException ossException) {
            return ossException.getRequestId();
        }
        return ((ClientException) exception).getRequestId();
    }

    @Override
    public AvatarContent load(String filename) {
        // 头像最大 2MB，一次读入内存可避免响应流中断时连接被悬挂，也省去一次取元数据的请求。
        try (OSSObject object = client.getObject(bucket, key(filename))) {
            byte[] bytes = readAllBytes(object.getObjectContent());
            return new AvatarContent(
                    new ByteArrayInputStream(bytes),
                    bytes.length,
                    resolveContentType(object.getObjectMetadata(), filename));
        } catch (OSSException exception) {
            throw translate(exception, filename);
        } catch (ClientException exception) {
            // 本分支只代表请求未获服务端确认（参数非法、网络/SSL 等本地问题），
            // errorCode 不表示「对象不存在」，因此一律 500，不得据此返回 404。
            log.error("头像读取失败（客户端异常），filename={}，errorCode={}，requestId={}",
                    filename, exception.getErrorCode(), exception.getRequestId(), exception);
            throw new IllegalStateException("头像读取失败", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("头像读取失败", exception);
        }
    }

    /**
     * OSS 服务端返回的错误按错误码分类：只有 NoSuchKey 是「头像不存在」的客户端语义，
     * 其余（AccessDenied、NoSuchBucket、SignatureDoesNotMatch 等）都是配置或环境故障，
     * 必须走 500，否则会被当成正常业务分支而永久静默。错误码与 requestId 只进日志，不透给客户端。
     */
    private IllegalStateException translate(OSSException exception, String filename) {
        String errorCode = exception.getErrorCode();
        if (NO_SUCH_KEY.equals(errorCode)) {
            throw new ResourceNotFoundException("头像不存在");
        }
        if (ACCESS_DENIED.equals(errorCode)) {
            log.error("对象存储拒绝访问，请检查 RAM 授权，filename={}，errorCode={}，requestId={}",
                    filename, errorCode, exception.getRequestId(), exception);
            return new IllegalStateException("对象存储拒绝访问，请检查 RAM 授权", exception);
        }
        log.error("头像读取失败（OSS 错误），filename={}，errorCode={}，requestId={}",
                filename, errorCode, exception.getRequestId(), exception);
        return new IllegalStateException("头像读取失败", exception);
    }

    private byte[] readAllBytes(InputStream content) throws IOException {
        byte[] bytes = content.readNBytes(MAX_CONTENT_BYTES);
        if (bytes.length >= MAX_CONTENT_BYTES) {
            throw new IllegalStateException("头像文件超过大小限制");
        }
        return bytes;
    }

    private MediaType resolveContentType(ObjectMetadata metadata, String filename) {
        String contentType = metadata.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (InvalidMediaTypeException exception) {
                // 对象元数据异常时退回按扩展名推断。
            }
        }
        return MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    private String key(String filename) {
        return keyPrefix + filename;
    }
}
