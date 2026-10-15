package com.sparta.logistics.order.application;

import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderWriter {

    private final OrderRepository orderRepository;

    /**
     * O-01 주문의 로컬 저장. 원격 호출을 포함하지 않는다.
     * Note: 별도 빈의 트랜잭션 프록시가 커밋까지 끝낸 뒤 반환하므로 호출자가 커밋 실패도 보상할 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderResult save(Order order) {
        return OrderResult.from(orderRepository.saveAndFlush(order));
    }
}
