package com.mindharbor.common.storage;

import java.io.InputStream;
import org.springframework.http.MediaType;

/**
 * 头像文件内容。
 * 输入流由响应写出方负责关闭，存储实现不持有它。
 */
public record AvatarContent(InputStream content, long contentLength, MediaType contentType) {
}
