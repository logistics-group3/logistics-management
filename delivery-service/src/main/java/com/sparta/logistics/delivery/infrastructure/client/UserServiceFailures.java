package com.sparta.logistics.delivery.infrastructure.client;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import feign.FeignException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.function.Predicate;

/**
 * user-service 호출 실패를 "user를 쓸 수 없음(가용성 실패)"과 "user가 정상으로 거절함"으로 나눈다(결정 로그 26).
 * 서킷 브레이커의 실패 판정({@code record-failure-predicate})과 adapter의 fallback이 같은 기준을 쓴다.
 */
public class UserServiceFailures implements Predicate<Throwable> {

    /** 서킷 브레이커가 실패로 셀 예외. 4xx 거절(예: 검증 실패)은 user가 살아 있다는 뜻이라 세지 않는다. */
    @Override
    public boolean test(Throwable t) {
        return isUnavailable(t);
    }

    /** 응답이 없거나(타임아웃·연결 실패), 5xx이거나, 우리 쪽에서 막은(서킷 열림, 벌크헤드 가득) 경우 */
    static boolean isUnavailable(Throwable t) {
        if (t instanceof CallNotPermittedException || t instanceof BulkheadFullException || t instanceof FeignException) {
            return true;
        }
        return t instanceof BusinessException e && e.getErrorCode().status().is5xxServerError();
    }

    /**
     * fallback에서 다시 던질 예외. 서킷·벌크헤드 거절은 그대로 두면 공통 예외 처리기가 500으로 응답하므로 503으로 바꾼다.
     * 그 밖의 예외는 원래 예외를 그대로 올린다.
     */
    static RuntimeException toResponse(Throwable t) {
        if (t instanceof CallNotPermittedException || t instanceof BulkheadFullException) {
            return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        return t instanceof RuntimeException runtime ? runtime : new IllegalStateException(t);
    }
}
