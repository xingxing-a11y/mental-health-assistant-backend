package com.mindharbor.common;

/** 资源密集型操作并发超限，提示稍后重试（429）。 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
