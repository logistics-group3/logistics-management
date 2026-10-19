package com.sparta.logistics.company.presentation;

import com.sparta.logistics.company.application.CompanyQueryService;
import com.sparta.logistics.company.presentation.dto.InternalCompanyResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 호출 전용(I-07). Gateway가 /internal/**를 막아 외부에서 호출할 수 없다.
 * 내부 API라 ApiResponse로 감싸지 않는다(ADR 0010).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/companies")
public class InternalCompanyController {

    private final CompanyQueryService companyQueryService;

    @GetMapping("/{companyId}")
    public InternalCompanyResponse getCompany(@PathVariable UUID companyId) {
        return InternalCompanyResponse.from(companyQueryService.getInternalCompany(companyId));
    }
}
