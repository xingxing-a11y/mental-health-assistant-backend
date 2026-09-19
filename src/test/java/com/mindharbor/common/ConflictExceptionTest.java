package com.mindharbor.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 条件更新未命中时用于表达「同一资源被并发修改」的异常，以及它在响应里的落地方式。 */
@DisplayName("并发冲突异常")
class ConflictExceptionTest {

    @Test
    @DisplayName("是运行时异常，并保留原始提示")
    void carriesMessage() {
        ConflictException exception = new ConflictException("头像已被其它请求修改");

        assertTrue(exception instanceof RuntimeException, "必须是运行时异常，否则会污染方法签名");
        assertEquals("头像已被其它请求修改", exception.getMessage());
    }

    @Test
    @DisplayName("映射为 409，且业务 code 与 HTTP 状态一致")
    void mapsToConflict() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response =
                handler.conflict(new ConflictException("头像已被其它请求修改"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody(), "409 的响应体不能为空");
        assertEquals(409, response.getBody().code(), "HTTP 状态码与业务 code 必须一致");
    }
}
