package com.sparta.logistics.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.response.ErrorResponse;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/**
 * 하위 서비스 에러 응답 → BusinessException 변환 (SA 3.5)
 * - 4xx + SA 에러 형식: 같은 status·code·message 로 그대로 전달 (예: 404 HUB_NOT_FOUND, 409 STOCK_INSUFFICIENT)
 * - 5xx 또는 형식을 알 수 없는 응답: 503 SERVICE_UNAVAILABLE
 */
@Slf4j
public class CommonFeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    public CommonFeignErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        HttpStatus status = HttpStatus.resolve(response.status());

        if (status != null && status.is4xxClientError() && response.body() != null) {
            try (InputStream body = response.body().asInputStream()) {
                ErrorResponse error = objectMapper.readValue(body, ErrorResponse.class);
                if (error.code() != null) {
                    return new BusinessException(new RemoteErrorCode(status, error.code(), error.message()));
                }
            } catch (Exception e) {
                log.warn("Feign error body parse failed. method={}, status={}", methodKey, response.status(), e);
            }
        }

        log.error("Feign call failed. method={}, status={}", methodKey, response.status());
        return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
    }
}
