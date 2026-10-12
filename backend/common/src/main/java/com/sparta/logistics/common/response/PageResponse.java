package com.sparta.logistics.common.response;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

/**
 * 목록(검색) 응답의 data 부분 (SA 6.1).
 * <pre>
 * { "content": [ ], "page": 0, "size": 10, "totalElements": 42, "totalPages": 5, "sort": "createdAt,DESC" }
 * </pre>
 * 컨트롤러에서: {@code return ApiResponse.ok(PageResponse.from(page, OrderResponse::from));}
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                toSortString(page.getSort())
        );
    }

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return from(page.map(mapper));
    }

    /** "createdAt,DESC" 형식. 정렬이 여러 개면 ";"로 구분 */
    private static String toSortString(Sort sort) {
        if (sort.isUnsorted()) {
            return null;
        }
        return sort.stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name())
                .collect(Collectors.joining(";"));
    }
}
