package com.sparta.logistics.user.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.domain.AffiliationType;
import com.sparta.logistics.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlackEmailTest {
    @Test
    @DisplayName("기존 회원은 이메일 없이 유지되고 알림 이메일의 공백을 정리하거나 해제할 수 있다")
    void optionalEmailCanBeSetAndCleared() {
        User user = User.signup("driver01", "encoded", "담당자", "U123", AffiliationType.HUB, "서울");
        assertThat(user.getSlackEmail()).isNull();
        user.changeSlackEmail(" driver+test@example.com ");
        assertThat(user.getSlackEmail()).isEqualTo("driver+test@example.com");
        user.changeSlackEmail("   ");
        assertThat(user.getSlackEmail()).isNull();
        assertThat(user.getSlackId()).isEqualTo("U123");
    }

    @Test
    @DisplayName("저장 컬럼보다 긴 알림 이메일은 거절한다")
    void rejectsTooLongEmail() {
        User user = User.signup("driver01", "encoded", "담당자", "U123", AffiliationType.HUB, "서울");
        assertThatThrownBy(() -> user.changeSlackEmail("a".repeat(255))).isInstanceOf(BusinessException.class);
    }
}
