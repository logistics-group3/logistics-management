package com.sparta.logistics.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 종료된 배송 생성 시도(이슈 #14). 값은 DeliveryCreationClosureRepository.close가 SQL로 넣고, 이 Entity는 존재 확인용이다.
 * Note: 갱신·논리 삭제가 없는 기록형 데이터라 BaseEntity를 쓰지 않는다. order_id가 생성 시도 식별자다.
 */
@Getter
@Entity
@Table(name = "p_delivery_creation_closures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryCreationClosure {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;

    @Column(name = "closed_by", nullable = false, length = 100)
    private String closedBy;
}
