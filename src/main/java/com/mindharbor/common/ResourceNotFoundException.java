package com.mindharbor.common;

/** 请求的资源不存在，统一映射为 HTTP 404 与业务 code 404。 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
