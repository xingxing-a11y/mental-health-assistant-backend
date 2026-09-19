package com.mindharbor.common.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * 用动态代理模拟 OSS 服务端，只实现头像用到的 putObject / getObject / deleteObject。
 *
 * <p>有了它，OSS 相关测试不需要真实凭证、不产生任何网络请求，离线也能跑；
 * 同时可以把服务端错误码（NoSuchKey、AccessDenied 等）精确注入到指定分支上，
 * 这是用真实 bucket 做集成测试反而难以稳定复现的。
 */
final class FakeOss implements InvocationHandler {

    private final Map<String, byte[]> objects = new HashMap<>();

    private final Map<String, String> contentTypes = new HashMap<>();

    private String lastKey;

    private String lastBucket;

    private String lastContentType;

    private long lastContentLength;

    private boolean missing;

    private boolean failDelete;

    private String serviceErrorCode;

    OSS proxy() {
        return (OSS) Proxy.newProxyInstance(
                FakeOss.class.getClassLoader(),
                new Class<?>[] {OSS.class},
                this);
    }

    void put(String key, byte[] data, String contentType) {
        objects.put(key, data);
        contentTypes.put(key, contentType);
    }

    String lastKey() {
        return lastKey;
    }

    String lastBucket() {
        return lastBucket;
    }

    String lastContentType() {
        return lastContentType;
    }

    long lastContentLength() {
        return lastContentLength;
    }

    /** 让后续 getObject 一律抛 NoSuchKey。 */
    void markMissing() {
        this.missing = true;
    }

    /** 让后续 getObject 抛出指定错误码的服务端异常。 */
    void failGetWith(String errorCode) {
        this.serviceErrorCode = errorCode;
    }

    /** 让后续 deleteObject 抛 AccessDenied。 */
    void markDeleteFailing() {
        this.failDelete = true;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "putObject":
                return handlePut((PutObjectRequest) args[0]);
            case "getObject":
                return handleGet((String) args[0], (String) args[1]);
            case "deleteObject":
                return handleDelete((String) args[0], (String) args[1]);
            case "shutdown":
                return null;
            default:
                throw new UnsupportedOperationException(method.getName());
        }
    }

    private Object handlePut(PutObjectRequest request) {
        lastBucket = request.getBucketName();
        lastKey = request.getKey();
        ObjectMetadata metadata = request.getMetadata();
        lastContentType = metadata == null ? null : metadata.getContentType();
        lastContentLength = metadata == null ? -1 : metadata.getContentLength();
        return null;
    }

    private Object handleGet(String bucket, String key) {
        lastBucket = bucket;
        lastKey = key;
        if (serviceErrorCode != null) {
            throw ossException(serviceErrorCode);
        }
        if (missing || !objects.containsKey(key)) {
            throw ossException("NoSuchKey");
        }
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(objects.get(key).length);
        metadata.setContentType(contentTypes.get(key));
        OSSObject object = new OSSObject();
        object.setBucketName(bucket);
        object.setKey(key);
        object.setObjectMetadata(metadata);
        object.setObjectContent(new ByteArrayInputStream(objects.get(key)));
        return object;
    }

    private Object handleDelete(String bucket, String key) {
        lastBucket = bucket;
        lastKey = key;
        if (failDelete) {
            throw ossException("AccessDenied");
        }
        objects.remove(key);
        return null;
    }

    /**
     * 构造带指定错误码的 OSSException。
     *
     * <p>该异常有多个 7 参数构造器，参数顺序无法从字节码确知，因此逐个试到错误码对上为止——
     * 直接写死最可能的那种会在 SDK 升级后静默构造出错误码不匹配的异常，让测试失去意义。
     */
    private static OSSException ossException(String errorCode) {
        String message = "模拟服务端错误";
        OSSException[] candidates = {
                new OSSException(errorCode, message, "request-id", "host-id", "404", "", ""),
                new OSSException(message, errorCode, "request-id", "host-id", "404", "", "")
        };
        for (OSSException candidate : candidates) {
            if (errorCode.equals(candidate.getErrorCode())) {
                return candidate;
            }
        }
        throw new IllegalStateException("无法构造错误码为 " + errorCode + " 的 OSSException");
    }
}
