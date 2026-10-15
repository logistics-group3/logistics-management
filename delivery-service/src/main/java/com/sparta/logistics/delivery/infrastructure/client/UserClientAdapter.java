package com.sparta.logistics.delivery.infrastructure.client;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.application.DeliveryManagerReader;
import com.sparta.logistics.delivery.application.RecipientReader;
import com.sparta.logistics.delivery.domain.DeliveryManager;
import com.sparta.logistics.delivery.infrastructure.client.dto.DeliveryManagerResponse;
import com.sparta.logistics.delivery.infrastructure.client.dto.UserResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** user의 사용자 정보를 배송 용어(배송담당자, 수령인)로 바꿔 들여온다(delivery.md 2-2). */
@Component
@RequiredArgsConstructor
public class UserClientAdapter implements DeliveryManagerReader, RecipientReader {

    /** user I-01이 없거나 탈퇴한 사용자에게 돌려주는 코드 */
    static final String USER_NOT_FOUND = "USER_NOT_FOUND";

    private final UserClient userClient;

    @Override
    public List<UUID> findIdsByHub(UUID hubId) {
        return userClient.getDeliveryManagers(hubId).stream()
                .map(DeliveryManagerResponse::userId)
                .toList();
    }

    @Override
    public Optional<DeliveryManager> findById(UUID userId) {
        return findUser(userId)
                .filter(user -> user.isDeliveryManager() && user.hubId() != null)
                .map(user -> new DeliveryManager(user.userId(), user.hubId()));
    }

    @Override
    public Optional<String> findSlackId(UUID receiverManagerId) {
        return findUser(receiverManagerId).map(UserResponse::slackId);
    }

    /**
     * Note: 404 USER_NOT_FOUND만 "없음"으로 바꾸고 나머지 실패(503, 타임아웃)는 그대로 올린다.
     * 장애를 "없음"으로 삼키면 user가 잠깐 멈춘 사이 배송담당자가 없다고 잘못 판단하게 된다.
     */
    private Optional<UserResponse> findUser(UUID userId) {
        try {
            return Optional.of(userClient.getUser(userId));
        } catch (BusinessException e) {
            if (USER_NOT_FOUND.equals(e.getErrorCode().code())) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
