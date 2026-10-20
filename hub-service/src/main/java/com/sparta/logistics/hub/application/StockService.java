package com.sparta.logistics.hub.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.hub.application.dto.AllocateStockCommand;
import com.sparta.logistics.hub.application.dto.InboundStockCommand;
import com.sparta.logistics.hub.application.dto.StockAllocationResult;
import com.sparta.logistics.hub.application.dto.StockResult;
import com.sparta.logistics.hub.application.dto.StockRestoreResult;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고를 바꾸는 로컬 트랜잭션 단위(SA 7.3). 메서드 하나가 한 트랜잭션이라 재고 수량과 이력이 함께 커밋되거나 함께 롤백된다.
 * 다른 서비스 호출이나 생성 경합 재시도는 {@link StockRegistrationService}가 트랜잭션 밖에서 조율한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final HubStockRepository stockRepository;
    private final HubStockHistoryRepository historyRepository;
    private final HubRepository hubRepository;

    /** I-04 재고 초기화. 수량 0으로 만들고, 이미 있으면 기존 값을 그대로 돌려준다(멱등). */
    @Transactional
    public StockResult createIfAbsent(UUID hubId, UUID productId) {
        HubStock stock = stockRepository.findByHubIdAndProductId(hubId, productId)
                .orElseGet(() -> stockRepository.saveAndFlush(HubStock.create(hubId, productId)));
        return StockResult.from(stock);
    }

    /** H-06 입고. 재고 행을 잠근 채 수량을 늘리고 INBOUND 이력을 남긴다. 행이 없으면 만든다. */
    @Transactional
    public StockResult inbound(InboundStockCommand command) {
        HubStock stock = lockByHubAndProduct(command.hubId(), command.productId())
                .orElseGet(() -> stockRepository.saveAndFlush(HubStock.create(command.hubId(), command.productId())));
        stock.increase(command.quantity());
        historyRepository.save(HubStockHistory.inbound(stock, command.quantity()));
        return StockResult.from(stock);
    }

    /**
     * I-05 재고 할당. 공급업체 허브의 재고 한 행만 잠그고 차감한다(ADR 0003). 수량이 모자라거나 재고 행이 없으면
     * 409 STOCK_INSUFFICIENT이고, 같은 주문의 재요청은 수량이 달라도 차감하지 않고 처음 차감한 결과를 돌려준다.
     * 같은 orderId에는 같은 요청을 보낸다는 order와의 계약을 전제로 한다.
     */
    @Transactional
    public StockAllocationResult allocate(AllocateStockCommand command) {
        // 재고 행이 있으면 hub_id FK로 허브도 있으므로, 행이 없을 때만 허브를 확인해 오류를 구분한다.
        HubStock stock = lockByHubAndProduct(command.supplierHubId(), command.productId())
                .orElseThrow(() -> missingStockException(command.supplierHubId()));

        // Note: 멱등 확인은 잠금 뒤에 한다. 같은 주문이 동시에 오면 둘째는 락을 기다린 뒤 첫째의 DECREASE 이력을 보고 중복 차감 없이 반환한다.
        return historyRepository.findByHubStockIdAndOrderIdAndChangeType(stock.getId(), command.orderId(), StockChangeType.DECREASE)
                .map(history -> existingAllocation(stock, command.orderId(), history))
                .orElseGet(() -> decrease(stock, command));
    }

    /**
     * 이미 할당한 주문이면 처음 결과를 돌려준다. 복원까지 끝난 주문은 한 주문 ID에 할당과 복원이 한 번씩이라는 규칙에 따라
     * 거부한다. 그대로 두면 200을 반환하고도 재고는 차감되지 않는다.
     */
    private StockAllocationResult existingAllocation(HubStock stock, UUID orderId, HubStockHistory decreased) {
        if (isRestored(orderId, stock)) {
            throw new BusinessException(HubErrorCode.STOCK_ALREADY_RESTORED);
        }
        return new StockAllocationResult(stock.getHubId(), decreased.getQuantity());
    }

    /**
     * I-06 재고 복원. 주문의 차감분을 되돌린다. 차감 이력이 없거나 이미 복원했으면 0이다.
     * order가 할당 응답을 받은 뒤에 복원을 요청한다는 순서를 전제로 하므로 차감 이력은 잠금 전에 읽는다.
     */
    @Transactional
    public StockRestoreResult restore(UUID orderId) {
        List<HubStockHistory> decreased = historyRepository.findAllByOrderIdAndChangeType(orderId, StockChangeType.DECREASE);
        int restored = 0;
        for (HubStockHistory history : decreased) {
            // Note: 행을 잠근 뒤에 복원 이력을 확인한다. 같은 주문의 복원이 동시에 와도 둘째는 이미 복원된 것으로 보고 0을 반환한다.
            restored += lockStock(history.getHubStockId())
                    .map(stock -> restoreOne(stock, orderId, history.getQuantity()))
                    .orElseGet(() -> skipMissingStock(orderId, history));
        }
        return new StockRestoreResult(restored);
    }

    /** 이미 복원한 재고면 0을 돌려주고, 아니면 되돌린 수량을 돌려준다. */
    private int restoreOne(HubStock stock, UUID orderId, int quantity) {
        if (isRestored(orderId, stock)) {
            return 0;
        }
        stock.increase(quantity);
        historyRepository.save(HubStockHistory.restore(stock, orderId, quantity));
        return quantity;
    }

    private int skipMissingStock(UUID orderId, HubStockHistory history) {
        log.warn("차감 이력의 재고 행이 없어 복원을 건너뜁니다. orderId={}, hubStockId={}", orderId, history.getHubStockId());
        return 0;
    }

    private StockAllocationResult decrease(HubStock stock, AllocateStockCommand command) {
        stock.decrease(command.quantity());
        historyRepository.save(HubStockHistory.decrease(stock, command.orderId(), command.quantity()));
        return new StockAllocationResult(stock.getHubId(), command.quantity());
    }

    private boolean isRestored(UUID orderId, HubStock stock) {
        return historyRepository.existsByHubStockIdAndOrderIdAndChangeType(stock.getId(), orderId, StockChangeType.RESTORE);
    }

    private BusinessException missingStockException(UUID supplierHubId) {
        return hubRepository.findById(supplierHubId).isPresent()
                ? new BusinessException(HubErrorCode.STOCK_INSUFFICIENT)
                : new BusinessException(HubErrorCode.HUB_NOT_FOUND);
    }

    private Optional<HubStock> lockByHubAndProduct(UUID hubId, UUID productId) {
        try {
            return stockRepository.lockByHubIdAndProductId(hubId, productId);
        } catch (PessimisticLockingFailureException e) {
            throw lockUnavailable(e);
        }
    }

    private Optional<HubStock> lockStock(UUID stockId) {
        try {
            return stockRepository.lockById(stockId);
        } catch (PessimisticLockingFailureException e) {
            throw lockUnavailable(e);
        }
    }

    /**
     * Note: 락 대기 시간 제한(config-repo/hub-service.yml의 lock_timeout)을 넘긴 요청은 기다리지 않고 503으로 돌려보낸다.
     * 한 재고 행에 요청이 몰릴 때 대기 요청이 DB 연결을 모두 잡아 다른 API까지 막는 상황을 막는다.
     */
    private BusinessException lockUnavailable(PessimisticLockingFailureException e) {
        log.warn("재고 행 락 대기 시간 초과", e);
        return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
    }
}
