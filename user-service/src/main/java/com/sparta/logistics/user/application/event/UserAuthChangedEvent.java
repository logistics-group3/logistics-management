package com.sparta.logistics.user.application.event;

import java.util.UUID;

/** Gateway 권한 재확인(I-01)에 담기는 값(role, 배송담당 여부)이 바뀌었거나 사용자가 탈퇴했다. */
public record UserAuthChangedEvent(UUID userId) {
}
