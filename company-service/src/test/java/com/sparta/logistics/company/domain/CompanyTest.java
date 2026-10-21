package com.sparta.logistics.company.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompanyTest {

    private static Company company() {
        return Company.create("스파르타 식품", "서울시 강남구 1", UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("수정 값이 null이면 기존 값을 유지한다")
    void updateKeepsNullFields() {
        Company company = company();

        company.update(null, "서울시 송파구 2");

        assertThat(company.getName()).isEqualTo("스파르타 식품");
        assertThat(company.getAddress()).isEqualTo("서울시 송파구 2");
    }

    @Test
    @DisplayName("관리 허브와 관리자를 바꿀 수 있다")
    void changeHubAndManager() {
        Company company = company();
        UUID hubId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        company.changeHub(hubId);
        company.assignManager(managerId);

        assertThat(company.getHubId()).isEqualTo(hubId);
        assertThat(company.getManagerId()).isEqualTo(managerId);
    }
}
