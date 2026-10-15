package com.sparta.logistics.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.OrderResult;
import com.sparta.logistics.order.application.dto.OrderSearchCondition;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderErrorCode;
import com.sparta.logistics.order.domain.OrderQueryRepository;
import com.sparta.logistics.order.domain.OrderRepository;
import com.sparta.logistics.order.domain.OrderSearchCriteria;
import com.sparta.logistics.order.domain.OrderStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderQueryRepository orderQueryRepository;

    private final UserContext user = new UserContext(UUID.randomUUID(), "junhyun", Role.USER, false);
    private final UserContext master = new UserContext(UUID.randomUUID(), "master01", Role.MASTER, false);
    private final PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        service = new OrderQueryService(orderRepository, orderQueryRepository);
    }

    @Test
    @DisplayName("주문자는 저장된 상품·가격 스냅샷을 단건 조회한다")
    void ownerReadsSnapshot() {
        Order order = order(user.userId());
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

        assertThat(service.getOrder(user, order.getId())).isEqualTo(OrderResult.from(order));
        verifyNoInteractions(orderQueryRepository);
    }

    @Test
    @DisplayName("MASTER는 다른 사용자의 주문도 단건 조회한다")
    void masterReadsAnotherUsersOrder() {
        Order order = order(user.userId());
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

        assertThat(service.getOrder(master, order.getId()).ordererId()).isEqualTo(user.userId());
    }

    @Test
    @DisplayName("주문자가 아닌 USER는 단건 조회에서 FORBIDDEN으로 거절한다")
    void rejectsNonOwner() {
        Order order = order(UUID.randomUUID());
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getOrder(user, order.getId()))
                .extracting("errorCode").isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("없는 주문은 ORDER_NOT_FOUND로 처리한다")
    void rejectsMissingOrder() {
        UUID id = UUID.randomUUID();
        given(orderRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(user, id))
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("USER 검색은 인증된 주문자 조건을 저장소에 넣고 페이지 전체 건수를 보존한다")
    void restrictsUserSearchInDatabaseCriteria() {
        Order order = order(user.userId());
        OrderSearchCondition condition = new OrderSearchCondition(OrderStatus.ORDERED, order.getSupplierCompanyId(),
                order.getReceiverCompanyId(), order.getProductId(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        given(orderQueryRepository.search(any(), eq(pageable))).willReturn(new PageImpl<>(List.of(order), pageable, 17));

        Page<OrderResult> result = service.search(user, condition, pageable);

        ArgumentCaptor<OrderSearchCriteria> criteria = ArgumentCaptor.forClass(OrderSearchCriteria.class);
        verify(orderQueryRepository).search(criteria.capture(), eq(pageable));
        assertThat(criteria.getValue()).isEqualTo(new OrderSearchCriteria(user.userId(), OrderStatus.ORDERED,
                order.getSupplierCompanyId(), order.getReceiverCompanyId(), order.getProductId(),
                LocalDate.of(2026, 10, 1).atStartOfDay(), LocalDate.of(2026, 11, 1).atStartOfDay()));
        assertThat(result.getContent()).containsExactly(OrderResult.from(order));
        assertThat(result.getTotalElements()).isEqualTo(17);
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("MASTER 검색은 주문자 제한 없이 요청한 검색 조건을 적용한다")
    void masterSearchesAcrossOwners() {
        OrderSearchCondition condition = new OrderSearchCondition(null, null, null, null, null, null);
        given(orderQueryRepository.search(any(), eq(pageable))).willReturn(Page.empty(pageable));

        service.search(master, condition, pageable);

        verify(orderQueryRepository).search(new OrderSearchCriteria(null, null, null, null, null, null, null), pageable);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 저장소에 접근하기 전에 거절한다")
    void rejectsReversedDateRange() {
        assertThatThrownBy(() -> service.search(user, new OrderSearchCondition(null, null, null, null,
                LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 15)), pageable))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
        verifyNoInteractions(orderRepository, orderQueryRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10000, 999999999})
    @DisplayName("yyyy-MM-dd 범위를 벗어나는 연도는 날짜 계산·DB 조회 전에 거절한다")
    void rejectsOutOfRangeYear(int year) {
        assertThatThrownBy(() -> new OrderSearchCondition(null, null, null, null, null, LocalDate.of(year, 12, 31)))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    private Order order(UUID owner) {
        return Order.create(UUID.randomUUID(), owner, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "주문 당시 상품", 12_000L, 50, UUID.randomUUID(), "오전 도착 희망");
    }
}
