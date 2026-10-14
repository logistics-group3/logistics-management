package com.sparta.logistics.gateway.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 인증 필터 등 Gateway 단계에서 던지는 예외. code는 common ErrorCode의 이름과 같은 값을 쓴다. */
@Getter
public class GatewayException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public GatewayException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static GatewayException unauthorized() {
        return new GatewayException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다.");
    }

    public static GatewayException tokenRoleMismatch() {
        return new GatewayException(HttpStatus.UNAUTHORIZED, "TOKEN_ROLE_MISMATCH",
                "권한이 변경되었거나 탈퇴한 사용자입니다. 다시 로그인하세요.");
    }

    public static GatewayException serviceUnavailable() {
        return new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "하위 서비스 호출에 실패했습니다.");
    }
}
