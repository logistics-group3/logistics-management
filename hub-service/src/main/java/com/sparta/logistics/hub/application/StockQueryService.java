package com.sparta.logistics.hub.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.application.dto.StockResult;
import com.sparta.logistics.hub.domain.HubErrorCode;
import com.sparta.logistics.hub.domain.HubRepository;
import com.sparta.logistics.hub.domain.HubStockRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQueryService {

    private final HubStockRepository stockRepository;
    private final HubRepository hubRepository;

    /** H-05 허브 재고 검색. 허브가 없으면 404 HUB_NOT_FOUND이고, productId는 선택 조건이다. */
    public Page<StockResult> search(UUID hubId, UUID productId, Pageable pageable) {
        hubRepository.findById(hubId).orElseThrow(() -> new BusinessException(HubErrorCode.HUB_NOT_FOUND));
        return stockRepository.search(hubId, productId, pageable).map(StockResult::from);
    }
}
