package com.sparta.logistics.company.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.logistics.common.auth.UserHeaders;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.company.application.CompanyQueryService;
import com.sparta.logistics.company.application.CompanyService;
import com.sparta.logistics.company.application.ProductQueryService;
import com.sparta.logistics.company.application.ProductService;
import com.sparta.logistics.company.application.dto.CompanyResult;
import com.sparta.logistics.company.application.dto.InternalCompanyResult;
import com.sparta.logistics.company.domain.CompanyErrorCode;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 업체·상품·내부 컨트롤러의 요청 검증, 응답 형식, 로그인 확인을 슬라이스 테스트로 확인한다.
 * common 웹 설정(@CurrentUser, Pageable 규칙, SA 6.1 에러 형식)이 함께 올라온다.
 */
@WebMvcTest(controllers = {CompanyController.class, ProductController.class,
        InternalCompanyController.class, InternalProductController.class},
        properties = "spring.cloud.config.enabled=false")
class CompanyControllerWebMvcTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyService companyService;
    @MockitoBean
    private CompanyQueryService companyQueryService;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private ProductQueryService productQueryService;

    /** Gateway가 넣어 주는 사용자 헤더 */
    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
        return request.header(UserHeaders.USER_ID, USER_ID.toString())
                .header(UserHeaders.USERNAME, "user01")
                .header(UserHeaders.ROLE, "USER")
                .header(UserHeaders.DELIVERY_MANAGER, "false");
    }

    private static CompanyResult companyResult() {
        return new CompanyResult(UUID.randomUUID(), "스파르타 식품", UUID.randomUUID(), "서울시", USER_ID,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("업체를 만들면 201과 생성 메시지, CompanyResponse를 돌려준다")
    void createCompany() throws Exception {
        given(companyService.create(any(), any())).willReturn(companyResult());
        String body = """
                {"name":"스파르타 식품","hubId":"%s","address":"서울시"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(asUser(post("/api/v1/companies")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("업체가 생성되었습니다."))
                .andExpect(jsonPath("$.data.managerId").value(USER_ID.toString()));
    }

    @Test
    @DisplayName("사용자 헤더 없이 업체를 만들면 401 UNAUTHORIZED")
    void createCompanyWithoutUser() throws Exception {
        String body = """
                {"name":"스파르타 식품","hubId":"%s","address":"서울시"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/companies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("필수 값이 빠진 업체 생성 요청은 VALIDATION_ERROR와 필드별 사유를 돌려준다")
    void createCompanyInvalid() throws Exception {
        mockMvc.perform(asUser(post("/api/v1/companies")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    @DisplayName("업체 수정에서 이름을 빈 문자열로 보내면 VALIDATION_ERROR, 생략하면 통과한다")
    void updateCompanyBlankName() throws Exception {
        UUID companyId = UUID.randomUUID();
        mockMvc.perform(asUser(patch("/api/v1/companies/{id}", companyId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        given(companyService.update(any(), eq(companyId), any())).willReturn(companyResult());
        mockMvc.perform(asUser(patch("/api/v1/companies/{id}", companyId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"address\":\"서울시 송파구\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("업체 검색의 size가 10·30·50이 아니면 10으로 보정해 넘긴다")
    void searchCompaniesNormalizesSize() throws Exception {
        given(companyQueryService.searchCompanies(any(), any())).willReturn(Page.empty());

        mockMvc.perform(asUser(get("/api/v1/companies").param("size", "20").param("name", "식품")))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(companyQueryService).searchCompanies(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("업체를 삭제하면 200과 삭제 메시지, data null을 돌려준다")
    void deleteCompany() throws Exception {
        mockMvc.perform(asUser(delete("/api/v1/companies/{id}", UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("업체가 삭제되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("상품 가격이 음수면 VALIDATION_ERROR")
    void createProductNegativePrice() throws Exception {
        mockMvc.perform(asUser(post("/api/v1/companies/{id}/products", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"생수\",\"price\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("I-07은 ApiResponse로 감싸지 않은 DTO를 그대로 돌려준다")
    void internalCompanyUnwrapped() throws Exception {
        UUID companyId = UUID.randomUUID();
        given(companyQueryService.getInternalCompany(companyId)).willReturn(
                new InternalCompanyResult(companyId, "스파르타 식품", UUID.randomUUID(), "서울시", USER_ID));

        mockMvc.perform(get("/internal/companies/{id}", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("I-08에서 없는 상품은 404 PRODUCT_NOT_FOUND를 SA 에러 형식으로 돌려준다")
    void internalProductNotFound() throws Exception {
        UUID productId = UUID.randomUUID();
        given(productQueryService.getInternalProduct(productId))
                .willThrow(new BusinessException(CompanyErrorCode.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/internal/products/{id}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }
}
