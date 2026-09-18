package com.mindharbor.common;

/** 统一响应结构：HTTP 状态码与业务 code 分离。 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "OK", data);
    }
}
