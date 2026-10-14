package com.sparta.logistics.common.feign;

import com.sparta.logistics.common.exception.ErrorCode;
import com.sparta.logistics.common.response.ErrorResponse;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 연결 실패·타임아웃(ErrorDecoder까지 오지 못한 Feign 예외)을 503으로 통일한다. */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FeignExceptionHandler {

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeign(FeignException e, HttpServletRequest request) {
        log.error("Feign call failed. path={}", request.getRequestURI(), e);
        ErrorResponse body = ErrorResponse.of(ErrorCode.SERVICE_UNAVAILABLE,
                ErrorCode.SERVICE_UNAVAILABLE.getMessage(), request.getRequestURI());
        return ResponseEntity.status(body.status()).body(body);
    }
}
