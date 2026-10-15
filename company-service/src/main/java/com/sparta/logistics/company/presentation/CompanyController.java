package com.sparta.logistics.company.presentation;

import com.sparta.logistics.common.auth.CurrentUser;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.common.response.PageResponse;
import com.sparta.logistics.company.application.CompanyQueryService;
import com.sparta.logistics.company.application.CompanyService;
import com.sparta.logistics.company.presentation.dto.CompanyResponse;
import com.sparta.logistics.company.presentation.dto.CompanySearchRequest;
import com.sparta.logistics.company.presentation.dto.CreateCompanyRequest;
import com.sparta.logistics.company.presentation.dto.UpdateCompanyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모든 API가 로그인 사용자 대상이라 @RequireRole이 없다. 관리자 본인·MASTER 검사는 application이 한다.
 * 조회 API도 @CurrentUser를 받는다. 값을 쓰지 않아도 사용자 헤더가 없으면 401로 막는 로그인 확인 역할을 한다.
 */
@Tag(name = "업체", description = "업체 CRUD (C-01~C-05)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final CompanyQueryService companyQueryService;

    @Operation(summary = "C-01 업체 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CompanyResponse> create(@CurrentUser UserContext user,
                                               @Valid @RequestBody CreateCompanyRequest request) {
        return ApiResponse.created("업체가 생성되었습니다.",
                CompanyResponse.from(companyService.create(user, request.toCommand())));
    }

    @Operation(summary = "C-02 업체 검색")
    @GetMapping
    public ApiResponse<PageResponse<CompanyResponse>> search(@CurrentUser UserContext user,
                                                             @ModelAttribute CompanySearchRequest request,
                                                             Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                companyQueryService.searchCompanies(request.toCondition(), pageable), CompanyResponse::from));
    }

    @Operation(summary = "C-03 업체 단건 조회")
    @GetMapping("/{companyId}")
    public ApiResponse<CompanyResponse> get(@CurrentUser UserContext user, @PathVariable UUID companyId) {
        return ApiResponse.ok(CompanyResponse.from(companyQueryService.getCompany(companyId)));
    }

    @Operation(summary = "C-04 업체 수정")
    @PatchMapping("/{companyId}")
    public ApiResponse<CompanyResponse> update(@CurrentUser UserContext user, @PathVariable UUID companyId,
                                               @Valid @RequestBody UpdateCompanyRequest request) {
        return ApiResponse.ok(CompanyResponse.from(companyService.update(user, companyId, request.toCommand())));
    }

    @Operation(summary = "C-05 업체 삭제")
    @DeleteMapping("/{companyId}")
    public ApiResponse<Void> delete(@CurrentUser UserContext user, @PathVariable UUID companyId) {
        companyService.delete(user, companyId);
        return ApiResponse.ok("업체가 삭제되었습니다.", null);
    }
}
