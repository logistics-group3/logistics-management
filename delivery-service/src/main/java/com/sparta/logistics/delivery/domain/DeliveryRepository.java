package com.sparta.logistics.delivery.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 배송 명령(생성·변경·삭제)용 저장소. Entity를 읽고 저장하는 일만 한다.
 * 조회 화면용 데이터는 application/DeliveryQueryRepository가 DTO로 바로 읽는다(결정 로그 17).
 */
public interface DeliveryRepository {

    Delivery save(Delivery delivery);

    /** 배송과 구간을 저장하고 바로 INSERT한다. order_id 유니크 위반을 호출한 곳에서 잡기 위해 쓴다. */
    Delivery saveAndFlush(Delivery delivery);

    Optional<Delivery> findById(UUID id);

    Optional<Delivery> findByOrderId(UUID orderId);

    /**
     * 배송 행을 잠그고 읽는다(SELECT ... FOR UPDATE). 배송 상태나 구간을 바꾸는 명령은 이 조회로 시작해 같은 배송의 다른 변경과 순서대로 처리한다.
     * 잠금은 트랜잭션이 끝날 때까지 유지된다. 삭제됐거나 없으면 비어 있다.
     */
    Optional<Delivery> findByIdForUpdate(UUID id);

    /** 주문 ID로 배송 행을 잠그고 읽는다. 잠금 규칙은 {@link #findByIdForUpdate(UUID)}와 같다. */
    Optional<Delivery> findByOrderIdForUpdate(UUID orderId);

    /**
     * 허브 배정 작업 잠금을 기다리지 않고 시도한다. 잡으면 true이고 트랜잭션이 끝날 때 풀린다.
     * 다른 작업이 이미 그 허브를 처리 중이면 false다(결정 로그 27). 트랜잭션 안에서만 부른다.
     */
    boolean tryLockHubAssignment(UUID hubId);

    /**
     * 이 허브가 담당하는 배정 대기 구간이 있는 배송을 오래된 순으로 최대 limit건 잠근다. 다른 요청이 잡고 있는 배송은 기다리지 않고
     * 건너뛴다(SKIP LOCKED). 트랜잭션 안에서만 부른다.
     */
    List<UUID> lockDeliveriesAwaitingManager(UUID hubId, int limit);

    /** 배송과 구간을 한 번에 읽는다. 배정 작업이 잠근 배송들을 읽을 때 쓴다. */
    List<Delivery> findAllWithRoutesByIdIn(Collection<UUID> ids);
}
