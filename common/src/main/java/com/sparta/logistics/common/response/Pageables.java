package com.sparta.logistics.common.response;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 검색 공통 Query Parameter 규칙 (SA 6.1)
 * - size 는 10, 30, 50 중 하나. 그 외 값은 10 으로 보정
 * - sort 기본값 createdAt,DESC. 허용 필드 외 정렬은 기본값으로 대체
 *
 * 사용: {@code Pageable p = Pageables.normalize(pageable, Set.of("createdAt", "username"));}
 */
public final class Pageables {

    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 30, 50);
    private static final int DEFAULT_SIZE = 10;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private Pageables() {
    }

    public static Pageable normalize(Pageable pageable, Set<String> allowedSortFields) {
        int size = ALLOWED_SIZES.contains(pageable.getPageSize()) ? pageable.getPageSize() : DEFAULT_SIZE;
        int page = Math.max(pageable.getPageNumber(), 0);

        Sort sort = pageable.getSort();
        boolean valid = sort.isSorted()
                && sort.stream().allMatch(order -> allowedSortFields.contains(order.getProperty()));

        return PageRequest.of(page, size, valid ? sort : DEFAULT_SORT);
    }
}
