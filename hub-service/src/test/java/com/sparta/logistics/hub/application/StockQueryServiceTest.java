package com.sparta.logistics.hub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.HubFixture;
import com.sparta.logistics.hub.application.dto.StockResult;
import com.sparta.logistics.hub.domain.HubErrorCode;
import com.sparta.logistics.hub.domain.HubRepository;
import com.sparta.logistics.hub.domain.HubStock;
import com.sparta.logistics.hub.domain.HubStockRepository;
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
class StockQueryServiceTest {

    @Mock
    private HubStockRepository stockRepository;
    @Mock
    private HubRepository hubRepository;
    @InjectMocks
    private StockQueryService stockQueryService;

    private final UUID hubId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final Pageable pageable = PageRequest.of(0, 10);

    @Test
    @DisplayName("허브의 재고를 검색해 결과로 바꿔 돌려준다")
    void search() {
        HubStock stock = HubStock.create(hubId, productId);
        stock.increase(450);
        given(hubRepository.findById(hubId)).willReturn(Optional.of(HubFixture.hub("허브", "주소")));
        given(stockRepository.search(hubId, productId, pageable)).willReturn(new PageImpl<>(List.of(stock), pageable, 1));

        Page<StockResult> page = stockQueryService.search(hubId, productId, pageable);

        assertThat(page.getContent()).extracting(StockResult::quantity).containsExactly(450);
        assertThat(page.getContent().get(0).productId()).isEqualTo(productId);
    }

    @Test
    @DisplayName("허브가 없으면 HUB_NOT_FOUND이고 재고를 조회하지 않는다")
    void searchHubNotFound() {
        given(hubRepository.findById(hubId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> stockQueryService.search(hubId, null, pageable))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HubErrorCode.HUB_NOT_FOUND));

        verify(stockRepository, never()).search(any(), any(), any());
    }
}
