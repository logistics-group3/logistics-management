package com.sparta.logistics.hub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.hub.HubFixture;
import com.sparta.logistics.hub.application.dto.InboundStockCommand;
import com.sparta.logistics.hub.application.dto.InitializeStockCommand;
import com.sparta.logistics.hub.application.dto.InternalStockResult;
import com.sparta.logistics.hub.application.dto.StockResult;
import com.sparta.logistics.hub.domain.Hub;
import com.sparta.logistics.hub.domain.HubErrorCode;
import com.sparta.logistics.hub.domain.HubRepository;
import com.sparta.logistics.hub.domain.HubStock;
import com.sparta.logistics.hub.domain.HubStockRepository;
import java.time.LocalDateTime;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class StockRegistrationServiceTest {

    @Mock
    private StockService stockService;
    @Mock
    private HubStockRepository stockRepository;
    @Mock
    private HubRepository hubRepository;
    @Mock
    private ProductReader productReader;
    @Mock
    private CompanyReader companyReader;
    @InjectMocks
    private StockRegistrationService registrationService;

    private final UUID hubId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final Hub hub = HubFixture.hub("허브", "주소");
    private final StockResult stockResult = new StockResult(UUID.randomUUID(), hubId, productId, 500, LocalDateTime.now());
    private final InboundStockCommand inbound = new InboundStockCommand(hubId, productId, 500);
    private final UserContext master = new UserContext(UUID.randomUUID(), "master01", Role.MASTER, false);
    private final UUID managerId = UUID.randomUUID();
    private final UserContext manager = new UserContext(managerId, "supplier01", Role.USER, false);
    private final UUID companyId = UUID.randomUUID();

    @Test
    @DisplayName("이미 재고 레코드가 있으면 company를 호출하지 않고 입고한다")
    void inboundExistingSkipsProductCheck() {
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));
        given(stockRepository.findByHubIdAndProductId(hubId, productId)).willReturn(Optional.of(HubStock.create(hubId, productId)));
        given(stockService.inbound(inbound)).willReturn(stockResult);

        assertThat(registrationService.inbound(master, inbound)).isEqualTo(stockResult);

        verify(productReader, never()).checkExists(any());
    }

    @Test
    @DisplayName("재고 레코드가 없으면 company로 상품 존재를 확인한 뒤 입고한다")
    void inboundNewChecksProduct() {
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));
        given(stockRepository.findByHubIdAndProductId(hubId, productId)).willReturn(Optional.empty());
        given(stockService.inbound(inbound)).willReturn(stockResult);

        registrationService.inbound(master, inbound);

        verify(productReader).checkExists(productId);
    }

    @Test
    @DisplayName("상품이 없으면 PRODUCT_NOT_FOUND가 그대로 올라오고 입고하지 않는다")
    void inboundProductNotFound() {
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));
        given(stockRepository.findByHubIdAndProductId(hubId, productId)).willReturn(Optional.empty());
        willThrow(new BusinessException(CommonErrorCode.NOT_FOUND)).given(productReader).checkExists(productId);

        assertThatThrownBy(() -> registrationService.inbound(master, inbound)).isInstanceOf(BusinessException.class);

        verify(stockService, never()).inbound(any());
    }

    @Test
    @DisplayName("공급업체 관리자는 자기 업체 소속 허브에 입고할 수 있다")
    void inboundBySupplierManager() {
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));
        given(productReader.getCompanyId(productId)).willReturn(companyId);
        given(companyReader.getCompany(companyId)).willReturn(new CompanyReader.CompanyInfo(hubId, managerId));
        given(stockService.inbound(inbound)).willReturn(stockResult);

        assertThat(registrationService.inbound(manager, inbound)).isEqualTo(stockResult);
    }

    @Test
    @DisplayName("업체 관리자가 아닌 USER는 403 FORBIDDEN이고 입고하지 않는다")
    void inboundByOtherUserForbidden() {
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));
        given(productReader.getCompanyId(productId)).willReturn(companyId);
        given(companyReader.getCompany(companyId)).willReturn(new CompanyReader.CompanyInfo(hubId, UUID.randomUUID()));

        assertThatThrownBy(() -> registrationService.inbound(manager, inbound))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        verify(stockService, never()).inbound(any());
    }

    @Test
    @DisplayName("공급업체 관리자라도 자기 업체 소속이 아닌 허브에는 403 FORBIDDEN이다")
    void inboundToOtherHubForbidden() {
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));
        given(productReader.getCompanyId(productId)).willReturn(companyId);
        given(companyReader.getCompany(companyId)).willReturn(new CompanyReader.CompanyInfo(UUID.randomUUID(), managerId));

        assertThatThrownBy(() -> registrationService.inbound(manager, inbound))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        verify(stockService, never()).inbound(any());
    }

    @Test
    @DisplayName("공급업체 관리자는 재고 레코드가 이미 있어도 company로 권한을 확인한다")
    void inboundByManagerChecksEvenWhenStockExists() {
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));
        given(productReader.getCompanyId(productId)).willReturn(companyId);
        given(companyReader.getCompany(companyId)).willReturn(new CompanyReader.CompanyInfo(hubId, managerId));
        given(stockService.inbound(inbound)).willReturn(stockResult);

        registrationService.inbound(manager, inbound);

        verify(productReader).getCompanyId(productId);
        verify(companyReader).getCompany(companyId);
    }

    @Test
    @DisplayName("권한 확인 중 company 장애는 그대로 올라오고 입고하지 않는다")
    void inboundByManagerCompanyUnavailable() {
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));
        given(productReader.getCompanyId(productId)).willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> registrationService.inbound(manager, inbound))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));

        verify(stockService, never()).inbound(any());
    }

    @Test
    @DisplayName("MASTER는 업체 관리자를 확인하지 않는다")
    void inboundByMasterSkipsManagerCheck() {
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));
        given(stockRepository.findByHubIdAndProductId(hubId, productId)).willReturn(Optional.of(HubStock.create(hubId, productId)));
        given(stockService.inbound(inbound)).willReturn(stockResult);

        registrationService.inbound(master, inbound);

        verify(companyReader, never()).getCompany(any());
    }

    @Test
    @DisplayName("허브가 없으면 HUB_NOT_FOUND이고 상품 확인도 하지 않는다")
    void inboundHubNotFound() {
        given(hubRepository.findById(hubId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.inbound(master, inbound))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HubErrorCode.HUB_NOT_FOUND));

        verify(productReader, never()).checkExists(any());
    }

    @Test
    @DisplayName("동시에 같은 재고를 만들다 유니크 위반이 나면 한 번 다시 시도해 성공한다")
    void inboundRetriesOnDuplicate() {
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));
        given(stockRepository.findByHubIdAndProductId(hubId, productId)).willReturn(Optional.empty());
        given(stockService.inbound(inbound))
                .willThrow(uniqueViolation())
                .willReturn(stockResult);

        assertThat(registrationService.inbound(master, inbound)).isEqualTo(stockResult);

        verify(stockService, times(2)).inbound(inbound);
    }

    @Test
    @DisplayName("재고 초기화도 허브가 있어야 하고 유니크 위반이면 한 번 다시 시도한다")
    void initializeRetriesOnDuplicate() {
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));
        given(stockService.createIfAbsent(hubId, productId))
                .willThrow(uniqueViolation())
                .willReturn(stockResult);

        assertThat(registrationService.initialize(new InitializeStockCommand(hubId, productId)))
                .isEqualTo(new InternalStockResult(stockResult.hubStockId(), stockResult.quantity()));

        verify(stockService, times(2)).createIfAbsent(hubId, productId);
    }

    @Test
    @DisplayName("유니크 위반이 아닌 무결성 위반은 다시 시도하지 않고 그대로 던진다")
    void inboundDoesNotRetryOnOtherIntegrityViolation() {
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));
        given(stockRepository.findByHubIdAndProductId(hubId, productId)).willReturn(Optional.empty());
        given(stockService.inbound(inbound))
                .willThrow(new DataIntegrityViolationException("ck", new SQLException("check", "23514")));

        assertThatThrownBy(() -> registrationService.inbound(master, inbound))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(stockService, times(1)).inbound(inbound);
    }

    @Test
    @DisplayName("재고 생성 중 락 대기 시간을 넘기면 503 SERVICE_UNAVAILABLE이다")
    void inboundLockTimeout() {
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));
        given(stockRepository.findByHubIdAndProductId(hubId, productId)).willReturn(Optional.empty());
        given(stockService.inbound(inbound)).willThrow(new CannotAcquireLockException("lock timeout"));

        assertThatThrownBy(() -> registrationService.inbound(master, inbound))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("재고 초기화는 허브가 없으면 HUB_NOT_FOUND다")
    void initializeHubNotFound() {
        given(hubRepository.findById(hubId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.initialize(new InitializeStockCommand(hubId, productId)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HubErrorCode.HUB_NOT_FOUND));
    }

    private static DataIntegrityViolationException uniqueViolation() {
        return new DataIntegrityViolationException("uk_p_hub_stocks_hub_product",
                new SQLException("duplicate key", "23505"));
    }
}
