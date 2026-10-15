package com.sparta.logistics.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.logistics.user.UserFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    private final User user = UserFixture.user("hong01", "hashed");

    @Test
    @DisplayName("프로필 수정은 보낸 값만 바꾸고 null인 값은 그대로 둔다")
    void updateProfilePartially() {
        user.updateProfile(null, "U09ZYXWV8UT", null, null);

        assertThat(user.getSlackId()).isEqualTo("U09ZYXWV8UT");
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getAffiliationType()).isEqualTo(AffiliationType.COMPANY);
        assertThat(user.getAffiliationName()).isEqualTo("부산수산도매");
    }
}
