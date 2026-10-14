package com.sparta.logistics.gateway.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

/** common의 ErrorResponse와 같은 형식 (Gateway 단계 예외도 클라이언트는 code 하나로 분기한다). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GatewayErrorResponse(int status, String code, String message, String path, LocalDateTime timestamp) {
}
