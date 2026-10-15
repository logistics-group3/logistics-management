package com.sparta.logistics.delivery.application;

import com.sparta.logistics.delivery.domain.DeliveryManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 배송담당자 조회(user I-14, I-01). 구현은 infrastructure/client. */
public interface DeliveryManagerReader {

    /** I-14 허브 소속 배송담당자 userId 목록. 없으면 빈 목록이다. */
    List<UUID> findIdsByHub(UUID hubId);

    /** I-01 사용자가 배송담당자면 값이 있다. 없거나(탈퇴 포함) 배송담당자가 아니면 빈 값이다. */
    Optional<DeliveryManager> findById(UUID userId);
}
