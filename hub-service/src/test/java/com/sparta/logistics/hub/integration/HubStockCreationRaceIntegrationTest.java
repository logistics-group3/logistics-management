package com.sparta.logistics.hub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.hub.application.CompanyReader;
import com.sparta.logistics.hub.application.ProductReader;
import com.sparta.logistics.hub.application.StockRegistrationService;
import com.sparta.logistics.hub.application.dto.InboundStockCommand;
import com.sparta.logistics.hub.application.dto.InitializeStockCommand;
import com.sparta.logistics.hub.domain.Hub;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 재고 행이 아직 없는 허브·상품에 여러 요청이 동시에 들어올 때 유니크 위반을 한 번 다시 시도해 모두 성공하는지 실제 PostgreSQL로 확인한다.
 * 단위 테스트는 유니크 위반 예외를 mock이 던져 주므로, 실제 DB의 SQLState가 재시도 판단에 맞는지는 여기서만 검증된다.
 * Note: company 호출(상품 존재 확인)은 이 테스트의 관심사가 아니라 mock으로 대체한다.
 */
class HubStockCreationRaceIntegrationTest extends PostgresIntegrationTest {

    private static final int THREADS = 10;

    @Autowired
    private StockRegistrationService registrationService;
    @Autowired
    private HubJpaRepository hubRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private ProductReader productReader;
    @MockitoBean
    private CompanyReader companyReader;

    private final UUID productId = UUID.randomUUID();
    private final UserContext master = new UserContext(UUID.randomUUID(), "master01", Role.MASTER, false);
    private Hub hub;

    @BeforeEach
    void setUp() {
        cleanUp();
        hub = hubRepository.findAll().get(0);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM p_hub_stock_histories");
        jdbcTemplate.update("DELETE FROM p_hub_stocks");
    }

    @Test
    @DisplayName("재고 행이 없는 상태에서 동시에 입고해도 모두 성공하고 행은 하나이며 증가분이 모두 반영된다")
    void concurrentInboundCreatesOneStock() throws Exception {
        List<Object> results = runConcurrently(() -> registrationService.inbound(master,
                new InboundStockCommand(hub.getId(), productId, 5)));

        assertThat(results).noneMatch(Throwable.class::isInstance);
        assertThat(stockRowCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT quantity FROM p_hub_stocks WHERE product_id = ?", Integer.class, productId))
                .isEqualTo(THREADS * 5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM p_hub_stock_histories WHERE change_type = 'INBOUND'", Integer.class)).isEqualTo(THREADS);
    }

    @Test
    @DisplayName("재고 초기화를 동시에 여러 번 호출해도 모두 같은 재고 행을 돌려받는다")
    void concurrentInitializeReturnsSameStock() throws Exception {
        List<Object> results = runConcurrently(() -> registrationService.initialize(
                new InitializeStockCommand(hub.getId(), productId)));

        assertThat(results).noneMatch(Throwable.class::isInstance);
        assertThat(results.stream().distinct()).hasSize(1);
        assertThat(stockRowCount()).isEqualTo(1);
    }

    private int stockRowCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM p_hub_stocks WHERE product_id = ?", Integer.class, productId);
    }

    /** 같은 작업을 모든 스레드가 같은 순간에 시작하게 하고 각 결과(값 또는 예외)를 모아 돌려준다. */
    private <T> List<Object> runConcurrently(Callable<T> work) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
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
}
