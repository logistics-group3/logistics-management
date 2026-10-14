package com.sparta.logistics.common.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

/** 목록 응답의 data: content, page, size, totalElements, totalPages, sort */
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
                toSortString(page.getSort()));
    }

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }

    /** "createdAt,DESC" 형식. 여러 개면 ';'로 잇는다. */
    private static String toSortString(Sort sort) {
        if (sort.isUnsorted()) {
            return null;
        }
        return sort.stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name())
                .reduce((a, b) -> a + ";" + b)
                .orElse(null);
    }
}
