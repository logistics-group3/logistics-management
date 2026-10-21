package com.sparta.logistics.company.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.support.PostgresIntegrationTest;
import com.sparta.logistics.company.application.CompanyService;
import com.sparta.logistics.company.application.ProductService;
import com.sparta.logistics.company.application.dto.CreateProductCommand;
import com.sparta.logistics.company.application.dto.ProductResult;
import com.sparta.logistics.company.application.dto.UpdateCompanyCommand;
import com.sparta.logistics.company.application.dto.UpdateProductCommand;
import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanyErrorCode;
import com.sparta.logistics.company.domain.CompanyRepository;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 서비스 트랜잭션의 커밋 순서와 PostgreSQL 잠금 대기로 업체·상품 삭제 불변식을 확인한다. */
class CompanyProductConcurrencyIntegrationTest extends PostgresIntegrationTest {
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyService companyService;
    @Autowired private ProductService productService;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;

    private final UserContext master = new UserContext(UUID.randomUUID(), "concurrency-master", Role.MASTER, false);

    @Test
    @DisplayName("상품 생성이 먼저면 업체 삭제가 기다렸다가 새 상품까지 함께 논리 삭제한다")
    void creationBeforeDeletionDeletesNewProduct() throws Exception {
        Company company = company();
        CountDownLatch prepared = new CountDownLatch(1), release = new CountDownLatch(1), deletionStarted = new CountDownLatch(1);
        AtomicInteger creatorPid = new AtomicInteger(), deleterPid = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<ProductResult> creation = workers.submit(() -> tx().execute(status -> {
                creatorPid.set(backendPid());
                ProductResult product = createProduct(company.getId());
                prepared.countDown();
                awaitLatch(release);
                return product;
            }));
            awaitLatch(prepared);
            Future<?> deletion = workers.submit(() -> tx().executeWithoutResult(status -> {
                deleterPid.set(backendPid());
                deletionStarted.countDown();
                companyService.delete(master, company.getId());
            }));
            awaitLatch(deletionStarted);
            // Note: 기존 구현이면 삭제가 먼저 끝난다. 임의의 대기 시간 대신 실제 잠금 대기 또는 작업 완료를 관측한다.
            await().atMost(Duration.ofSeconds(5)).until(() -> deletion.isDone() || blockedBy(deleterPid.get(), creatorPid.get()));
            boolean waited = blockedBy(deleterPid.get(), creatorPid.get());
            release.countDown();
            ProductResult product = creation.get(5, TimeUnit.SECONDS);
            deletion.get(5, TimeUnit.SECONDS);

            assertDeletedTogether(company.getId());
            assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM p_products WHERE product_id = ?",
                    Boolean.class, product.productId())).isTrue();
            assertThat(waited).as("업체 삭제는 같은 업체의 상품 생성 커밋을 기다린다").isTrue();
        } finally {
            release.countDown();
            stop(workers);
        }
    }

    @Test
    @DisplayName("업체 삭제가 먼저면 상품 생성이 기다렸다가 COMPANY_NOT_FOUND로 거절된다")
    void deletionBeforeCreationRejectsProduct() throws Exception {
        Company company = company();
        CountDownLatch prepared = new CountDownLatch(1), release = new CountDownLatch(1), creationStarted = new CountDownLatch(1);
        AtomicInteger creatorPid = new AtomicInteger(), deleterPid = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> deletion = workers.submit(() -> tx().executeWithoutResult(status -> {
                deleterPid.set(backendPid());
                companyService.delete(master, company.getId());
                prepared.countDown();
                awaitLatch(release);
            }));
            awaitLatch(prepared);
            Future<ProductResult> creation = workers.submit(() -> tx().execute(status -> {
                creatorPid.set(backendPid());
                creationStarted.countDown();
                return createProduct(company.getId());
            }));
            awaitLatch(creationStarted);
            await().atMost(Duration.ofSeconds(5)).until(() -> creation.isDone() || blockedBy(creatorPid.get(), deleterPid.get()));
            boolean waited = blockedBy(creatorPid.get(), deleterPid.get());
            release.countDown();
            deletion.get(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> creation.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BusinessException.class)
                    .satisfies(error -> assertThat(((BusinessException) error.getCause()).getErrorCode())
                            .isEqualTo(CompanyErrorCode.COMPANY_NOT_FOUND));
            assertDeletedTogether(company.getId());
            assertThat(waited).as("상품 생성은 같은 업체의 삭제 커밋을 기다린다").isTrue();
        } finally {
            release.countDown();
            stop(workers);
        }
    }

    @Test
    @DisplayName("업체 삭제가 먼저면 상품 수정이 기다렸다가 PRODUCT_NOT_FOUND로 거절되고 삭제를 되돌리지 않는다")
    void deletionBeforeProductUpdateKeepsDeletion() throws Exception {
        Company company = company();
        UUID productId = createProduct(company.getId()).productId();
        CountDownLatch prepared = new CountDownLatch(1), release = new CountDownLatch(1), updateStarted = new CountDownLatch(1);
        AtomicInteger updaterPid = new AtomicInteger(), deleterPid = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> deletion = workers.submit(() -> tx().executeWithoutResult(status -> {
                deleterPid.set(backendPid());
                companyService.delete(master, company.getId());
                prepared.countDown();
                awaitLatch(release);
            }));
            awaitLatch(prepared);
            Future<?> update = workers.submit(() -> tx().executeWithoutResult(status -> {
                updaterPid.set(backendPid());
                updateStarted.countDown();
                productService.update(master, company.getId(), productId, new UpdateProductCommand("늦은 수정", null, null));
            }));
            awaitLatch(updateStarted);
            await().atMost(Duration.ofSeconds(5)).until(() -> update.isDone() || blockedBy(updaterPid.get(), deleterPid.get()));
            boolean waited = blockedBy(updaterPid.get(), deleterPid.get());
            release.countDown();
            deletion.get(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> update.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BusinessException.class)
                    .satisfies(error -> assertThat(((BusinessException) error.getCause()).getErrorCode())
                            .isEqualTo(CompanyErrorCode.PRODUCT_NOT_FOUND));
            assertDeletedTogether(company.getId());
            assertThat(waited).as("상품 수정은 같은 업체의 삭제 커밋을 기다린다").isTrue();
        } finally {
            release.countDown();
            stop(workers);
        }
    }

    @Test
    @DisplayName("관리자 변경이 먼저면 업체명 수정이 기다렸다가 새 관리자 기준으로 거절된다")
    void managerTransferBeforeCompanyUpdateKeepsNewManager() throws Exception {
        UUID oldManager = UUID.randomUUID(), newManager = UUID.randomUUID();
        Company company = companies.save(Company.create("동시성 테스트 업체", "테스트 주소", UUID.randomUUID(), oldManager));
        UserContext oldManagerUser = new UserContext(oldManager, "old-manager", Role.USER, false);
        CountDownLatch prepared = new CountDownLatch(1), release = new CountDownLatch(1), updateStarted = new CountDownLatch(1);
        AtomicInteger transferPid = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> transfer = workers.submit(() -> tx().executeWithoutResult(status -> {
                transferPid.set(backendPid());
                companies.findByIdForUpdate(company.getId()).orElseThrow().assignManager(newManager);
                prepared.countDown();
                awaitLatch(release);
            }));
            awaitLatch(prepared);
            Future<?> update = workers.submit(() -> {
                updateStarted.countDown();
                companyService.update(oldManagerUser, company.getId(), new UpdateCompanyCommand("늦은 수정", null, null, null));
            });
            awaitLatch(updateStarted);
            // Note: update는 서비스 안에서 트랜잭션을 직접 열어 테스트가 백엔드 PID를 알 수 없다. 대기 중인 잠금 요청 쪽을 PID로 찾는다.
            await().atMost(Duration.ofSeconds(5)).until(() -> update.isDone() || waiterOf(transferPid.get()));
            boolean waited = waiterOf(transferPid.get());
            release.countDown();
            transfer.get(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> update.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BusinessException.class)
                    .satisfies(error -> assertThat(((BusinessException) error.getCause()).getErrorCode())
                            .isEqualTo(CommonErrorCode.FORBIDDEN));
            assertThat(jdbc.queryForObject("SELECT manager_id FROM p_companies WHERE company_id = ?",
                    UUID.class, company.getId())).isEqualTo(newManager);
            assertThat(jdbc.queryForObject("SELECT name FROM p_companies WHERE company_id = ?",
                    String.class, company.getId())).isEqualTo("동시성 테스트 업체");
            assertThat(waited).as("업체 수정은 같은 업체의 관리자 변경 커밋을 기다린다").isTrue();
        } finally {
            release.countDown();
            stop(workers);
        }
    }

    @Test
    @DisplayName("한 업체의 상품 생성이 커밋을 기다려도 다른 업체의 상품은 생성된다")
    void differentCompaniesProceedIndependently() throws Exception {
        Company first = company(), second = company();
        CountDownLatch prepared = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<ProductResult> held = workers.submit(() -> tx().execute(status -> {
                ProductResult product = createProduct(first.getId());
                prepared.countDown();
                awaitLatch(release);
                return product;
            }));
            awaitLatch(prepared);
            ProductResult independent = workers.submit(() -> createProduct(second.getId())).get(5, TimeUnit.SECONDS);
            assertThat(independent.companyId()).isEqualTo(second.getId());
            assertThat(held.isDone()).isFalse();
            release.countDown();
            held.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            stop(workers);
        }
    }

    private Company company() {
        return companies.save(Company.create("동시성 테스트 업체", "테스트 주소", UUID.randomUUID(), master.userId()));
    }

    private ProductResult createProduct(UUID companyId) {
        return productService.create(master, companyId, new CreateProductCommand("동시성 테스트 상품", null, 1000L));
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactions);
    }

    private int backendPid() {
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private boolean blockedBy(int waiter, int holder) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, holder, waiter));
    }

    private boolean waiterOf(int holder) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)))", Boolean.class, holder));
    }

    private void assertDeletedTogether(UUID companyId) {
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM p_companies WHERE company_id = ?",
                Boolean.class, companyId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM p_products WHERE company_id = ? AND deleted_at IS NULL",
                Integer.class, companyId)).isZero();
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("테스트 실행 순서 대기 시간 초과");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static void stop(ExecutorService workers) throws InterruptedException {
        workers.shutdownNow();
        assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
}
