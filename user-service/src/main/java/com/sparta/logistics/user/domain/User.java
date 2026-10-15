package com.sparta.logistics.user.domain;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.entity.BaseEntity;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "p_users")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID id;

    /** 탈퇴 후에도 재사용할 수 없어서(SA 5.2) 부분 인덱스가 아닌 일반 유니크 제약을 둔다. */
    @Column(nullable = false, unique = true, length = 10)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "slack_id", nullable = false, length = 50)
    private String slackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "affiliation_type", nullable = false, length = 20)
    private AffiliationType affiliationType;

    @Column(name = "affiliation_name", nullable = false, length = 100)
    private String affiliationName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "is_delivery_manager", nullable = false)
    private boolean deliveryManager;

    @Column(name = "hub_id")
    private UUID hubId;

    private User(String username, String encodedPassword, String name, String slackId,
                 AffiliationType affiliationType, String affiliationName) {
        this.username = username;
        this.password = encodedPassword;
        this.name = name;
        this.slackId = slackId;
        this.affiliationType = affiliationType;
        this.affiliationName = affiliationName;
        this.role = Role.USER;
        this.deliveryManager = false;
    }

    /** 가입 즉시 활성화되며 권한은 USER로 시작한다(SA U-01). */
    public static User signup(String username, String encodedPassword, String name, String slackId,
                              AffiliationType affiliationType, String affiliationName) {
        return new User(username, encodedPassword, name, slackId, affiliationType, affiliationName);
    }

    /**
     * U-06 프로필 수정. null인 값은 바꾸지 않는다.
     * 배송담당자의 소속은 담당 허브로 고정되므로 여기서 바꿀 수 없다(user 결정 0001). 소속은 U-07 허브 지정으로만 바뀐다.
     */
    public void updateProfile(String name, String slackId, AffiliationType affiliationType, String affiliationName) {
        if (deliveryManager && (affiliationType != null || affiliationName != null)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "배송담당자의 소속은 허브 지정으로만 바꿀 수 있습니다.");
        }
        if (name != null) {
            this.name = name;
        }
        if (slackId != null) {
            this.slackId = slackId;
        }
        if (affiliationType != null) {
            this.affiliationType = affiliationType;
        }
        if (affiliationName != null) {
            this.affiliationName = affiliationName;
        }
    }

    /** 기동 시 환경 변수로 만드는 초기 관리자. 가입 API로는 MASTER를 만들 수 없으므로 이 경로만 MASTER로 시작한다. */
    public static User master(String username, String encodedPassword, String slackId) {
        User user = new User(username, encodedPassword, "마스터", slackId, AffiliationType.HUB, "스파르타 물류 본사");
        user.role = Role.MASTER;
        return user;
    }
}
