package com.sparta.logistics.delivery.infrastructure.client;

import com.sparta.logistics.delivery.infrastructure.client.dto.AssignmentNotificationRequest;
import com.sparta.logistics.delivery.infrastructure.client.dto.OrderCreationResultResponse;
import com.sparta.logistics.delivery.infrastructure.config.ConnectFailureRetryConfig;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * order-service 내부 API(결정 로그 29). 연결 실패만 한 번 더 시도한다(ConnectFailureRetryer). 연결 실패는 요청이 가지 않았으므로
 * I-20를 다시 보내도 DM이 두 번 가지 않는다. 읽기 타임아웃은 order가 받았을 수 있어 다시 보내지 않는다.
 */
@FeignClient(name = "order-service", path = "/internal/orders", configuration = ConnectFailureRetryConfig.class)
public interface OrderClient {

    /** I-20 배정 결과 알림 (제공: order-service). 202, 발송은 order가 비동기로 한다 */
    @PostMapping("/{orderId}/assignment-notifications")
    void notifyAssignment(@PathVariable("orderId") UUID orderId, @RequestBody AssignmentNotificationRequest request);

    /** I-21 주문 생성 결과 (제공: order-service) */
    @GetMapping("/{orderId}/creation-result")
    OrderCreationResultResponse getCreationResult(@PathVariable("orderId") UUID orderId);
}
