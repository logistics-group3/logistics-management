package com.sparta.logistics.delivery.domain;

import java.util.UUID;

/**
 * 배송 생성 종료 표시 저장소와 주문 단위 잠금.
 * I-09의 저장과 I-13이 같은 orderId의 잠금을 잡아, "종료 표시 확인 → 저장" 사이에 삭제가 끼어들어도 고아 배송이 생기지 않게 한다.
 */
public interface DeliveryCreationClosureRepository {

    boolean existsById(UUID orderId);

    /** 종료 표시를 남긴다. 이미 있으면 그대로 둔다(멱등). */
    void close(UUID orderId, String closedBy);

    /**
     * 같은 orderId의 I-09 저장·I-13을 한 번에 하나씩만 실행하도록 트랜잭션 범위 advisory lock을 잡는다. 커밋·롤백에서 풀린다.
     * 반환값은 의미가 없다(항상 1). 잠금 획득이 목적이다.
     */
    int lockOrder(UUID orderId);
}
