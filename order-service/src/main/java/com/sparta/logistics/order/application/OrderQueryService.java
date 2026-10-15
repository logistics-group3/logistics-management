package com.sparta.logistics.order.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.dto.OrderSearchCondition;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderQueryRepository;
import com.sparta.logistics.order.domain.OrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    /** O-02. USER의 본인 주문 조건은 내용 조회와 전체 건수 계산에 함께 적용한다. */
    public Page<OrderResult> search(UserContext user, OrderSearchCondition condition, Pageable pageable) {
        // Note: 조회 후 필터링하면 페이지와 totalElements에 다른 사용자의 주문이 섞일 수 있다.
        UUID ordererId = user.isMaster() ? null : user.userId();
        return orderQueryRepository.search(condition.forOrderer(ordererId), pageable).map(OrderResult::from);
    }

    /** O-03. 없거나 삭제된 주문은 404, 다른 사용자의 주문은 403으로 처리한다. */
    public OrderResult getOrder(UserContext user, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        user.validateSelfOrMaster(order.getOrdererId());
        return OrderResult.from(order);
    }
}
