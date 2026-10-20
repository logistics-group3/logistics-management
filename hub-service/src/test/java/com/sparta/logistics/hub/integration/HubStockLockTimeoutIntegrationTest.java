package com.sparta.logistics.hub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.hub.application.StockService;
import com.sparta.logistics.hub.application.dto.AllocateStockCommand;
import com.sparta.logistics.hub.domain.Hub;
import com.sparta.logistics.hub.domain.HubStock;
import com.sparta.logistics.hub.domain.HubStockRepository;
import com.sparta.logistics.hub.infrastructure.persistence.HubJpaRepository;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 다른 트랜잭션이 재고 행을 잠근 채 있으면 할당이 무한정 기다리지 않고 SERVICE_UNAVAILABLE로 끝나는지 실제 PostgreSQL로 확인한다.
 * Note: 운영 값은 config-repo/hub-service.yml의 connection-init-sql이다. 테스트는 같은 방식으로 더 짧은 대기 시간을 준다.
 */
@TestPropertySource(properties = "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '500ms'")
class HubStockLockTimeoutIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private StockService stockService;
    @Autowired
    private HubStockRepository stockRepository;
    @Autowired
    private HubJpaRepository hubRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM p_hub_stock_histories");
        jdbcTemplate.update("DELETE FROM p_hub_stocks");
    }

    @Test
    @DisplayName("다른 트랜잭션이 재고 행을 잠그고 있으면 대기 시간을 넘긴 할당은 SERVICE_UNAVAILABLE이고 재고는 그대로다")
    void allocateGivesUpWhenRowIsLocked() throws Exception {
        Hub hub = hubRepository.findAll().get(0);
        UUID productId = UUID.randomUUID();
        HubStock stock = HubStock.create(hub.getId(), productId);
        stock.increase(10);
        stockRepository.saveAndFlush(stock);

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    stockRepository.lockByHubIdAndProductId(hub.getId(), productId);
                    locked.countDown();
                    await(release);
                }));
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            long start = System.nanoTime();
            assertThatThrownBy(() -> stockService.allocate(new AllocateStockCommand(UUID.randomUUID(), productId, 1, hub.getId())))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(5_000);
        } finally {
            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT quantity FROM p_hub_stocks WHERE hub_stock_id = ?", Integer.class, stock.getId())).isEqualTo(10);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
