package com.sparta.logistics.hub.infrastructure.client;

import com.sparta.logistics.hub.application.CompanyReader;
import com.sparta.logistics.hub.infrastructure.client.dto.CompanyResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompanyClientAdapter implements CompanyReader {

    private final ProductClient productClient;

    @Override
    public CompanyInfo getCompany(UUID companyId) {
        CompanyResponse response = productClient.getCompany(companyId);
        return new CompanyInfo(response.hubId(), response.managerId());
    }
}
