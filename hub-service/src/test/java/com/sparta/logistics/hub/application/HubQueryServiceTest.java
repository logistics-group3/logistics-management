package com.sparta.logistics.hub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.HubFixture;
import com.sparta.logistics.hub.application.dto.HubResult;
import com.sparta.logistics.hub.application.dto.InternalHubResult;
import com.sparta.logistics.hub.domain.Hub;
import com.sparta.logistics.hub.domain.HubErrorCode;
import com.sparta.logistics.hub.domain.HubRepository;
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
class HubQueryServiceTest {

    @Mock
    private HubRepository hubRepository;
    @InjectMocks
    private HubQueryService hubQueryService;

    private final UUID hubId = UUID.randomUUID();

    @Test
    @DisplayName("허브가 있으면 단건 조회 결과에 좌표까지 담는다")
    void getHub() {
        Hub hub = HubFixture.hub("서울특별시 센터", "서울특별시 송파구 송파대로 55");
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));

        HubResult result = hubQueryService.getHub(hubId);

        assertThat(result.name()).isEqualTo("서울특별시 센터");
        assertThat(result.latitude()).isEqualByComparingTo("37.5665");
        assertThat(result.longitude()).isEqualByComparingTo("126.978");
    }

    @Test
    @DisplayName("허브가 없으면 HUB_NOT_FOUND")
    void getHubNotFound() {
        given(hubRepository.findById(hubId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> hubQueryService.getHub(hubId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HubErrorCode.HUB_NOT_FOUND));
    }

    @Test
    @DisplayName("내부 조회는 허브 ID·이름·주소만 반환한다")
    void getInternalHub() {
        Hub hub = HubFixture.hub("부산광역시 센터", "부산 동구 중앙대로 206");
        given(hubRepository.findById(hubId)).willReturn(Optional.of(hub));

        InternalHubResult result = hubQueryService.getInternalHub(hubId);

        assertThat(result.name()).isEqualTo("부산광역시 센터");
        assertThat(result.address()).isEqualTo("부산 동구 중앙대로 206");
    }

    @Test
    @DisplayName("내부 조회도 허브가 없으면 HUB_NOT_FOUND")
    void getInternalHubNotFound() {
        given(hubRepository.findById(hubId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> hubQueryService.getInternalHub(hubId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HubErrorCode.HUB_NOT_FOUND));
    }

    @Test
    @DisplayName("검색 결과를 응답 DTO 페이지로 바꾸고 페이지 정보를 유지한다")
    void search() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Hub> page = new PageImpl<>(List.of(HubFixture.hub("서울특별시 센터", "서울")), pageable, 1);
        given(hubRepository.search("서울", pageable)).willReturn(page);

        Page<HubResult> result = hubQueryService.search("서울", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(HubResult::name).containsExactly("서울특별시 센터");
    }
}
