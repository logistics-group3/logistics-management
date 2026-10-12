package com.sparta.logistics.common.web;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 검색 공통 Query Parameter 규칙 (SA 6.1)
 * <ul>
 *   <li>page: 0부터 시작</li>
 *   <li>size: 10, 30, 50 중 하나. 그 외 값은 10으로 보정</li>
 *   <li>sort: 없으면 createdAt,DESC. 허용 필드는 API별로 {@link QuerydslSorts}에서 거른다</li>
 * </ul>
 */
public final class PageRequests {

    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 30, 50);
    private static final int DEFAULT_SIZE = 10;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private PageRequests() {
    }

    public static Pageable normalize(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = ALLOWED_SIZES.contains(pageable.getPageSize()) ? pageable.getPageSize() : DEFAULT_SIZE;
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : DEFAULT_SORT;
        return PageRequest.of(page, size, sort);
    }
}
