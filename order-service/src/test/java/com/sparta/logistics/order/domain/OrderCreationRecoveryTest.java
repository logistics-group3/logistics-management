package com.sparta.logistics.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderCreationRecoveryTest {
    @Test
    @DisplayName("복구 실행권을 얻으면 원래 작업은 완료·중단·실패 상태를 덮어쓸 수 없다")
    void fencesOriginalWorker() {
        OrderCreation creation = OrderCreation.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID recoveryId = UUID.randomUUID();
        creation.claimRecovery(recoveryId);
        assertThat(creation.getStatus()).isEqualTo(OrderCreationStatus.RECOVERY_REQUIRED);
        assertThatThrownBy(() -> creation.complete(UUID.randomUUID())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> creation.abort(null, "x", "x")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> creation.requireRecovery(null, CreationFailureReason.ORDER_COMMIT_UNKNOWN))
                .isInstanceOf(BusinessException.class);
        creation.finishRecovery(recoveryId);
        assertThat(creation.getStatus()).isEqualTo(OrderCreationStatus.ABORTED);
        assertThat(creation.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("실행 중 중복은 재실행하지 않고 명시적 재개 후에는 과거 실행 ID가 거절된다")
    void onlyCurrentExecutionCanFinish() {
        OrderCreationRecovery recovery = OrderCreationRecovery.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), OrderCreationStatus.PROCESSING, "프로세스 종료 확인");
        UUID old = recovery.getExecutionId();
        assertThat(recovery.resume(null)).isFalse();
        assertThat(recovery.resume(old)).isTrue();
        assertThat(recovery.getExecutionId()).isNotEqualTo(old);
        assertThatThrownBy(() -> recovery.complete(old)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> recovery.resume(old)).isInstanceOf(BusinessException.class);
        recovery.complete(recovery.getExecutionId());
        assertThat(recovery.resume(null)).isFalse();
    }
}
