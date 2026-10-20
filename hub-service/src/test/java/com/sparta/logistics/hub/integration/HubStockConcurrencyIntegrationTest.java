package com.sparta.logistics.hub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.hub.application.StockService;
import com.sparta.logistics.hub.application.dto.AllocateStockCommand;
import com.sparta.logistics.hub.application.dto.InboundStockCommand;
import com.sparta.logistics.hub.application.dto.StockAllocationResult;
import com.sparta.logistics.hub.domain.Hub;
import com.sparta.logistics.hub.domain.HubErrorCode;
import com.sparta.logistics.hub.domain.HubStock;
import com.sparta.logistics.hub.domain.HubStockRepository;
import com.sparta.logistics.hub.infrastructure.persistence.HubJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 실제 PostgreSQL에서 여러 스레드가 같은 재고를 동시에 바꿀 때의 정합성을 확인한다(SA 7.3, 7.4, ADR 0003).
 * Note: 락과 커밋이 실제로 일어나야 하므로 테스트 트랜잭션으로 묶지 않고, 끝나면 재고·이력 테이블을 직접 비운다.
 */
class HubStockConcurrencyIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private StockService stockService;
    @Autowired
    private HubStockRepository stockRepository;
    @Autowired
    private HubJpaRepository hubRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID productId = UUID.randomUUID();
    private Hub supplierHub;

    @BeforeEach
    void setUp() {
        cleanUp();
        supplierHub = hubRepository.findAll().get(0);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM p_hub_stock_histories");
        jdbcTemplate.update("DELETE FROM p_hub_stocks");
    }

    private HubStock stockWith(int quantity) {
        HubStock stock = HubStock.create(supplierHub.getId(), productId);
        if (quantity > 0) {
            stock.increase(quantity);
        }
        return stockRepository.saveAndFlush(stock);
    }

    private int quantityOf(HubStock stock) {
        return jdbcTemplate.queryForObject("SELECT quantity FROM p_hub_stocks WHERE hub_stock_id = ?", Integer.class, stock.getId());
    }

    private int historyCount(String changeType) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM p_hub_stock_histories WHERE change_type = ?", Integer.class, changeType);
    }

    /** 모든 작업을 같은 순간에 시작시키고 각 결과(값 또는 예외)를 모아 돌려준다. */
    private <T> List<Object> runConcurrently(int count, IntFunction<Callable<T>> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<T> work = task.apply(i);
                futures.add(executor.submit(() -> {
                    start.await();
                    return work.call();
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    results.add(e.getCause());
                }
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("재고 10개에 서로 다른 주문 20건이 동시에 1개씩 할당하면 정확히 10건만 성공하고 재고는 0이 된다")
    void concurrentAllocationNeverOversells() throws Exception {
        HubStock stock = stockWith(10);

        List<Object> results = runConcurrently(20, i -> () -> stockService.allocate(
                new AllocateStockCommand(UUID.randomUUID(), productId, 1, supplierHub.getId())));

        assertThat(results.stream().filter(StockAllocationResult.class::isInstance)).hasSize(10);
        assertThat(results.stream().filter(BusinessException.class::isInstance))
                .hasSize(10)
                .allSatisfy(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(HubErrorCode.STOCK_INSUFFICIENT));
        assertThat(quantityOf(stock)).isZero();
        assertThat(historyCount("DECREASE")).isEqualTo(10);
    }

    @Test
    @DisplayName("같은 주문이 동시에 여러 번 할당돼도 한 번만 차감하고 모두 같은 결과를 받는다")
    void concurrentSameOrderAllocatesOnce() throws Exception {
        HubStock stock = stockWith(10);
        UUID orderId = UUID.randomUUID();

        List<Object> results = runConcurrently(10, i -> () -> stockService.allocate(
                new AllocateStockCommand(orderId, productId, 4, supplierHub.getId())));

        assertThat(results).allSatisfy(result -> assertThat(result).isEqualTo(new StockAllocationResult(supplierHub.getId(), 4)));
        assertThat(quantityOf(stock)).isEqualTo(6);
        assertThat(historyCount("DECREASE")).isEqualTo(1);
    }

    @Test
    @DisplayName("동시에 입고해도 서로의 증가분을 덮어쓰지 않는다")
    void concurrentInboundKeepsEveryIncrease() throws Exception {
        HubStock stock = stockWith(0);

        List<Object> results = runConcurrently(20, i -> () -> stockService.inbound(
                new InboundStockCommand(supplierHub.getId(), productId, 5)));

        assertThat(results).noneMatch(Throwable.class::isInstance);
        assertThat(quantityOf(stock)).isEqualTo(100);
        assertThat(historyCount("INBOUND")).isEqualTo(20);
    }

    @Test
    @DisplayName("같은 주문의 복원이 동시에 여러 번 와도 한 번만 되돌린다")
    void concurrentRestoreRestoresOnce() throws Exception {
        HubStock stock = stockWith(10);
        UUID orderId = UUID.randomUUID();
        stockService.allocate(new AllocateStockCommand(orderId, productId, 4, supplierHub.getId()));

        List<Object> results = runConcurrently(10, i -> () -> stockService.restore(orderId).restoredQuantity());

        assertThat(results).noneMatch(Throwable.class::isInstance);
        assertThat(results.stream().mapToInt(Integer.class::cast).sum()).isEqualTo(4);
        assertThat(quantityOf(stock)).isEqualTo(10);
        assertThat(historyCount("RESTORE")).isEqualTo(1);
    }
}
