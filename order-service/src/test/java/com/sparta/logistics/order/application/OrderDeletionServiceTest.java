package com.sparta.logistics.order.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.OrderDeletionOutcome;
import com.sparta.logistics.order.application.dto.OrderDeletionTarget;
import com.sparta.logistics.order.domain.DeletionFailureReason;
import com.sparta.logistics.order.domain.OrderErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class OrderDeletionServiceTest {
    @Mock private OrderDeletionWriter writer;
    @Mock private DeliveryReader delivery;
    private OrderDeletionService service;
    private final UserContext master = new UserContext(UUID.randomUUID(), "master", Role.MASTER, false);
    private final UUID id = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();

    @BeforeEach
    void setUp() { service = new OrderDeletionService(writer, delivery); }

    @Test
    @DisplayName("MASTER가 아니면 조회나 원격 호출 전에 거부한다")
    void rejectsUser() {
        assertThatThrownBy(() -> service.delete(new UserContext(master.userId(), "user", Role.USER, false), id))
                .extracting("errorCode").isEqualTo(CommonErrorCode.FORBIDDEN);
        verifyNoInteractions(writer, delivery);
    }

    @Test
    @DisplayName("취소·기존 진행 기록은 배송을 재조회하지 않고 삭제한다")
    void skipsReadForApprovedTarget() {
        OrderDeletionTarget target = target(false);
        service.delete(master, id);
        verify(writer).start(target, false);
        verify(writer).delete(master, id);
        verifyNoInteractions(delivery);
    }

    @Test
    @DisplayName("새 ORDERED 삭제는 배송 완료를 조회하고 확인 결과를 시작 기록에 전달한다")
    void checksDelivery() {
        OrderDeletionTarget target = target(true);
        given(delivery.isDelivered(id, deliveryId)).willReturn(true);
        service.delete(master, id);
        verify(writer).start(target, true);
        verify(writer).delete(master, id);
    }

    @Test
    @DisplayName("삭제 조건 거절은 그대로 반환하고 삭제 실행으로 넘어가지 않는다")
    void preservesEligibilityFailure() {
        OrderDeletionTarget target = target(true);
        willThrow(new BusinessException(OrderErrorCode.ORDER_NOT_DELETABLE)).given(writer).start(target, false);
        assertThatThrownBy(() -> service.delete(master, id)).extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_DELETABLE);
        verify(writer, never()).delete(any(), any());
    }

    @Test
    @DisplayName("시작 기록 커밋이 실패하면 원격 삭제 단계에 진입하지 않는다")
    void stopsWhenStartFails() {
        OrderDeletionTarget target = target(false);
        willThrow(failure()).given(writer).start(target, false);
        assertError(CommonErrorCode.INTERNAL_SERVER_ERROR);
        verify(writer, never()).delete(any(), any());
    }

    @Test
    @DisplayName("삭제 반환 예외 뒤 커밋이 확인되면 성공을 유지하고 기록을 덮지 않는다")
    void confirmsCommit() {
        target(false);
        willThrow(failure()).given(writer).delete(master, id);
        given(writer.resolve(id)).willReturn(OrderDeletionOutcome.COMMITTED);
        assertThatCode(() -> service.delete(master, id)).doesNotThrowAnyException();
        verify(writer, never()).requireRecovery(any(), any());
    }

    @Test
    @DisplayName("로컬 미커밋이면 실패를 기록하고 최초 500을 반환한다")
    void recordsFailedWrite() {
        target(false);
        willThrow(failure()).given(writer).delete(master, id);
        given(writer.resolve(id)).willReturn(OrderDeletionOutcome.NOT_COMMITTED);
        assertError(CommonErrorCode.INTERNAL_SERVER_ERROR);
        verify(writer).requireRecovery(id, DeletionFailureReason.ORDER_WRITE_FAILED);
    }

    @Test
    @DisplayName("원격 삭제 오류는 503과 배송 삭제 실패 기록을 유지한다")
    void recordsRemoteFailure() {
        target(false);
        willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE)).given(writer).delete(master, id);
        given(writer.resolve(id)).willReturn(OrderDeletionOutcome.NOT_COMMITTED);
        assertError(CommonErrorCode.SERVICE_UNAVAILABLE);
        verify(writer).requireRecovery(id, DeletionFailureReason.DELIVERY_DELETE_FAILED);
    }

    @Test
    @DisplayName("커밋 확인이 불확실하면 삭제 허가 근거를 해제하지 않는다")
    void recordsUnknownResult() {
        target(false);
        willThrow(failure()).given(writer).delete(master, id);
        given(writer.resolve(id)).willReturn(OrderDeletionOutcome.UNKNOWN);
        assertError(CommonErrorCode.INTERNAL_SERVER_ERROR);
        verify(writer).requireRecovery(id, DeletionFailureReason.ORDER_COMMIT_UNKNOWN);
    }

    @Test
    @DisplayName("재확인·복구 기록 실패가 겹쳐도 최초 원격 오류를 덮지 않는다")
    void preservesOriginalFailure() {
        target(false);
        willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE)).given(writer).delete(master, id);
        given(writer.resolve(id)).willThrow(failure());
        willThrow(failure()).given(writer).requireRecovery(any(), any());
        assertError(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    private OrderDeletionTarget target(boolean check) {
        OrderDeletionTarget target = new OrderDeletionTarget(id, deliveryId, check);
        given(writer.inspect(id)).willReturn(target);
        return target;
    }
    private void assertError(CommonErrorCode code) {
        assertThatThrownBy(() -> service.delete(master, id)).extracting("errorCode").isEqualTo(code);
    }
    private DataAccessResourceFailureException failure() { return new DataAccessResourceFailureException("fixture DB failure"); }
}
