package com.sparta.logistics.delivery.infrastructure.client;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.application.DeliveryManagerReader;
import com.sparta.logistics.delivery.application.RecipientReader;
import com.sparta.logistics.delivery.domain.DeliveryManager;
import com.sparta.logistics.delivery.infrastructure.client.dto.DeliveryManagerResponse;
import com.sparta.logistics.delivery.infrastructure.client.dto.UserResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * user의 사용자 정보를 배송 용어(배송담당자, 수령인)로 바꿔 들여온다(delivery.md 2-2).
 *
 * <p>Note: user 장애가 배송 전체로 번지지 않게 막는다(결정 로그 26, fault-isolation.md).
 * <ul>
 *   <li>벌크헤드: user를 동시에 부르는 호출을 제한한다. user가 멈춰도 묶이는 Tomcat 스레드가 그 수를 넘지 않아 나머지 스레드로 조회가 돈다.</li>
 *   <li>서킷 브레이커: 실패·느린 호출이 많으면 user를 부르지 않고 바로 실패한다. 장애 중 요청이 타임아웃까지 기다리지 않는다.</li>
 * </ul>
 * 기본 적용 순서는 CircuitBreaker(Bulkhead(호출))다. 벌크헤드 거절도 서킷 브레이커가 실패로 센다.
 * 애너테이션은 다른 빈이 부르는 public 메서드에만 건다. 같은 클래스 안의 호출(findUser)에는 AOP가 걸리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserClientAdapter implements DeliveryManagerReader, RecipientReader {

    /** Resilience4j 인스턴스 이름. 설정은 config-repo/delivery-service.yml의 resilience4j.*.instances.user-service */
    static final String USER_SERVICE = "user-service";

    /** user I-01이 없거나 탈퇴한 사용자에게 돌려주는 코드 */
    static final String USER_NOT_FOUND = "USER_NOT_FOUND";

    private final UserClient userClient;

    /** 자동 배정 후보(I-14). 대신할 값이 없으므로 user를 쓸 수 없으면 503으로 바로 실패한다. */
    @Override
    @Bulkhead(name = USER_SERVICE)
    @CircuitBreaker(name = USER_SERVICE, fallbackMethod = "managersUnavailable")
    public List<UUID> findIdsByHub(UUID hubId) {
        return userClient.getDeliveryManagers(hubId).stream()
                .map(DeliveryManagerResponse::userId)
                .toList();
    }

    /** D-04 재배정 대상 확인. 확인할 수 없으면 503이다. */
    @Override
    @Bulkhead(name = USER_SERVICE)
    @CircuitBreaker(name = USER_SERVICE, fallbackMethod = "managerUnavailable")
    public Optional<DeliveryManager> findById(UUID userId) {
        return findUser(userId)
                .filter(user -> user.isDeliveryManager() && user.hubId() != null)
                .map(user -> new DeliveryManager(user.userId(), user.hubId()));
    }

    /**
     * 수령인 Slack ID(I-01). user를 쓸 수 없으면 Slack ID 없이 배송을 만든다(부분 저하, 결정 로그 26).
     * 배송 생성은 이미 Slack ID가 NULL이어도 진행하고 알림만 빠진다.
     */
    @Override
    @Bulkhead(name = USER_SERVICE)
    @CircuitBreaker(name = USER_SERVICE, fallbackMethod = "slackIdUnavailable")
    public Optional<String> findSlackId(UUID receiverManagerId) {
        return findUser(receiverManagerId).map(UserResponse::slackId);
    }

    /**
     * Note: 404 USER_NOT_FOUND만 "없음"으로 바꾸고 나머지 실패(503, 타임아웃)는 그대로 올린다.
     * 장애를 "없음"으로 삼키면 user가 잠깐 멈춘 사이 배송담당자가 없다고 잘못 판단하게 된다.
     * 부분 저하는 여기가 아니라 fallback에서 이유를 남기고 한다.
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

    private List<UUID> managersUnavailable(UUID hubId, Throwable t) {
        throw UserServiceFailures.toResponse(t);
    }

    private Optional<DeliveryManager> managerUnavailable(UUID userId, Throwable t) {
        throw UserServiceFailures.toResponse(t);
    }

    private Optional<String> slackIdUnavailable(UUID receiverManagerId, Throwable t) {
        if (!UserServiceFailures.isUnavailable(t)) {
            throw UserServiceFailures.toResponse(t);
        }
        log.warn("user 장애로 수령인 Slack ID 없이 진행: receiverManagerId={}, cause={}",
                receiverManagerId, t.getClass().getSimpleName());
        return Optional.empty();
    }
}
