package com.sparta.logistics.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.user.UserFixture;
import java.util.UUID;
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

    @Test
    @DisplayName("배송담당자의 소속은 프로필 수정으로 바꿀 수 없다")
    void deliveryManagerAffiliationIsFixed() {
        user.assignDeliveryManager(UUID.randomUUID(), "서울특별시 센터");

        assertThatThrownBy(() -> user.updateProfile(null, null, AffiliationType.COMPANY, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> user.updateProfile(null, null, null, "부산수산도매"))
                .isInstanceOf(BusinessException.class);
        assertThat(user.getAffiliationName()).isEqualTo("서울특별시 센터");
    }

    @Test
    @DisplayName("배송담당자의 이름과 Slack ID는 프로필 수정으로 바꿀 수 있다")
    void deliveryManagerCanUpdateNameAndSlack() {
        user.assignDeliveryManager(UUID.randomUUID(), "서울특별시 센터");

        user.updateProfile("김철수", "U02", null, null);

        assertThat(user.getName()).isEqualTo("김철수");
        assertThat(user.getSlackId()).isEqualTo("U02");
    }

    @Test
    @DisplayName("배송담당자로 지정하면 소속 허브와 함께 소속을 그 허브로 바꾼다")
    void assignDeliveryManager() {
        UUID hubId = UUID.randomUUID();

        user.assignDeliveryManager(hubId, "서울특별시 센터");

        assertThat(user.isDeliveryManager()).isTrue();
        assertThat(user.getHubId()).isEqualTo(hubId);
        assertThat(user.getAffiliationType()).isEqualTo(AffiliationType.HUB);
        assertThat(user.getAffiliationName()).isEqualTo("서울특별시 센터");
    }

    @Test
    @DisplayName("배송담당자를 해제하면 소속 허브만 지우고 소속 정보는 남긴다")
    void releaseDeliveryManager() {
        user.assignDeliveryManager(UUID.randomUUID(), "서울특별시 센터");

        user.releaseDeliveryManager();

        assertThat(user.isDeliveryManager()).isFalse();
        assertThat(user.getHubId()).isNull();
        assertThat(user.getAffiliationType()).isEqualTo(AffiliationType.HUB);
        assertThat(user.getAffiliationName()).isEqualTo("서울특별시 센터");
    }

    @Test
    @DisplayName("권한을 바꾼다")
    void changeRole() {
        user.changeRole(Role.MASTER);

        assertThat(user.getRole()).isEqualTo(Role.MASTER);
    }
}
