package com.sparta.logistics.common.exception;

import com.sparta.logistics.common.exception.ErrorResponse.FieldErrorDetail;
import feign.FeignException;
import feign.RetryableException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
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
 * 모든 예외를 SA 6.1 에러 응답 형식({@link ErrorResponse})으로 변환한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.warn("BusinessException {} {}: {}", errorCode.getCode(), request.getRequestURI(), e.getMessage());
        }
        return respond(ErrorResponse.of(errorCode, e.getMessage(), request.getRequestURI()));
    }

    /** @Valid @RequestBody 검증 실패 → 400 VALIDATION_ERROR + errors[] */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e,
                                                          HttpServletRequest request) {
        List<FieldErrorDetail> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(ErrorResponse.validation(errors, request.getRequestURI()));
    }

    /** @RequestParam·@PathVariable 검증 실패 (@Validated 없이 Spring 6.1 메서드 검증) */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e,
                                                                HttpServletRequest request) {
        List<FieldErrorDetail> errors = e.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorDetail(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return respond(ErrorResponse.validation(errors, request.getRequestURI()));
    }

    /** JSON 파싱 실패, Enum 값 오류, 타입 불일치, 필수 파라미터 누락, 허용되지 않은 정렬 필드 */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            PropertyReferenceException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest request) {
        return respond(ErrorResponse.validation(List.of(), request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpServletRequest request) {
        return respond(ErrorResponse.of(CommonErrorCode.METHOD_NOT_ALLOWED, request.getRequestURI()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpServletRequest request) {
        return respond(ErrorResponse.of(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(HttpServletRequest request) {
        return respond(ErrorResponse.of(CommonErrorCode.RESOURCE_NOT_FOUND, request.getRequestURI()));
    }

    /** Feign 타임아웃·연결 실패(상대 서비스 다운), ErrorDecoder를 거치지 않은 Feign 오류 → 503 */
    @ExceptionHandler({RetryableException.class, FeignException.class})
    public ResponseEntity<ErrorResponse> handleFeign(FeignException e, HttpServletRequest request) {
        log.warn("서비스 간 호출 실패 {}: {}", request.getRequestURI(), e.getMessage());
        return respond(ErrorResponse.of(CommonErrorCode.SERVICE_UNAVAILABLE, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unexpected error {}", request.getRequestURI(), e);
        return respond(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR, request.getRequestURI()));
    }

    private ResponseEntity<ErrorResponse> respond(ErrorResponse body) {
        return ResponseEntity.status(body.status()).body(body);
    }
}
