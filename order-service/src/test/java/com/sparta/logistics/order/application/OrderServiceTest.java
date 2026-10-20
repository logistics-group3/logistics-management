package com.sparta.logistics.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.exception.SimpleErrorCode;
import com.sparta.logistics.order.application.dto.CompanyResult;
import com.sparta.logistics.order.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.dto.OrderCreationCommitResult;
import com.sparta.logistics.order.application.dto.ProductResult;
import com.sparta.logistics.order.domain.CreationFailureReason;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderRepository;
import com.sparta.logistics.order.domain.OrderStatus;
import feign.Request;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OrderServiceTest {

    @Mock
    private CompanyReader companyReader;
    @Mock
    private DeliveryGateway deliveryGateway;
    @Mock
    private OrderWriter orderWriter;
    @Mock
    private OrderRepository orderRepository;

    private final UserContext user = new UserContext(UUID.randomUUID(), "junhyun", Role.USER, false);
    private final CompanyResult supplier = new CompanyResult(UUID.randomUUID(), "공급업체", UUID.randomUUID(),
            "서울 집하지", UUID.randomUUID());
    private final CompanyResult receiver = new CompanyResult(UUID.randomUUID(), "수령업체", UUID.randomUUID(),
            "부산 배송지", user.userId());
    private final ProductResult product = new ProductResult(UUID.randomUUID(), supplier.companyId(), "마른오징어", 12_000L);
    private final UUID deliveryId = UUID.randomUUID();
    private final CreateOrderCommand command = new CreateOrderCommand(
            supplier.companyId(), receiver.companyId(), product.productId(), 50, "오전 도착 희망");
    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(companyReader, deliveryGateway, orderWriter, orderRepository, OrderCreationExecutor.direct());
    }

    @Test
    @DisplayName("업체·상품 검증 후 같은 선발급 ID로 배송을 만들고 인증된 주문자의 주문을 저장한다")
    void createsOrder() {
        stubSuccess();

        OrderResult result = service.create(user, "test-key", command);

        ArgumentCaptor<CreateDeliveryCommand> delivery = ArgumentCaptor.forClass(CreateDeliveryCommand.class);
        ArgumentCaptor<Order> order = ArgumentCaptor.forClass(Order.class);
        InOrder sequence = inOrder(companyReader, deliveryGateway, orderWriter);
        sequence.verify(companyReader).getCompany(supplier.companyId());
        sequence.verify(companyReader).getCompany(receiver.companyId());
        sequence.verify(companyReader).getProduct(product.productId());
        sequence.verify(orderWriter).start(any(), any());
        sequence.verify(deliveryGateway).create(delivery.capture());
        sequence.verify(orderWriter).save(order.capture(), eq(supplier.name()), eq(receiver.name()));

        assertThat(result.orderId()).isNotNull().isEqualTo(order.getValue().getId());
        assertThat(result.ordererId()).isEqualTo(user.userId());
        assertThat(result.productName()).isEqualTo("마른오징어");
        assertThat(result.unitPrice()).isEqualTo(12_000L);
        assertThat(result.quantity()).isEqualTo(50);
        assertThat(result.totalPrice()).isEqualTo(600_000L);
        assertThat(result.deliveryId()).isEqualTo(deliveryId);
        assertThat(result.status()).isEqualTo(OrderStatus.ORDERED);
        assertThat(result.requestNote()).isEqualTo(command.requestNote());
        assertThat(delivery.getValue()).isEqualTo(new CreateDeliveryCommand(result.orderId(), supplier.companyId(),
                receiver.companyId(), supplier.hubId(), receiver.hubId(), supplier.address(), receiver.address(),
                receiver.name(), receiver.managerId()));
        verify(deliveryGateway, never()).deleteByOrderId(any());
    }

    @Test
    @DisplayName("MASTER는 수령업체 관리자가 아니어도 주문하며 주문자는 실제 요청자로 기록한다")
    void masterCanOrderForAnotherCompany() {
        stubSuccess();
        UserContext master = new UserContext(UUID.randomUUID(), "master01", Role.MASTER, false);

        OrderResult result = service.create(master, "test-key", command);

        assertThat(result.ordererId()).isEqualTo(master.userId());
        verify(orderWriter).save(any(), eq(supplier.name()), eq(receiver.name()));
    }

    @Test
    @DisplayName("수령업체 관리자가 아닌 USER는 배송을 만들기 전에 거부한다")
    void rejectsNonManager() {
        stubCompanies();
        UserContext stranger = new UserContext(UUID.randomUUID(), "stranger", Role.USER, false);

        assertThatThrownBy(() -> service.create(stranger, "test-key", command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.FORBIDDEN);
        verifyNoInteractions(deliveryGateway);
        verify(orderWriter, never()).start(any(), any());
        verify(orderWriter, never()).save(any(), any(), any());
        verify(companyReader, never()).getProduct(any());
    }

    @Test
    @DisplayName("상품이 공급업체 소속이 아니면 배송 생성과 주문 저장을 하지 않는다")
    void rejectsProductFromAnotherSupplier() {
        stubCompanies();
        given(companyReader.getProduct(product.productId())).willReturn(
                new ProductResult(product.productId(), UUID.randomUUID(), product.name(), product.price()));

        assertThatThrownBy(() -> service.create(user, "test-key", command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        verifyNoInteractions(deliveryGateway);
        verify(orderWriter, never()).start(any(), any());
        verify(orderWriter, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("업체 조회가 404로 거절되면 원래 에러를 전달하고 보상하지 않는다")
    void propagatesCompanyNotFound() {
        BusinessException failure = notFound("COMPANY_NOT_FOUND");
        given(companyReader.getCompany(supplier.companyId())).willThrow(failure);

        assertThatThrownBy(() -> service.create(user, "test-key", command)).isSameAs(failure);
        verifyNoInteractions(deliveryGateway);
        verify(orderWriter, never()).start(any(), any());
        verify(orderWriter, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("상품 조회가 404로 거절되면 원래 에러를 전달하고 보상하지 않는다")
    void propagatesProductNotFound() {
        stubCompanies();
        BusinessException failure = notFound("PRODUCT_NOT_FOUND");
        given(companyReader.getProduct(product.productId())).willThrow(failure);

        assertThatThrownBy(() -> service.create(user, "test-key", command)).isSameAs(failure);
        verifyNoInteractions(deliveryGateway);
        verify(orderWriter, never()).start(any(), any());
        verify(orderWriter, never()).save(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"company", "product"})
    @DisplayName("업체·상품 조회 타임아웃은 변경한 데이터 없이 SERVICE_UNAVAILABLE로 끝난다")
    void lookupTimeoutNeedsNoCompensation(String target) {
        if (target.equals("company")) {
            given(companyReader.getCompany(supplier.companyId())).willThrow(timeout());
        } else {
            stubCompanies();
            given(companyReader.getProduct(product.productId())).willThrow(timeout());
        }

        assertThatThrownBy(() -> service.create(user, "test-key", command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        verifyNoInteractions(deliveryGateway);
        verify(orderWriter, never()).start(any(), any());
        verify(orderWriter, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("배송담당자 없음은 저장 전 거절이므로 배송 삭제를 호출하지 않는다")
    void deliveryRejectionNeedsNoCompensation() {
        stubLookup();
        BusinessException failure = notFound("DELIVERY_MANAGER_NOT_FOUND");
        given(deliveryGateway.create(any())).willThrow(failure);

        assertThatThrownBy(() -> service.create(user, "test-key", command)).extracting("errorCode").isEqualTo(failure.getErrorCode());
        verify(deliveryGateway, never()).deleteByOrderId(any());
        verify(orderWriter, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("배송 5xx 응답은 생성 여부를 모르므로 같은 orderId로 삭제 보상한다")
    void compensatesDeliveryServerFailure() {
        stubLookup();
        BusinessException failure = new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        given(deliveryGateway.create(any())).willThrow(failure);

        assertThatThrownBy(() -> service.create(user, "test-key", command)).extracting("errorCode").isEqualTo(failure.getErrorCode());
        UUID orderId = captureCreatedOrderId();
        verify(deliveryGateway).deleteByOrderId(orderId);
        verify(orderWriter, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("배송 타임아웃도 같은 orderId로 삭제 보상하고 SERVICE_UNAVAILABLE을 반환한다")
    void compensatesDeliveryTimeout() {
        stubLookup();
        given(deliveryGateway.create(any())).willThrow(timeout());

        assertThatThrownBy(() -> service.create(user, "test-key", command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        UUID orderId = captureCreatedOrderId();
        verify(deliveryGateway).deleteByOrderId(orderId);
        verify(orderWriter, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("결과를 모르는 배송 생성도 보상이 성공하고 I-17이 CLOSED면 시도를 ABORTED로 닫는다")
    void abortsUnknownDeliveryWhenClosureConfirmed() {
        stubLookup();
        given(deliveryGateway.create(any())).willThrow(timeout());
        given(deliveryGateway.isCreationClosed(any())).willReturn(true);

        assertThatThrownBy(() -> service.create(user, "test-key", command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        UUID orderId = captureCreatedOrderId();
        verify(deliveryGateway).isCreationClosed(orderId);
        verify(orderWriter).abort(any(), eq(null), eq(CommonErrorCode.SERVICE_UNAVAILABLE.code()), any());
        verify(orderWriter, never()).requireRecovery(any(), any(), any());
    }

    @Test
    @DisplayName("I-17이 CLOSED가 아니거나 조회에 실패하면 복구 필요로 남긴다")
    void keepsRecoveryWhenClosureNotConfirmed() {
        stubLookup();
        given(deliveryGateway.create(any())).willThrow(timeout());
        given(deliveryGateway.isCreationClosed(any())).willReturn(false).willThrow(timeout());

        assertThatThrownBy(() -> service.create(user, "test-key", command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        assertThatThrownBy(() -> service.create(user, "test-key-2", command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);

        verify(orderWriter, times(2)).requireRecovery(any(), eq(null), eq(CreationFailureReason.DELIVERY_RESULT_UNKNOWN));
        verify(orderWriter, never()).abort(any(), any(), any(), any());
    }

    @Test
    @DisplayName("삭제 보상이 실패하면 I-17을 확인하지 않고 복구 필요로 남긴다")
    void skipsClosureCheckWhenCompensationFails() {
        stubLookup();
        given(deliveryGateway.create(any())).willThrow(timeout());
        willThrow(timeout()).given(deliveryGateway).deleteByOrderId(any());

        assertThatThrownBy(() -> service.create(user, "test-key", command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);

        verify(deliveryGateway, never()).isCreationClosed(any());
        verify(orderWriter).requireRecovery(any(), eq(null), eq(CreationFailureReason.DELIVERY_RESULT_UNKNOWN));
    }

    @Test
    @DisplayName("배송 응답에 ID가 없으면 주문을 저장하지 않고 생성 여부가 불명확한 배송을 보상한다")
    void compensatesMissingDeliveryId() {
        stubLookup();
        given(deliveryGateway.create(any())).willReturn(null);

        assertThatThrownBy(() -> service.create(user, "test-key", command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        UUID orderId = captureCreatedOrderId();
        verify(deliveryGateway).deleteByOrderId(orderId);
        verify(orderWriter, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("배송 성공 후 주문 저장 실패는 배송을 삭제하고 INTERNAL_SERVER_ERROR로 반환한다")
    void compensatesOrderSaveFailure() {
        stubDeliveryCreated();
        given(orderWriter.save(any(), any(), any())).willThrow(new DataIntegrityViolationException("DB 저장 실패"));
        given(orderWriter.resolveCreation(any())).willReturn(OrderCreationCommitResult.notCommitted());

        assertThatThrownBy(() -> service.create(user, "test-key", command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
        UUID orderId = captureCreatedOrderId();
        verify(deliveryGateway).deleteByOrderId(orderId);
    }

    @Test
    @DisplayName("배송 생성 후 주문 금액 규칙 위반도 보상하고 원래 비즈니스 오류를 유지한다")
    void compensatesEntityValidationFailure() {
        stubCompanies();
        given(companyReader.getProduct(product.productId())).willReturn(
                new ProductResult(product.productId(), supplier.companyId(), product.name(), Long.MAX_VALUE));
        given(deliveryGateway.create(any())).willReturn(deliveryId);
        given(orderWriter.resolveCreation(any())).willReturn(OrderCreationCommitResult.notCommitted());

        assertThatThrownBy(() -> service.create(user, "test-key", command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        UUID orderId = captureCreatedOrderId();
        verify(deliveryGateway).deleteByOrderId(orderId);
        verify(orderWriter, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("배송 삭제 보상은 실패하면 같은 orderId로 총 3회까지 시도한다")
    void retriesCompensationUntilSuccess() {
        stubDeliveryCreated();
        given(orderWriter.save(any(), any(), any())).willThrow(new DataIntegrityViolationException("DB 저장 실패"));
        given(orderWriter.resolveCreation(any())).willReturn(OrderCreationCommitResult.notCommitted());
        willThrow(timeout()).willThrow(timeout()).willDoNothing().given(deliveryGateway).deleteByOrderId(any());

        assertThatThrownBy(() -> service.create(user, "test-key", command))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
        UUID orderId = captureCreatedOrderId();
        verify(deliveryGateway, times(3)).deleteByOrderId(orderId);
    }

    @Test
    @DisplayName("보상 3회가 모두 실패해도 최초 오류를 유지하고 orderId와 실패 상태를 ERROR 로그에 남긴다")
    void logsExhaustedCompensationWithoutMaskingFailure(CapturedOutput output) {
        stubLookup();
        BusinessException failure = new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        given(deliveryGateway.create(any())).willThrow(failure);
        willThrow(timeout()).given(deliveryGateway).deleteByOrderId(any());

        assertThatThrownBy(() -> service.create(user, "test-key", command)).extracting("errorCode").isEqualTo(failure.getErrorCode());

        UUID orderId = captureCreatedOrderId();
        verify(deliveryGateway, times(3)).deleteByOrderId(orderId);
        assertThat(output.getAll()).contains("ERROR", orderId.toString(), "COMPENSATION_FAILED");
    }

    private void stubCompanies() {
        given(companyReader.getCompany(supplier.companyId())).willReturn(supplier);
        given(companyReader.getCompany(receiver.companyId())).willReturn(receiver);
    }

    private void stubLookup() {
        stubCompanies();
        given(companyReader.getProduct(product.productId())).willReturn(product);
    }

    private void stubDeliveryCreated() {
        stubLookup();
        given(deliveryGateway.create(any())).willReturn(deliveryId);
    }

    private void stubSuccess() {
        stubDeliveryCreated();
        given(orderWriter.save(any(), any(), any())).willAnswer(invocation -> OrderResult.from(invocation.getArgument(0, Order.class)));
    }

    private UUID captureCreatedOrderId() {
        ArgumentCaptor<CreateDeliveryCommand> command = ArgumentCaptor.forClass(CreateDeliveryCommand.class);
        verify(deliveryGateway).create(command.capture());
        return command.getValue().orderId();
    }

    private static BusinessException notFound(String code) {
        return new BusinessException(new SimpleErrorCode(HttpStatus.NOT_FOUND, code, "리소스가 없습니다."));
    }

    private static RetryableException timeout() {
        return new RetryableException(-1, "Read timed out", Request.HttpMethod.POST, (Long) null,
                Request.create(Request.HttpMethod.POST, "http://test-service/internal/resource",
                        Map.of(), null, StandardCharsets.UTF_8, null));
    }
}
