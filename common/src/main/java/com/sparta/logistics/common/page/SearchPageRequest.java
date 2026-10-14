package com.sparta.logistics.common.page;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.ErrorCode;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 검색 공통 파라미터 (API 설계서). 검색 DTO가 상속하거나 필드로 가져 {@code @ModelAttribute}로 바인딩한다.
 * page는 0부터, size는 10/30/50 외에는 10으로 보정, sort는 "필드,ASC|DESC"이고 허용 필드만 가능하다.
 */
public class SearchPageRequest {

    public static final int DEFAULT_SIZE = 10;
    public static final Set<Integer> ALLOWED_SIZES = Set.of(10, 30, 50);
    public static final String DEFAULT_SORT_FIELD = "createdAt";

    private int page = 0;
    private int size = DEFAULT_SIZE;
    private String sort = DEFAULT_SORT_FIELD + ",DESC";

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    /**
     * @param allowedSortFields API별 허용 정렬 필드. 그 외는 400 INVALID_REQUEST (정렬 인젝션 방지, ADR-15)
     */
    public Pageable toPageable(Set<String> allowedSortFields) {
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_SIZES.contains(size) ? size : DEFAULT_SIZE;
        return PageRequest.of(safePage, safeSize, parseSort(allowedSortFields));
    }

    private Sort parseSort(Set<String> allowedSortFields) {
        String value = (sort == null || sort.isBlank()) ? DEFAULT_SORT_FIELD + ",DESC" : sort;
        String[] parts = value.split(",");
        String field = parts[0].trim();
        if (!allowedSortFields.contains(field) || parts.length > 2) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "허용되지 않은 정렬 필드입니다: " + field);
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            String dir = parts[1].trim();
            if (dir.equalsIgnoreCase("DESC")) {
                direction = Sort.Direction.DESC;
            } else if (!dir.equalsIgnoreCase("ASC")) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "정렬 방향은 ASC 또는 DESC여야 합니다.");
            }
        }
        return Sort.by(direction, field);
    }
}
