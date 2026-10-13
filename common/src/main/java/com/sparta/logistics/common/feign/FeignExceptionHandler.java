package com.sparta.logistics.common.feign;

import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.response.ErrorResponse;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 연결 실패·타임아웃(RetryableException 등) → 503 SERVICE_UNAVAILABLE */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FeignExceptionHandler {

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeign(FeignException e, HttpServletRequest request) {
        log.error("Feign exception: {}", e.getMessage());
        ErrorResponse body = ErrorResponse.of(CommonErrorCode.SERVICE_UNAVAILABLE, request.getRequestURI());
        return ResponseEntity.status(body.status()).body(body);
    }
}
