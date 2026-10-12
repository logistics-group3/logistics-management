package com.sparta.logistics.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestsTest {

    @Test
    @DisplayName("size가 10·30·50이 아니면 10으로 보정한다")
    void normalizeSize() {
        // given
        Pageable pageable = PageRequest.of(0, 20);

        // when
        Pageable normalized = PageRequests.normalize(pageable);

        // then
        assertThat(normalized.getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("허용된 size는 그대로 쓴다")
    void keepAllowedSize() {
        assertThat(PageRequests.normalize(PageRequest.of(2, 30)).getPageSize()).isEqualTo(30);
        assertThat(PageRequests.normalize(PageRequest.of(2, 50)).getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("정렬이 없으면 createdAt DESC")
    void defaultSort() {
        // when
        Pageable normalized = PageRequests.normalize(PageRequest.of(0, 10));

        // then
        Sort.Order order = normalized.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
