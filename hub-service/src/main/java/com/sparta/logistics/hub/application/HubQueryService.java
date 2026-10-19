package com.sparta.logistics.hub.application;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.application.dto.HubResult;
import com.sparta.logistics.hub.application.dto.InternalHubResult;
import com.sparta.logistics.hub.domain.Hub;
import com.sparta.logistics.hub.domain.HubErrorCode;
import com.sparta.logistics.hub.domain.HubRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HubQueryService {

    private final HubRepository hubRepository;

    public Page<HubResult> search(String keyword, Pageable pageable) {
        return hubRepository.search(keyword, pageable).map(HubResult::from);
    }

    /**
     * 논리 삭제된 허브는 @SQLRestriction으로 조회되지 않아 404가 된다.
     * Note: 단건 조회만 캐시한다(성능 측정 항목 B). 예외(404)는 캐시하지 않는다. 목록 검색은 조건·페이지 조합이 많아 캐시하지 않는다.
     */
    @Cacheable(HubCacheNames.HUB)
    public HubResult getHub(UUID hubId) {
        return HubResult.from(findHub(hubId));
    }

    @Cacheable(HubCacheNames.INTERNAL_HUB)
    public InternalHubResult getInternalHub(UUID hubId) {
        return InternalHubResult.from(findHub(hubId));
    }

    private Hub findHub(UUID hubId) {
        return hubRepository.findById(hubId)
                .orElseThrow(() -> new BusinessException(HubErrorCode.HUB_NOT_FOUND));
    }
}
