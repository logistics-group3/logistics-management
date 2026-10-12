package com.sparta.logistics.common.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.exception.RemoteErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/**
 * 상대 서비스 에러 응답 → BusinessException (SA 3.5)
 * <ul>
 *   <li>4xx: 상대의 code·message를 그대로 유지 (예: 404 HUB_NOT_FOUND, 409 STOCK_INSUFFICIENT)</li>
 *   <li>5xx 또는 해석 불가: 503 SERVICE_UNAVAILABLE</li>
 * </ul>
 * 특정 코드만 다르게 처리하려면 호출부에서 {@code catch (BusinessException e) { if (e.hasCode(...)) ... }}.
 */
@Slf4j
@RequiredArgsConstructor
public class FeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        HttpStatus status = HttpStatus.resolve(response.status());
        if (status == null || status.is5xxServerError()) {
            log.warn("Feign 호출 실패 {} status={}", methodKey, response.status());
            return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        RemoteError body = readBody(response);
        if (body == null || body.code() == null) {
            return new BusinessException(new RemoteErrorCode(status, status.name(), status.getReasonPhrase()));
        }
        return new BusinessException(new RemoteErrorCode(status, body.code(), body.message()));
    }

    private RemoteError readBody(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream in = response.body().asInputStream()) {
            return objectMapper.readValue(in, RemoteError.class);
        } catch (IOException e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RemoteError(String code, String message) {
    }
}
