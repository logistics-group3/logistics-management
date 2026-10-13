package com.sparta.logistics.common.exception;

import com.sparta.logistics.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리 (MVC 서비스 전용). CommonWebAutoConfiguration 이 빈으로 등록한다.
 * 서비스에서 특정 예외만 따로 처리하고 싶으면 서비스 쪽 @RestControllerAdvice 를 추가하면 된다 (이 핸들러는 가장 마지막 순서).
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("[{}] {}", code.getCode(), e.getMessage(), e);
        } else {
            log.debug("[{}] {}", code.getCode(), e.getMessage());
        }
        return toResponse(ErrorResponse.of(code, e.getMessage(), request.getRequestURI()));
    }

    /** @Valid @RequestBody 검증 실패 → 400 VALIDATION_ERROR + errors */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return toResponse(ErrorResponse.ofValidation(CommonErrorCode.VALIDATION_ERROR, request.getRequestURI(), errors));
    }

    /** @PathVariable/@RequestParam 에 붙인 제약 조건 검증 실패 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(err -> new ErrorResponse.FieldError(
                                result.getMethodParameter().getParameterName(), err.getDefaultMessage())))
                .toList();
        return toResponse(ErrorResponse.ofValidation(CommonErrorCode.VALIDATION_ERROR, request.getRequestURI(), errors));
    }

    /** JSON 파싱 실패, Enum 값 바인딩 실패 등 */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest request) {
        log.debug("Bad request: {}", e.getMessage());
        return toResponse(ErrorResponse.of(CommonErrorCode.VALIDATION_ERROR, request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        return toResponse(ErrorResponse.of(CommonErrorCode.NOT_FOUND, request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                                  HttpServletRequest request) {
        return toResponse(ErrorResponse.of(CommonErrorCode.METHOD_NOT_ALLOWED, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        // 415, 406 등 Spring MVC 가 상태 코드를 알고 있는 예외는 그 상태 코드를 그대로 쓴다
        if (e instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            int status = springError.getStatusCode().value();
            log.debug("Client error {} at {}: {}", status, request.getRequestURI(), e.getMessage());
            ErrorResponse body = new ErrorResponse(status, CommonErrorCode.INVALID_REQUEST.getCode(),
                    CommonErrorCode.INVALID_REQUEST.getMessage(), request.getRequestURI(), LocalDateTime.now(), null);
            return toResponse(body);
        }
        log.error("Unhandled exception at {}", request.getRequestURI(), e);
        return toResponse(ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI()));
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorResponse body) {
        return ResponseEntity.status(body.status()).body(body);
    }
}
