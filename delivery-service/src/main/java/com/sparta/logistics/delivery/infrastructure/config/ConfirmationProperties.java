package com.sparta.logistics.delivery.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 주문 확정 뒤 배정·알림 설정(결정 로그 29, config-repo/delivery-service.yml의 delivery.confirmation).
 *
 * @param manualAssignmentAfter 확정 뒤 이 시간이 지나도록 후보가 0명이면 수동 지정으로 넘긴다
 * @param notifyOrder           배정 결과를 order에 알린다(I-20). 테스트처럼 order가 없는 환경에서 끈다
 * @param check                 확정 유실 안전망. 주기는 delivery.confirmation.check.interval-ms(@Scheduled가 직접 읽는다)
 */
@ConfigurationProperties(prefix = "delivery.confirmation")
public record ConfirmationProperties(@DefaultValue("60s") Duration manualAssignmentAfter,
                                     @DefaultValue("true") boolean notifyOrder,
                                     @DefaultValue Check check) {

    /**
     * @param enabled     안전망 주기 작업을 켠다
     * @param after       만든 뒤 이 시간이 지나도 미확정이면 order에 묻는다
     * @param giveUpAfter 만든 뒤 이 시간이 지나면 더 묻지 않는다(고아 배송)
     * @param batchSize   한 번에 묻는 최대 건수
     */
    public record Check(@DefaultValue("true") boolean enabled,
                        @DefaultValue("2m") Duration after,
                        @DefaultValue("30m") Duration giveUpAfter,
                        @DefaultValue("50") int batchSize) {
    }
}
