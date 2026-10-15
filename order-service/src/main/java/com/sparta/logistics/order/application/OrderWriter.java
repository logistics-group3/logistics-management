package com.sparta.logistics.order.application;

import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.event.OrderCreatedEvent;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderWriter {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * O-01 주문의 로컬 저장. 원격 호출을 포함하지 않는다.
     * Note: 별도 빈의 트랜잭션 프록시가 커밋까지 끝낸 뒤 반환하므로 호출자가 커밋 실패도 보상할 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderResult save(Order order, String supplierCompanyName, String receiverCompanyName) {
        Order savedOrder = orderRepository.saveAndFlush(order);
        // Note: flush는 커밋이 아니다. 트랜잭션 안에서 발행해 커밋 성공 시에만 리스너가 실행되게 한다.
        eventPublisher.publishEvent(OrderCreatedEvent.from(savedOrder, supplierCompanyName, receiverCompanyName));
        return OrderResult.from(savedOrder);
    }
}
