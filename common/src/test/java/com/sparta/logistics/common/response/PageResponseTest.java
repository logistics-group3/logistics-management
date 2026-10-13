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
    @DisplayName("Page를 SA 6.1 목록 응답 형식으로 바꾸고 정렬은 '필드,방향' 문자열로 표현한다")
    void fromPage() {
        PageRequest pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), pageable, 42);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(42);
        assertThat(response.totalPages()).isEqualTo(5);
        assertThat(response.sort()).isEqualTo("createdAt,DESC");
    }
}
