package com.mindharbor.common;

/** 并发冲突：资源被其它请求先行改动，统一映射为 HTTP 409 与业务 code 409。 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
