package com.sparta.logistics.delivery.application;

import java.util.Optional;
import java.util.UUID;

/** 수령인 Slack ID 조회(user I-01). 구현은 infrastructure/client. */
public interface RecipientReader {

    /** 요청업체 관리자의 Slack ID. 사용자가 없으면(탈퇴 포함) 빈 값이다. */
    Optional<String> findSlackId(UUID receiverManagerId);
}
