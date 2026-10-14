package com.sparta.logistics.common.exception;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Note: 모든 서비스의 예외를 SA 6.1 에러 형식 하나로 바꾸는 지점. 컨트롤러와 서비스는 예외를 던지기만 하고
 * 응답 모양을 몰라도 된다. 예상한 예외는 WARN 없이 그대로 변환하고, 예상하지 못한 예외만 ERROR로 남긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest request) {
        return respond(e.getErrorCode(), e.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(CommonErrorCode.VALIDATION_ERROR, CommonErrorCode.VALIDATION_ERROR.message(), request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = e.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return respond(CommonErrorCode.VALIDATION_ERROR, CommonErrorCode.VALIDATION_ERROR.message(), request, errors);
    }

    /** 본문 JSON 파싱 실패, Enum에 없는 값 바인딩 실패(SA 8.5), 타입 불일치, 필수 파라미터·헤더 누락. */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest request) {
        return respond(CommonErrorCode.VALIDATION_ERROR, CommonErrorCode.VALIDATION_ERROR.message(), request, List.of());
    }

    /** 컨트롤러 파라미터의 메서드 검증 실패. 예: {@code @RequestParam @Min(1) int quantity} (Spring 6.1+). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorResponse.FieldError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return respond(CommonErrorCode.VALIDATION_ERROR, CommonErrorCode.VALIDATION_ERROR.message(), request, errors);
    }

    /** 정렬 파라미터에 Entity에 없는 필드를 넣은 경우. 예: {@code sort=password,DESC}. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleUnknownSort(PropertyReferenceException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = List.of(new ErrorResponse.FieldError("sort", "정렬할 수 없는 필드입니다: " + e.getPropertyName()));
        return respond(CommonErrorCode.VALIDATION_ERROR, CommonErrorCode.VALIDATION_ERROR.message(), request, errors);
    }

    /**
     * Note: Spring MVC 표준 예외(415 Content-Type, 406, 404 경로 없음, 405 메서드 등)는 모두 Spring의 {@link org.springframework.web.ErrorResponse}를 구현하고
     * 올바른 HTTP 상태를 갖고 있다. 이것을 아래 Exception 처리기로 흘려보내면 클라이언트 실수가 500으로 바뀐다. 상태를 보존해 공통 코드로 바꾼다.
     */
    @ExceptionHandler({ServletException.class, ErrorResponseException.class})
    public ResponseEntity<ErrorResponse> handleSpringStandard(Exception e, HttpServletRequest request) {
        if (!(e instanceof org.springframework.web.ErrorResponse standard)) {
            return handleUnexpected(e, request);
        }
        HttpStatusCode status = standard.getStatusCode();
        if (status.is5xxServerError()) {
            return handleUnexpected(e, request);
        }
        ErrorCode errorCode = toCommonCode(status);
        return respond(errorCode, errorCode.message(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception: {} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(CommonErrorCode.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_SERVER_ERROR.message(), request, null);
    }

    private ErrorCode toCommonCode(HttpStatusCode status) {
        return switch (HttpStatus.valueOf(status.value())) {
            case NOT_FOUND -> CommonErrorCode.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE -> CommonErrorCode.NOT_ACCEPTABLE;
            case UNSUPPORTED_MEDIA_TYPE -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case BAD_REQUEST -> CommonErrorCode.VALIDATION_ERROR;
            default -> new SimpleErrorCode(status, CommonErrorCode.INVALID_REQUEST.code(), CommonErrorCode.INVALID_REQUEST.message());
        };
    }

    private ResponseEntity<ErrorResponse> respond(
            ErrorCode errorCode, String message, HttpServletRequest request, List<ErrorResponse.FieldError> errors) {
        return ResponseEntity.status(errorCode.status())
                .body(ErrorResponse.of(errorCode, message, request.getRequestURI(), errors));
    }
}
