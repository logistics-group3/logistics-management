package com.sparta.logistics.hub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.HubFixture;
import com.sparta.logistics.hub.application.dto.HubRouteResult;
import com.sparta.logistics.hub.domain.Hub;
import com.sparta.logistics.hub.domain.HubErrorCode;
import com.sparta.logistics.hub.domain.HubRoute;
import com.sparta.logistics.hub.domain.HubRouteRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class HubRouteQueryServiceTest {

    @Mock
    private HubRouteRepository hubRouteRepository;
    @InjectMocks
    private HubRouteQueryService hubRouteQueryService;

    private final Hub seoul = HubFixture.hub("서울특별시 센터", "서울특별시 송파구 송파대로 55");
    private final Hub busan = HubFixture.hub("부산광역시 센터", "부산 동구 중앙대로 206");
    private final HubRoute route = HubRoute.create(seoul, busan, new BigDecimal("422.64"), 363);

    @Test
    @DisplayName("경로가 있으면 단건 조회 결과에 두 허브 이름과 거리·시간을 담는다")
    void getHubRoute() {
        UUID hubRouteId = UUID.randomUUID();
        given(hubRouteRepository.findById(hubRouteId)).willReturn(Optional.of(route));

        HubRouteResult result = hubRouteQueryService.getHubRoute(hubRouteId);

        assertThat(result.departureHub().name()).isEqualTo("서울특별시 센터");
        assertThat(result.arrivalHub().name()).isEqualTo("부산광역시 센터");
        assertThat(result.distanceKm()).isEqualByComparingTo("422.64");
        assertThat(result.durationMinutes()).isEqualTo(363);
    }

    @Test
    @DisplayName("경로가 없으면 HUB_ROUTE_NOT_FOUND")
    void getHubRouteNotFound() {
        UUID hubRouteId = UUID.randomUUID();
        given(hubRouteRepository.findById(hubRouteId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> hubRouteQueryService.getHubRoute(hubRouteId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HubErrorCode.HUB_ROUTE_NOT_FOUND));
    }

    @Test
    @DisplayName("검색 결과를 응답 DTO 페이지로 바꾸고 필터와 페이지 정보를 그대로 넘긴다")
    void search() {
        UUID departureHubId = UUID.randomUUID();
        UUID arrivalHubId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<HubRoute> page = new PageImpl<>(List.of(route), pageable, 1);
        given(hubRouteRepository.search(departureHubId, arrivalHubId, pageable)).willReturn(page);

        Page<HubRouteResult> result = hubRouteQueryService.search(departureHubId, arrivalHubId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(r -> r.arrivalHub().name()).containsExactly("부산광역시 센터");
    }

    @Test
    @DisplayName("필터가 없어도 저장소에 null 조건으로 위임한다")
    void searchWithoutFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        given(hubRouteRepository.search(null, null, pageable)).willReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<HubRouteResult> result = hubRouteQueryService.search(null, null, pageable);

        assertThat(result.getContent()).isEmpty();
    }
}
