package com.sparta.logistics.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.CompanyResult;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import com.sparta.logistics.order.application.dto.OrderCreationCommitResult;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.dto.ProductResult;
import com.sparta.logistics.order.domain.CreationFailureReason;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderRepository;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class OrderCreationServiceTest {

    @Mock
    private CompanyReader company;
    @Mock
    private DeliveryGateway delivery;
    @Mock
    private OrderWriter writer;
    @Mock
    private OrderRepository orders;

    private final UserContext user = new UserContext(UUID.randomUUID(), "owner", Role.USER, false);
    private final UUID supplierId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final CreateOrderCommand command = new CreateOrderCommand(supplierId, receiverId, productId, 2, "오후 도착 희망");
    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(company, delivery, writer, orders);
        given(company.getCompany(supplierId)).willReturn(new CompanyResult(supplierId, "공급", UUID.randomUUID(), "서울", UUID.randomUUID()));
        given(company.getCompany(receiverId)).willReturn(new CompanyResult(receiverId, "수령", UUID.randomUUID(), "부산", user.userId()));
        given(company.getProduct(productId)).willReturn(new ProductResult(productId, supplierId, "상품", 10_000L));
    }

    @Test
    @DisplayName("진행 기록을 커밋하지 못하면 배송 생성을 시작하지 않는다")
    void stopsBeforeDeliveryWhenStartFails() {
        willThrow(databaseFailure()).given(writer).start(any());

        assertFailure(CommonErrorCode.INTERNAL_SERVER_ERROR);
        verifyNoInteractions(delivery);
        verify(writer, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("저장 예외 후 커밋된 결과가 확인되면 그 결과를 반환하고 배송을 삭제하지 않는다")
    void returnsCommittedOrderWithoutCompensation() {
        given(delivery.create(any())).willReturn(deliveryId);
        AtomicReference<Order> saved = new AtomicReference<>();
        given(writer.save(any(), any(), any())).willAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            throw databaseFailure();
        });
        given(writer.resolveCreation(any())).willAnswer(invocation -> OrderCreationCommitResult.from(saved.get()));

        OrderResult result = service.create(user, command);

        assertThat(result.orderId()).isEqualTo(saved.get().getId());
        assertThat(result.deliveryId()).isEqualTo(deliveryId);
        verify(writer, times(1)).save(any(), any(), any());
        verify(delivery, never()).deleteByOrderId(any());
        verify(writer, never()).abort(any(), any());
        verify(writer, never()).requireRecovery(any(), any(), any());
    }

    @Test
    @DisplayName("주문 커밋 상태가 불일치하면 자동 삭제 없이 복구 대상으로 남긴다")
    void keepsDeliveryWhenCommitIsUnknown() {
        failSave(OrderCreationCommitResult.unknown());

        assertFailure(CommonErrorCode.INTERNAL_SERVER_ERROR);

        verify(delivery, never()).deleteByOrderId(any());
        verify(writer).requireRecovery(any(), eq(deliveryId), eq(CreationFailureReason.ORDER_COMMIT_UNKNOWN));
    }

    @Test
    @DisplayName("커밋 결과 조회가 실패해도 미저장으로 단정해 배송을 삭제하지 않는다")
    void keepsDeliveryWhenVerificationFails() {
        given(delivery.create(any())).willReturn(deliveryId);
        given(writer.save(any(), any(), any())).willThrow(databaseFailure());
        given(writer.resolveCreation(any())).willThrow(databaseFailure());

        assertFailure(CommonErrorCode.INTERNAL_SERVER_ERROR);

        verify(delivery, never()).deleteByOrderId(any());
        verify(writer).requireRecovery(any(), eq(deliveryId), eq(CreationFailureReason.ORDER_COMMIT_UNKNOWN));
    }

    @Test
    @DisplayName("미저장 확인 뒤 배송 삭제까지 실패하면 삭제 실패 사유를 보존한다")
    void recordsFailedDeletion() {
        failSave(OrderCreationCommitResult.notCommitted());
        willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE)).given(delivery).deleteByOrderId(any());

        assertFailure(CommonErrorCode.INTERNAL_SERVER_ERROR);

        verify(delivery, times(3)).deleteByOrderId(any());
        verify(writer).requireRecovery(any(), eq(deliveryId), eq(CreationFailureReason.DELIVERY_DELETE_FAILED));
        verify(writer, never()).abort(any(), any());
    }

    @Test
    @DisplayName("삭제가 성공한 미저장 시도는 확인한 배송 ID와 함께 종료한다")
    void abortsAfterConfirmedCleanup() {
        failSave(OrderCreationCommitResult.notCommitted());

        assertFailure(CommonErrorCode.INTERNAL_SERVER_ERROR);

        verify(delivery).deleteByOrderId(any());
        verify(writer).abort(any(), eq(deliveryId));
    }

    @Test
    @DisplayName("배송 결과가 불확실하면 삭제 보상이 성공해도 복구 대기를 유지한다")
    void keepsUncertainDeliveryOpenAfterCleanup() {
        given(delivery.create(any())).willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));

        assertFailure(CommonErrorCode.SERVICE_UNAVAILABLE);

        verify(delivery).deleteByOrderId(any());
        verify(writer).requireRecovery(any(), isNull(), eq(CreationFailureReason.DELIVERY_RESULT_UNKNOWN));
        verify(writer, never()).abort(any(), any());
        verify(writer, never()).resolveCreation(any());
        verify(writer, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("배송 응답 해석의 예상 밖 오류도 진행 기록을 남기고 503으로 처리한다")
    void recordsUnexpectedDeliveryFailure() {
        given(delivery.create(any())).willThrow(new IllegalStateException("decode failed"));

        assertFailure(CommonErrorCode.SERVICE_UNAVAILABLE);
        verify(writer).requireRecovery(any(), isNull(), eq(CreationFailureReason.DELIVERY_RESULT_UNKNOWN));
    }

    @Test
    @DisplayName("복구 기록 갱신 실패가 원래 저장 실패를 덮거나 삭제를 유발하지 않는다")
    void preservesOriginalFailureWhenRecoveryWriteFails() {
        failSave(OrderCreationCommitResult.unknown());
        willThrow(databaseFailure()).given(writer).requireRecovery(any(), any(), any());

        assertFailure(CommonErrorCode.INTERNAL_SERVER_ERROR);
        verify(delivery, never()).deleteByOrderId(any());
    }

    @Test
    @DisplayName("명확한 배송 거절은 종료 기록에 실패해도 원래 409를 반환한다")
    void preservesDeliveryRejectionWhenAbortFails() {
        given(delivery.create(any())).willThrow(new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION));
        willThrow(databaseFailure()).given(writer).abort(any(), isNull());

        assertFailure(CommonErrorCode.INVALID_STATUS_TRANSITION);
        verify(delivery, never()).deleteByOrderId(any());
    }

    @Test
    @DisplayName("확인된 보상 뒤 종료 기록에 실패해도 최초 저장 실패를 유지한다")
    void preservesFailureWhenAbortAfterCleanupFails() {
        failSave(OrderCreationCommitResult.notCommitted());
        willThrow(databaseFailure()).given(writer).abort(any(), any());

        assertFailure(CommonErrorCode.INTERNAL_SERVER_ERROR);
        verify(delivery).deleteByOrderId(any());
    }

    private void failSave(OrderCreationCommitResult result) {
        given(delivery.create(any())).willReturn(deliveryId);
        given(writer.save(any(), any(), any())).willThrow(databaseFailure());
        given(writer.resolveCreation(any())).willReturn(result);
    }

    private void assertFailure(CommonErrorCode code) {
        assertThatThrownBy(() -> service.create(user, command)).extracting("errorCode").isEqualTo(code);
    }

    private static DataAccessResourceFailureException databaseFailure() {
        return new DataAccessResourceFailureException("injected database failure");
    }
}
