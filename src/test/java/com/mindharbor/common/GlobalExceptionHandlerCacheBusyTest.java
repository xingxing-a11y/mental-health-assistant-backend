package com.mindharbor.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mindharbor.common.cache.CacheRebuildInProgressException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerCacheBusyTest {

    @Test
    void rebuildInProgressReturns503WithRetryAfter() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response =
                handler.cacheRebuildInProgress(new CacheRebuildInProgressException("cache:key"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals("1", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertNotNull(response.getBody());
        assertEquals(503, response.getBody().code());
    }
}
