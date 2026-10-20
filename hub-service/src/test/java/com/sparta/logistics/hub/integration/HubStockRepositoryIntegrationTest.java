package com.sparta.logistics.hub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.InvalidSortException;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.hub.domain.Hub;
import com.sparta.logistics.hub.domain.HubStock;
import com.sparta.logistics.hub.domain.HubStockHistory;
import com.sparta.logistics.hub.domain.HubStockHistoryRepository;
import com.sparta.logistics.hub.domain.HubStockRepository;
import com.sparta.logistics.hub.domain.StockChangeType;
import com.sparta.logistics.hub.infrastructure.persistence.HubJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 재고 검색, 락 조회 순서, CHECK·부분 유니크 제약과 이력 멱등키를 확인한다. */
// Note: 컨테이너 DB를 다른 통합 테스트와 공유하므로 클래스 전체를 트랜잭션으로 묶어 테스트가 끝나면 롤백한다.
@Transactional
class HubStockRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private HubStockRepository stockRepository;
    @Autowired
    private HubStockHistoryRepository historyRepository;
    @Autowired
    private HubJpaRepository hubRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private Hub hubA;
    private Hub hubB;
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        List<Hub> hubs = hubRepository.findAll();
        hubA = hubs.get(0);
        hubB = hubs.get(1);
    }

    private HubStock stock(Hub hub, UUID product, int quantity) {
        HubStock stock = HubStock.create(hub.getId(), product);
        if (quantity > 0) {
            stock.increase(quantity);
        }
        return stockRepository.saveAndFlush(stock);
    }

    private void clear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("허브의 재고를 상품 필터와 수량 정렬로 검색한다")
    void search() {
        UUID other = UUID.randomUUID();
        stock(hubA, productId, 30);
        stock(hubA, other, 10);
        stock(hubB, productId, 99);
        clear();

        Page<HubStock> all = stockRepository.search(hubA.getId(), null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "quantity")));
        Page<HubStock> filtered = stockRepository.search(hubA.getId(), productId,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(all.getContent()).extracting(HubStock::getQuantity).containsExactly(10, 30);
        assertThat(all.getTotalElements()).isEqualTo(2);
        assertThat(filtered.getContent()).extracting(HubStock::getProductId).containsExactly(productId);
    }

    @Test
    @DisplayName("재고가 없는 허브는 빈 페이지이고 논리 삭제된 재고는 제외된다")
    void searchEmptyAndDeleted() {
        HubStock deleted = stock(hubA, productId, 5);
        deleted.softDelete("tester");
        clear();

        Page<HubStock> page = stockRepository.search(hubA.getId(), null, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("허용하지 않은 정렬 필드는 거절한다")
    void searchRejectsUnknownSort() {
        assertThatThrownBy(() -> stockRepository.search(hubA.getId(), null,
                PageRequest.of(0, 10, Sort.by("hubId"))))
                .isInstanceOf(InvalidSortException.class);
    }

    @Test
    @DisplayName("허브·상품이나 재고 ID로 재고 행을 잠가 조회하고 없으면 비어 있다")
    void lockStock() {
        HubStock stock = stock(hubA, productId, 3);
        clear();

        assertThat(stockRepository.lockByHubIdAndProductId(hubA.getId(), productId))
                .get().extracting(HubStock::getId).isEqualTo(stock.getId());
        assertThat(stockRepository.lockById(stock.getId())).isPresent();
        assertThat(stockRepository.lockByHubIdAndProductId(hubB.getId(), productId)).isEmpty();
        assertThat(stockRepository.lockById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("같은 허브·상품의 재고를 두 번 만들 수 없다")
    void uniqueHubProduct() {
        stock(hubA, productId, 1);

        assertThatThrownBy(() -> stock(hubA, productId, 1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("논리 삭제한 재고와 같은 허브·상품의 재고는 다시 만들 수 있다")
    void uniqueAllowsAfterSoftDelete() {
        HubStock old = stock(hubA, productId, 1);
        old.softDelete("tester");
        clear();

        HubStock recreated = stock(hubA, productId, 2);

        assertThat(recreated.getId()).isNotEqualTo(old.getId());
    }

    @Test
    @DisplayName("재고 수량은 DB CHECK로도 음수가 될 수 없다")
    void quantityCheck() {
        HubStock stock = stock(hubA, productId, 1);
        clear();

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE p_hub_stocks SET quantity = -1 WHERE hub_stock_id = ?", stock.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 주문이 같은 재고를 두 번 차감하는 이력은 DB가 막는다")
    void historyIdempotencyKey() {
        HubStock stock = stock(hubA, productId, 10);
        UUID orderId = UUID.randomUUID();
        stock.decrease(3);
        historyRepository.save(HubStockHistory.decrease(stock, orderId, 3));
        entityManager.flush();

        // Note: EntityManager.flush()는 Spring 예외 변환을 거치지 않아 Hibernate의 ConstraintViolationException(PersistenceException)이 그대로 올라온다.
        assertThatThrownBy(() -> {
            historyRepository.save(HubStockHistory.decrease(stock, orderId, 3));
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("같은 주문의 차감 이력과 복원 이력은 함께 있을 수 있고 입고 이력은 여러 번 쌓인다")
    void historyAllowsDifferentTypesAndRepeatedInbound() {
        HubStock stock = stock(hubA, productId, 0);
        UUID orderId = UUID.randomUUID();
        stock.increase(10);
        historyRepository.save(HubStockHistory.inbound(stock, 10));
        stock.increase(5);
        historyRepository.save(HubStockHistory.inbound(stock, 5));
        stock.decrease(4);
        historyRepository.save(HubStockHistory.decrease(stock, orderId, 4));
        stock.increase(4);
        historyRepository.save(HubStockHistory.restore(stock, orderId, 4));
        clear();

        List<HubStockHistory> decreases = historyRepository.findAllByOrderIdAndChangeType(orderId, StockChangeType.DECREASE);

        assertThat(decreases).hasSize(1);
        assertThat(decreases.get(0).getQuantity()).isEqualTo(4);
        assertThat(decreases.get(0).getQuantityAfter()).isEqualTo(11);
        assertThat(decreases.get(0).getCreatedBy()).isNotBlank();
        assertThat(historyRepository.findAllByOrderIdAndChangeType(orderId, StockChangeType.RESTORE)).hasSize(1);
        assertThat(historyRepository.findByHubStockIdAndOrderIdAndChangeType(stock.getId(), orderId, StockChangeType.DECREASE))
                .hasValueSatisfying(history -> assertThat(history.getQuantity()).isEqualTo(4));
        assertThat(historyRepository.existsByHubStockIdAndOrderIdAndChangeType(stock.getId(), orderId, StockChangeType.RESTORE)).isTrue();
        assertThat(historyRepository.existsByHubStockIdAndOrderIdAndChangeType(stock.getId(), UUID.randomUUID(), StockChangeType.RESTORE)).isFalse();
    }
}
