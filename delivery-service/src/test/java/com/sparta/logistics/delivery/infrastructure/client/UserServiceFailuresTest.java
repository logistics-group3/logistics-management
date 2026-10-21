package com.sparta.logistics.delivery.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import feign.FeignException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 서킷 브레이커의 실패 판정과 fallback이 쓰는 기준(결정 로그 26) */
class UserServiceFailuresTest {

    private final UserServiceFailures failures = new UserServiceFailures();

    @Test
    @DisplayName("응답 없음·5xx·서킷 열림·벌크헤드 가득은 user를 쓸 수 없는 실패로 센다")
    void unavailable() {
        assertThat(failures.test(mock(FeignException.class))).isTrue();
        assertThat(failures.test(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE))).isTrue();
        assertThat(failures.test(CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("t")))).isTrue();
        assertThat(failures.test(BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("t")))).isTrue();
    }

    @Test
    @DisplayName("4xx 거절은 user가 살아 있다는 뜻이라 실패로 세지 않는다")
    void clientErrorIsNotFailure() {
        assertThat(failures.test(new BusinessException(CommonErrorCode.VALIDATION_ERROR))).isFalse();
    }

    @Test
    @DisplayName("서킷·벌크헤드 거절은 500이 아니라 503 SERVICE_UNAVAILABLE로 응답하게 바꾼다")
    void rejectionBecomes503() {
        RuntimeException open = UserServiceFailures.toResponse(
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("t")));
        assertThat(open).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);

        BusinessException original = new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        assertThat(UserServiceFailures.toResponse(original)).isSameAs(original);
    }
}
