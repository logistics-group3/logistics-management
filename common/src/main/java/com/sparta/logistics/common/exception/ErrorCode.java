package com.sparta.logistics.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** API 설계서 '에러 코드' 표. 클라이언트는 HTTP 상태가 아니라 code(name)로 분기한다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값 검증에 실패했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    MANAGER_HUB_MISMATCH(HttpStatus.BAD_REQUEST, "담당자의 소속 허브가 구간 담당 허브와 다릅니다."),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    TOKEN_ROLE_MISMATCH(HttpStatus.UNAUTHORIZED, "권한이 변경되었거나 탈퇴한 사용자입니다. 다시 로그인하세요."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    HUB_NOT_FOUND(HttpStatus.NOT_FOUND, "허브를 찾을 수 없습니다."),
    HUB_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "허브 간 경로를 찾을 수 없습니다."),
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "업체를 찾을 수 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "배송을 찾을 수 없습니다."),
    DELIVERY_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "배송 구간을 찾을 수 없습니다."),
    DELIVERY_MANAGER_NOT_FOUND(HttpStatus.NOT_FOUND, "배송담당자를 찾을 수 없습니다."),

    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),

    // 409
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 username입니다."),
    STOCK_INSUFFICIENT(HttpStatus.CONFLICT, "재고가 부족합니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 상태 변경입니다."),
    ORDER_NOT_DELETABLE(HttpStatus.CONFLICT, "삭제할 수 없는 주문입니다."),

    // 500 / 503
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "하위 서비스 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String message;

    /** 문자열 code를 ErrorCode로. 모르는 code면 null. */
    public static ErrorCode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ErrorCode value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
