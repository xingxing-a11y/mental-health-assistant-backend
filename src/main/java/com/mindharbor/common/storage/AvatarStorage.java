package com.mindharbor.common.storage;

import com.mindharbor.common.ResourceNotFoundException;
import java.io.InputStream;

/**
 * 头像文件存储端口：本地磁盘与云对象存储提供同一套读写语义。
 * 具体实现由配置项 storage.type 决定，业务代码只依赖本接口。
 */
public interface AvatarStorage {

    /** 头像对外访问前缀，请求映射与 users.avatar_url 共用同一常量，避免两处漂移。 */
    String URL_PREFIX = "/uploads/avatars";

    /**
     * 保存头像文件，输入流由调用方负责关闭。
     *
     * <p>contentLength 必须是输入流的真实字节数：对象存储需要据此设置请求长度，取值与实际不符
     * 会导致对象损坏。本地实现不依赖该值，但部分实现要求调用方**在写入前**就已知长度，
     * 因此调用方应先把流读完并计数，再连同已知长度一起传入。
     *
     * @param contentLength 文件字节数，须为实测值
     * @return 可直接写入 users.avatar_url 的相对访问路径
     */
    String save(InputStream content, long contentLength, String filename, String contentType);

    /** 删除头像文件；文件不存在或删除失败都不影响主流程。 */
    void delete(String filename);

    /** 读取头像文件，不存在时抛出 {@link ResourceNotFoundException}。 */
    AvatarContent load(String filename);
}
