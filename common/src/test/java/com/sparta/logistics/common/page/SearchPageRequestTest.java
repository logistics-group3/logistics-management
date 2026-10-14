package com.sparta.logistics.common.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.ErrorCode;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class SearchPageRequestTest {

    private static final Set<String> ALLOWED = Set.of("createdAt", "name");

    @Test
    void 기본값은_page0_size10_createdAt_DESC() {
        Pageable pageable = new SearchPageRequest().toPageable(ALLOWED);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void 허용되지_않은_size는_10으로_보정한다() {
        SearchPageRequest request = new SearchPageRequest();
        request.setSize(25);

        assertThat(request.toPageable(ALLOWED).getPageSize()).isEqualTo(10);
    }

    @Test
    void 허용된_size는_그대로_쓴다() {
        SearchPageRequest request = new SearchPageRequest();
        request.setSize(50);

        assertThat(request.toPageable(ALLOWED).getPageSize()).isEqualTo(50);
    }

    @Test
    void 허용되지_않은_정렬_필드는_INVALID_REQUEST() {
        SearchPageRequest request = new SearchPageRequest();
        request.setSort("password,ASC");

        assertThatThrownBy(() -> request.toPageable(ALLOWED))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }
}
