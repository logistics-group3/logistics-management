package com.sparta.logistics.order.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.CompanyResult;
import com.sparta.logistics.order.application.dto.CreateDeliveryCommand;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.dto.ProductResult;
import com.sparta.logistics.order.application.dto.UpdateOrderCommand;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderRepository;
import feign.FeignException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 주문 쓰기 흐름. 생성의 외부 호출과 수정의 로컬 트랜잭션을 구분한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int COMPENSATION_MAX_ATTEMPTS = 3;

    private final CompanyReader companyReader;
    private final DeliveryGateway deliveryGateway;
    private final OrderWriter orderWriter;
    private final OrderRepository orderRepository;

    /** O-01. 요청업체 관리자 또는 MASTER만 주문하며 배송 생성·주문 저장 실패를 단계별로 보상한다. */
    @Transactional(propagation = Propagation.NEVER)
    public OrderResult create(UserContext user, CreateOrderCommand command) {
        // Note: 원격 호출을 DB 트랜잭션 안에 두지 않는다. 저장은 OrderWriter의 독립 트랜잭션으로만 수행한다.
        UUID orderId = UUID.randomUUID();
        CompanyResult supplier;
        CompanyResult receiver;
        ProductResult product;
        try {
            supplier = companyReader.getCompany(command.supplierCompanyId());
            receiver = companyReader.getCompany(command.receiverCompanyId());
            user.validateSelfOrMaster(receiver.managerId());
            product = companyReader.getProduct(command.productId());
        } catch (FeignException exception) {
            log.warn("업체·상품 조회 통신 실패: orderId={}", orderId, exception);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        if (!command.supplierCompanyId().equals(product.companyId())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "상품이 공급업체 소속이 아닙니다.");
        }

        UUID deliveryId = createDelivery(new CreateDeliveryCommand(orderId, command.supplierCompanyId(),
                command.receiverCompanyId(), supplier.hubId(), receiver.hubId(), supplier.address(), receiver.address(),
                receiver.name(), receiver.managerId()));
        try {
            Order order = Order.create(orderId, user.userId(), command.supplierCompanyId(), command.receiverCompanyId(),
                    command.productId(), product.name(), product.price(), command.quantity(), deliveryId, command.requestNote());
            return orderWriter.save(order, supplier.name(), receiver.name());
        } catch (BusinessException exception) {
            compensateDelivery(orderId);
            throw exception;
        } catch (RuntimeException exception) {
            log.error("주문 저장 실패: orderId={}", orderId, exception);
            compensateDelivery(orderId);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /** O-04. 주문자 본인 또는 MASTER만 요청사항을 수정하며 취소된 주문은 거부한다. */
    @Transactional
    public OrderResult updateRequestNote(UserContext user, UUID orderId, UpdateOrderCommand command) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        user.validateSelfOrMaster(order.getOrdererId());
        order.updateRequestNote(command.requestNote());
        // Note: 변경 감지와 Audit 갱신을 먼저 실행해 응답에도 이번 수정의 updatedAt을 담는다. 커밋은 메서드 종료 후다.
        orderRepository.flush();
        return OrderResult.from(order);
    }

    private UUID createDelivery(CreateDeliveryCommand command) {
        try {
            UUID deliveryId = deliveryGateway.create(command);
            if (deliveryId == null) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "배송 생성 응답에 ID가 없습니다.");
            }
            return deliveryId;
        } catch (BusinessException exception) {
            if (CommonErrorCode.SERVICE_UNAVAILABLE.code().equals(exception.getErrorCode().code())) {
                compensateDelivery(command.orderId());
            }
            throw exception;
        } catch (FeignException exception) {
            // Note: 타임아웃은 생성 실패를 뜻하지 않는다. 선발급 orderId로 결과를 모르는 배송도 멱등 삭제한다.
            log.warn("배송 생성 통신 실패: orderId={}", command.orderId(), exception);
            compensateDelivery(command.orderId());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private void compensateDelivery(UUID orderId) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= COMPENSATION_MAX_ATTEMPTS; attempt++) {
            try {
                deliveryGateway.deleteByOrderId(orderId);
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn("배송 삭제 보상 시도 실패: orderId={}, attempt={}", orderId, attempt);
            }
        }
        // Note: 보상 실패가 최초 실패 원인을 덮지 않게 한다. SA 7.1에 따라 orderId를 남겨 수동 재처리한다.
        log.error("배송 삭제 보상 실패: orderId={}, status=COMPENSATION_FAILED, attempts={}",
                orderId, COMPENSATION_MAX_ATTEMPTS, lastFailure);
    }
}
