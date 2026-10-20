package com.sparta.logistics.hub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.hub.HubFixture;
import com.sparta.logistics.hub.application.dto.AllocateStockCommand;
import com.sparta.logistics.hub.application.dto.InboundStockCommand;
import com.sparta.logistics.hub.application.dto.StockAllocationResult;
import com.sparta.logistics.hub.application.dto.StockResult;
import com.sparta.logistics.hub.domain.Hub;
import com.sparta.logistics.hub.domain.HubErrorCode;
import com.sparta.logistics.hub.domain.HubRepository;
import com.sparta.logistics.hub.domain.HubStock;
import com.sparta.logistics.hub.domain.HubStockHistory;
import com.sparta.logistics.hub.domain.HubStockHistoryRepository;
import com.sparta.logistics.hub.domain.HubStockRepository;
import com.sparta.logistics.hub.domain.StockChangeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private HubStockRepository stockRepository;
    @Mock
    private HubStockHistoryRepository historyRepository;
    @Mock
    private HubRepository hubRepository;
    @InjectMocks
    private StockService stockService;

    private final UUID productId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final Hub supplierHub = hub();
    private final Hub otherHub = hub();

    private static Hub hub() {
        Hub hub = HubFixture.hub("허브", "주소");
        ReflectionTestUtils.setField(hub, "id", UUID.randomUUID());
        return hub;
    }

    private HubStock stock(Hub hub, int quantity) {
        HubStock stock = HubStock.create(hub.getId(), productId);
        ReflectionTestUtils.setField(stock, "id", UUID.randomUUID());
        if (quantity > 0) {
            stock.increase(quantity);
        }
        return stock;
    }

    private AllocateStockCommand allocateCommand(int quantity) {
        return new AllocateStockCommand(orderId, productId, quantity, supplierHub.getId());
    }

    private void givenSupplierHubWithStock(HubStock stock) {
        given(stockRepository.lockByHubIdAndProductId(supplierHub.getId(), productId)).willReturn(Optional.of(stock));
    }

    private void givenHistory(HubStock stock, StockChangeType type, HubStockHistory history) {
        given(historyRepository.findByHubStockIdAndOrderIdAndChangeType(stock.getId(), orderId, type))
                .willReturn(Optional.of(history));
    }

    @Test
    @DisplayName("재고 행 락 대기 시간을 넘기면 SERVICE_UNAVAILABLE이고 차감하지 않는다")
    void allocateLockTimeout() {
        given(stockRepository.lockByHubIdAndProductId(supplierHub.getId(), productId))
                .willThrow(new CannotAcquireLockException("lock timeout"));

        assertThatThrownBy(() -> stockService.allocate(allocateCommand(1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));

        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("할당은 공급업체 허브의 재고만 차감하고 DECREASE 이력을 남긴다")
    void allocate() {
        HubStock stock = stock(supplierHub, 10);
        givenSupplierHubWithStock(stock);

        StockAllocationResult result = stockService.allocate(allocateCommand(4));

        assertThat(result).isEqualTo(new StockAllocationResult(supplierHub.getId(), 4));
        assertThat(stock.getQuantity()).isEqualTo(6);
        ArgumentCaptor<HubStockHistory> history = ArgumentCaptor.forClass(HubStockHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getChangeType()).isEqualTo(StockChangeType.DECREASE);
        assertThat(history.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(history.getValue().getQuantity()).isEqualTo(4);
        assertThat(history.getValue().getQuantityAfter()).isEqualTo(6);
    }

    @Test
    @DisplayName("재고를 전부 차감할 수 있다")
    void allocateAll() {
        HubStock stock = stock(supplierHub, 5);
        givenSupplierHubWithStock(stock);

        stockService.allocate(allocateCommand(5));

        assertThat(stock.getQuantity()).isZero();
    }

    @Test
    @DisplayName("공급업체 허브 재고가 모자라면 STOCK_INSUFFICIENT이고 수량과 이력은 그대로다")
    void allocateInsufficient() {
        HubStock stock = stock(supplierHub, 7);
        givenSupplierHubWithStock(stock);

        assertThatThrownBy(() -> stockService.allocate(allocateCommand(8)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HubErrorCode.STOCK_INSUFFICIENT));

        assertThat(stock.getQuantity()).isEqualTo(7);
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("공급업체 허브에 재고 행이 없으면 STOCK_INSUFFICIENT다")
    void allocateWithoutStockRow() {
        given(hubRepository.findById(supplierHub.getId())).willReturn(Optional.of(supplierHub));
        given(stockRepository.lockByHubIdAndProductId(supplierHub.getId(), productId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.allocate(allocateCommand(1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HubErrorCode.STOCK_INSUFFICIENT));

        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 주문을 다시 할당하면 차감하지 않고 처음 차감한 수량을 돌려준다")
    void allocateIdempotent() {
        HubStock stock = stock(supplierHub, 6);
        givenSupplierHubWithStock(stock);
        givenHistory(stock, StockChangeType.DECREASE, HubStockHistory.decrease(stock, orderId, 4));

        StockAllocationResult result = stockService.allocate(allocateCommand(4));

        assertThat(result).isEqualTo(new StockAllocationResult(supplierHub.getId(), 4));
        assertThat(stock.getQuantity()).isEqualTo(6);
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("복원까지 끝난 주문을 다시 할당하면 STOCK_ALREADY_RESTORED이고 차감하지 않는다")
    void allocateAfterRestore() {
        HubStock stock = stock(supplierHub, 10);
        givenSupplierHubWithStock(stock);
        givenHistory(stock, StockChangeType.DECREASE, HubStockHistory.decrease(stock, orderId, 4));
        given(historyRepository.existsByHubStockIdAndOrderIdAndChangeType(stock.getId(), orderId, StockChangeType.RESTORE))
                .willReturn(true);

        assertThatThrownBy(() -> stockService.allocate(allocateCommand(4)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HubErrorCode.STOCK_ALREADY_RESTORED));

        assertThat(stock.getQuantity()).isEqualTo(10);
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("재고 행이 없고 공급업체 허브도 없으면 HUB_NOT_FOUND다")
    void allocateSupplierHubNotFound() {
        given(stockRepository.lockByHubIdAndProductId(supplierHub.getId(), productId)).willReturn(Optional.empty());
        given(hubRepository.findById(supplierHub.getId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.allocate(allocateCommand(1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HubErrorCode.HUB_NOT_FOUND));

        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("복원은 주문의 차감분을 되돌리고 RESTORE 이력을 남긴다")
    void restore() {
        HubStock stock = stock(supplierHub, 6);
        UUID stockId = stock.getId();
        given(historyRepository.findAllByOrderIdAndChangeType(orderId, StockChangeType.DECREASE))
                .willReturn(List.of(HubStockHistory.decrease(stock, orderId, 4)));
        given(stockRepository.lockById(stockId)).willReturn(Optional.of(stock));

        assertThat(stockService.restore(orderId).restoredQuantity()).isEqualTo(4);

        assertThat(stock.getQuantity()).isEqualTo(10);
        ArgumentCaptor<HubStockHistory> history = ArgumentCaptor.forClass(HubStockHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getChangeType()).isEqualTo(StockChangeType.RESTORE);
        assertThat(history.getValue().getQuantityAfter()).isEqualTo(10);
    }

    @Test
    @DisplayName("차감 이력이 없는 주문의 복원은 0이고 아무것도 잠그지 않는다")
    void restoreWithoutDecrease() {
        given(historyRepository.findAllByOrderIdAndChangeType(orderId, StockChangeType.DECREASE)).willReturn(List.of());

        assertThat(stockService.restore(orderId).restoredQuantity()).isZero();

        verify(stockRepository, never()).lockById(any());
    }

    @Test
    @DisplayName("이미 복원한 재고는 다시 복원하지 않아 0이다")
    void restoreAlreadyRestored() {
        HubStock stock = stock(supplierHub, 10);
        given(historyRepository.findAllByOrderIdAndChangeType(orderId, StockChangeType.DECREASE))
                .willReturn(List.of(HubStockHistory.decrease(stock, orderId, 4)));
        given(stockRepository.lockById(stock.getId())).willReturn(Optional.of(stock));
        given(historyRepository.existsByHubStockIdAndOrderIdAndChangeType(stock.getId(), orderId, StockChangeType.RESTORE))
                .willReturn(true);

        assertThat(stockService.restore(orderId).restoredQuantity()).isZero();

        assertThat(stock.getQuantity()).isEqualTo(10);
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("입고는 잠근 재고 행의 수량을 늘리고 INBOUND 이력을 남긴다")
    void inboundExisting() {
        HubStock existing = stock(otherHub, 10);
        given(stockRepository.lockByHubIdAndProductId(otherHub.getId(), productId)).willReturn(Optional.of(existing));

        StockResult result = stockService.inbound(new InboundStockCommand(otherHub.getId(), productId, 500));

        assertThat(result.quantity()).isEqualTo(510);
        ArgumentCaptor<HubStockHistory> history = ArgumentCaptor.forClass(HubStockHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getChangeType()).isEqualTo(StockChangeType.INBOUND);
        assertThat(history.getValue().getOrderId()).isNull();
        assertThat(history.getValue().getQuantityAfter()).isEqualTo(510);
    }

    @Test
    @DisplayName("재고 레코드가 없는 허브에 입고하면 새로 만들어 수량을 채운다")
    void inboundCreatesStock() {
        given(stockRepository.lockByHubIdAndProductId(otherHub.getId(), productId)).willReturn(Optional.empty());
        given(stockRepository.saveAndFlush(any(HubStock.class))).willAnswer(invocation -> {
            HubStock created = invocation.getArgument(0);
            ReflectionTestUtils.setField(created, "id", UUID.randomUUID());
            return created;
        });

        StockResult result = stockService.inbound(new InboundStockCommand(otherHub.getId(), productId, 30));

        assertThat(result.quantity()).isEqualTo(30);
        assertThat(result.hubId()).isEqualTo(otherHub.getId());
        verify(historyRepository, times(1)).save(any(HubStockHistory.class));
    }

    @Test
    @DisplayName("재고 초기화는 이미 있으면 기존 값을 돌려주고 새로 만들지 않는다")
    void createIfAbsentExisting() {
        HubStock existing = stock(otherHub, 7);
        given(stockRepository.findByHubIdAndProductId(otherHub.getId(), productId)).willReturn(Optional.of(existing));

        StockResult result = stockService.createIfAbsent(otherHub.getId(), productId);

        assertThat(result.quantity()).isEqualTo(7);
        assertThat(result.hubStockId()).isEqualTo(existing.getId());
        verify(stockRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("재고 초기화는 없으면 수량 0으로 만든다")
    void createIfAbsentNew() {
        given(stockRepository.findByHubIdAndProductId(otherHub.getId(), productId)).willReturn(Optional.empty());
        given(stockRepository.saveAndFlush(any(HubStock.class))).willAnswer(invocation -> invocation.getArgument(0));

        StockResult result = stockService.createIfAbsent(otherHub.getId(), productId);

        assertThat(result.quantity()).isZero();
        assertThat(result.productId()).isEqualTo(productId);
    }
}
