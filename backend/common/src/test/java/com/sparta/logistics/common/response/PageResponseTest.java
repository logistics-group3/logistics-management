package com.sparta.logistics.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PageResponseTest {

    @Test
    @DisplayName("Page를 SA 6.1 목록 응답 형식으로 변환한다")
    void fromPage() {
        // given
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageImpl<Integer> page = new PageImpl<>(List.of(1, 2, 3), pageable, 42);

        // when
        PageResponse<String> response = PageResponse.from(page, String::valueOf);

        // then
        assertThat(response.content()).containsExactly("1", "2", "3");
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(42);
        assertThat(response.totalPages()).isEqualTo(5);
        assertThat(response.sort()).isEqualTo("createdAt,DESC");
    }
}
