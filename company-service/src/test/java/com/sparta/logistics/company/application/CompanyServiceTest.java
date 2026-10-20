package com.sparta.logistics.company.application;

import static com.sparta.logistics.company.CompanyFixture.company;
import static com.sparta.logistics.company.CompanyFixture.master;
import static com.sparta.logistics.company.CompanyFixture.product;
import static com.sparta.logistics.company.CompanyFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.common.exception.ErrorCode;
import com.sparta.logistics.common.exception.SimpleErrorCode;
import com.sparta.logistics.company.application.dto.CompanyResult;
import com.sparta.logistics.company.application.dto.CreateCompanyCommand;
import com.sparta.logistics.company.application.dto.UpdateCompanyCommand;
import com.sparta.logistics.company.domain.Company;
import com.sparta.logistics.company.domain.CompanyErrorCode;
import com.sparta.logistics.company.domain.CompanyRepository;
import com.sparta.logistics.company.domain.Product;
import com.sparta.logistics.company.domain.ProductRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private HubReader hubReader;
    @Mock
    private UserReader userReader;
    @Mock
    private TransactionTemplate transactionTemplate;
    private CompanyService companyService;

    private final UUID hubId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        companyService = new CompanyService(companyRepository, productRepository, hubReader, userReader, transactionTemplate);
    }

    /** TransactionTemplate 목이 콜백을 바로 실행하게 한다. update 성공 테스트에서만 쓴다. */
    @SuppressWarnings("unchecked")
    private void runCallbackDirectly() {
        given(transactionTemplate.execute(any())).willAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }

    @Test
    @DisplayName("USER가 관리자를 생략하면 본인을 관리자로 업체를 만들고 사용자 조회는 하지 않는다")
    void createByUserAsSelfManager() {
        UserContext user = user(UUID.randomUUID());
        given(companyRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

        CompanyResult result = companyService.create(user, new CreateCompanyCommand("스파르타 식품", hubId, "서울시", null));

        assertThat(result.managerId()).isEqualTo(user.userId());
        verify(hubReader).checkExists(hubId);
        verifyNoInteractions(userReader);
    }

    @Test
    @DisplayName("USER가 다른 사람을 관리자로 지정하면 FORBIDDEN이고 원격 호출 없이 끝난다")
    void createByUserForOtherManager() {
        UserContext user = user(UUID.randomUUID());

        assertErrorCode(() -> companyService.create(user,
                new CreateCompanyCommand("스파르타 식품", hubId, "서울시", UUID.randomUUID())), CommonErrorCode.FORBIDDEN);
        verifyNoInteractions(hubReader, userReader);
    }

    @Test
    @DisplayName("MASTER가 지정한 관리자는 I-01로 존재를 확인한 뒤 저장한다")
    void createByMasterChecksManager() {
        UUID managerId = UUID.randomUUID();
        given(companyRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

        CompanyResult result = companyService.create(master(), new CreateCompanyCommand("스파르타 식품", hubId, "서울시", managerId));

        assertThat(result.managerId()).isEqualTo(managerId);
        verify(userReader).checkExists(managerId);
    }

    @Test
    @DisplayName("MASTER가 관리자를 비우면 VALIDATION_ERROR")
    void createByMasterWithoutManager() {
        assertErrorCode(() -> companyService.create(master(),
                new CreateCompanyCommand("스파르타 식품", hubId, "서울시", null)), CommonErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("허브가 없으면 hub-service의 HUB_NOT_FOUND를 그대로 올리고 저장하지 않는다")
    void createWithUnknownHub() {
        SimpleErrorCode hubNotFound = new SimpleErrorCode(HttpStatus.NOT_FOUND, "HUB_NOT_FOUND", "허브를 찾을 수 없습니다.");
        willThrow(new BusinessException(hubNotFound)).given(hubReader).checkExists(hubId);

        assertErrorCode(() -> companyService.create(user(UUID.randomUUID()),
                new CreateCompanyCommand("스파르타 식품", hubId, "서울시", null)), hubNotFound);
        verify(companyRepository, never()).save(any());
    }

    @Test
    @DisplayName("업체 관리자는 주소와 허브를 바꿀 수 있고 바꾼 허브는 I-02로 확인한다")
    void updateByManager() {
        UUID managerId = UUID.randomUUID();
        Company company = company(managerId);
        given(companyRepository.findById(company.getId())).willReturn(Optional.of(company));
        runCallbackDirectly();

        CompanyResult result = companyService.update(user(managerId), company.getId(),
                new UpdateCompanyCommand(null, hubId, "서울시 송파구", null));

        assertThat(result.address()).isEqualTo("서울시 송파구");
        assertThat(result.hubId()).isEqualTo(hubId);
        assertThat(result.name()).isEqualTo("스파르타 식품");
        verify(hubReader).checkExists(hubId);
    }

    @Test
    @DisplayName("관리자가 아닌 USER가 수정하면 FORBIDDEN")
    void updateByOtherUser() {
        Company company = company(UUID.randomUUID());
        given(companyRepository.findById(company.getId())).willReturn(Optional.of(company));

        assertErrorCode(() -> companyService.update(user(UUID.randomUUID()), company.getId(),
                new UpdateCompanyCommand("새 이름", null, null, null)), CommonErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("업체 관리자라도 관리자 변경은 MASTER만 할 수 있다")
    void updateManagerByUser() {
        UUID managerId = UUID.randomUUID();
        Company company = company(managerId);
        given(companyRepository.findById(company.getId())).willReturn(Optional.of(company));

        assertErrorCode(() -> companyService.update(user(managerId), company.getId(),
                new UpdateCompanyCommand(null, null, null, UUID.randomUUID())), CommonErrorCode.FORBIDDEN);
        verifyNoInteractions(userReader);
    }

    @Test
    @DisplayName("MASTER가 관리자를 바꾸면 I-01로 확인한 뒤 바꾼다")
    void updateManagerByMaster() {
        Company company = company(UUID.randomUUID());
        UUID newManagerId = UUID.randomUUID();
        given(companyRepository.findById(company.getId())).willReturn(Optional.of(company));
        runCallbackDirectly();

        CompanyResult result = companyService.update(master(), company.getId(),
                new UpdateCompanyCommand(null, null, null, newManagerId));

        assertThat(result.managerId()).isEqualTo(newManagerId);
        verify(userReader).checkExists(newManagerId);
    }

    @Test
    @DisplayName("없는 업체를 수정하면 COMPANY_NOT_FOUND")
    void updateUnknownCompany() {
        UUID companyId = UUID.randomUUID();
        given(companyRepository.findById(companyId)).willReturn(Optional.empty());

        assertErrorCode(() -> companyService.update(master(), companyId,
                new UpdateCompanyCommand("새 이름", null, null, null)), CompanyErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("업체를 삭제하면 소속 상품도 함께 논리 삭제한다")
    void deleteWithProducts() {
        UUID managerId = UUID.randomUUID();
        Company company = company(managerId);
        Product first = product(company);
        Product second = product(company);
        given(companyRepository.findByIdForUpdate(company.getId())).willReturn(Optional.of(company));
        given(productRepository.findAllByCompanyId(company.getId())).willReturn(List.of(first, second));

        companyService.delete(user(managerId), company.getId());

        assertThat(company.isDeleted()).isTrue();
        assertThat(company.getDeletedBy()).isEqualTo("user01");
        assertThat(first.isDeleted()).isTrue();
        assertThat(second.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("관리자가 아닌 USER가 삭제하면 FORBIDDEN이고 아무것도 삭제하지 않는다")
    void deleteByOtherUser() {
        Company company = company(UUID.randomUUID());
        given(companyRepository.findByIdForUpdate(company.getId())).willReturn(Optional.of(company));

        assertErrorCode(() -> companyService.delete(user(UUID.randomUUID()), company.getId()), CommonErrorCode.FORBIDDEN);
        assertThat(company.isDeleted()).isFalse();
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("없는 업체를 삭제하면 COMPANY_NOT_FOUND이고 상품을 변경하지 않는다")
    void deleteUnknownCompany() {
        UUID companyId = UUID.randomUUID();
        given(companyRepository.findByIdForUpdate(companyId)).willReturn(Optional.empty());

        assertErrorCode(() -> companyService.delete(master(), companyId), CompanyErrorCode.COMPANY_NOT_FOUND);

        verifyNoInteractions(productRepository);
    }
}
