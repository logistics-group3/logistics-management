package com.sparta.logistics.hub.application;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.hub.application.dto.InboundStockCommand;
import com.sparta.logistics.hub.application.dto.InitializeStockCommand;
import com.sparta.logistics.hub.application.dto.InternalStockResult;
import com.sparta.logistics.hub.application.dto.StockResult;
import com.sparta.logistics.hub.domain.HubErrorCode;
import com.sparta.logistics.hub.domain.HubRepository;
import com.sparta.logistics.hub.domain.HubStockRepository;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * 재고 레코드를 만들 수 있는 진입점(I-04, H-06). DB 트랜잭션 밖에서 허브·상품 존재를 확인하고,
 * 같은 허브·상품을 동시에 만들다 유니크 제약에 걸린 요청을 한 번 다시 시도한다.
 * Note: 트랜잭션 메서드를 같은 클래스에서 부르면 프록시를 거치지 않아 트랜잭션이 적용되지 않으므로 StockService를 따로 두었다.
 */
@Service
@RequiredArgsConstructor
public class StockRegistrationService {

    private static final String UNIQUE_VIOLATION = "23505";

    private final StockService stockService;
    private final HubStockRepository stockRepository;
    private final HubRepository hubRepository;
    private final ProductReader productReader;
    private final CompanyReader companyReader;

    /** I-04 재고 초기화(company가 상품 생성 시 호출). 허브가 없으면 404 HUB_NOT_FOUND다. */
    public InternalStockResult initialize(InitializeStockCommand command) {
        requireHub(command.hubId());
        return InternalStockResult.from(
                retryOnceOnDuplicate(() -> stockService.createIfAbsent(command.hubId(), command.productId())));
    }

    /**
     * H-06 입고. MASTER 또는 상품이 속한 업체의 관리자(공급업체 관리자)만 할 수 있다. 업체 관리자는 자기 업체의 소속 허브에만 입고한다.
     * 재고 레코드가 없으면 company I-08로 상품 존재를 확인한 뒤 만든다(SA 6.5).
     * Note: company 호출은 응답을 기다리는 동안 DB 연결을 잡고 있지 않도록 트랜잭션 밖에서 한다(service.md).
     */
    public StockResult inbound(UserContext user, InboundStockCommand command) {
        requireHub(command.hubId());
        if (user.isMaster()) {
            if (stockRepository.findByHubIdAndProductId(command.hubId(), command.productId()).isEmpty()) {
                productReader.checkExists(command.productId());
            }
        } else {
            validateSupplierManager(user, command);
        }
        return retryOnceOnDuplicate(() -> stockService.inbound(command));
    }

    private void validateSupplierManager(UserContext user, InboundStockCommand command) {
        UUID companyId = productReader.getCompanyId(command.productId());
        CompanyReader.CompanyInfo company = companyReader.getCompany(companyId);
        if (!user.isSelf(company.managerId()) || !company.hubId().equals(command.hubId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private void requireHub(UUID hubId) {
        hubRepository.findById(hubId).orElseThrow(() -> new BusinessException(HubErrorCode.HUB_NOT_FOUND));
    }

    private <T> T retryOnceOnDuplicate(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException e) {
            if (!isUniqueViolation(e)) {
                throw e;
            }
            return action.get();
        } catch (PessimisticLockingFailureException e) {
            // Note: 다른 트랜잭션의 같은 행 생성을 lock_timeout보다 오래 기다린 경우다. 재고 행 락 초과와 같이 503으로 돌려보낸다.
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /** Note: CHECK·FK 위반은 다시 해도 같은 결과이므로 재시도하지 않는다. 유니크 위반만 SQLState 23505로 가려낸다. */
    private boolean isUniqueViolation(DataIntegrityViolationException e) {
        return NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql
                && UNIQUE_VIOLATION.equals(sql.getSQLState());
    }
}
