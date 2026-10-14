package com.sparta.logistics.common.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.ErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;

/**
 * 하위 서비스의 에러 응답을 도메인 예외로 바꾼다 (ADR-04).
 * 호출하는 쪽은 HTTP 상태가 아니라 예외의 ErrorCode로 보상 여부를 결정한다.
 * 5xx는 모두 SERVICE_UNAVAILABLE(503)로 통일한다.
 */
@RequiredArgsConstructor
public class FeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() >= 500) {
            return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }

        String code = null;
        String message = null;
        if (response.body() != null) {
            try (InputStream body = response.body().asInputStream()) {
                JsonNode json = objectMapper.readTree(body);
                code = json.path("code").asText(null);
                message = json.path("message").asText(null);
            } catch (IOException ignored) {
                // 본문을 읽을 수 없으면 상태 코드만으로 판단한다.
            }
        }

        ErrorCode errorCode = ErrorCode.fromCode(code);
        if (errorCode == null) {
            errorCode = ErrorCode.INVALID_REQUEST;
        }
        return new BusinessException(errorCode, message != null ? message : errorCode.getMessage());
    }
}
