package com.sparta.logistics.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.exception.ErrorResponse;
import com.sparta.logistics.common.exception.RemoteErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/**
 * 하위 서비스 에러 응답 변환 규칙.
 * - 4xx + 공통 에러 형식: 하위 서비스의 코드를 그대로 전달 (예: 409 STOCK_INSUFFICIENT)
 * - 5xx 또는 해석 불가: 503 SERVICE_UNAVAILABLE
 */
@Slf4j
@RequiredArgsConstructor
public class FeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        HttpStatus status = HttpStatus.resolve(response.status());
        if (status != null && status.is4xxClientError()) {
            ErrorResponse body = readBody(response);
            if (body != null && body.code() != null) {
                return new BusinessException(new RemoteErrorCode(status, body.code(), body.message()), body.message());
            }
        }
        log.warn("하위 서비스 오류 응답: {} -> {}", methodKey, response.status());
        return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    private ErrorResponse readBody(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream body = response.body().asInputStream()) {
            return objectMapper.readValue(body, ErrorResponse.class);
        } catch (IOException e) {
            return null;
        }
    }
}
