package com.sparta.logistics.user.domain.model;

import com.sparta.logistics.common.domain.BaseEntity;
import com.sparta.logistics.common.security.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 사용자 (SA 5.2 p_users). 사용자 관리 API(U-03~U-08)는 user-service 담당자가 이어서 구현한다.
 */
@Entity
@Table(name = "p_users")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @UuidGenerator
    private UUID userId;

    /** 로그인 ID. 탈퇴 후에도 재사용 불가 (DB UNIQUE는 삭제 여부와 무관) */
    @Column(nullable = false, length = 10, unique = true)
    private String username;

    /** BCrypt 해시 */
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 50)
    private String slackId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AffiliationType affiliationType;

    @Column(nullable = false, length = 100)
    private String affiliationName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    /** 배송담당자 여부 (MASTER가 부여) */
    @Column(name = "is_delivery_manager", nullable = false)
    private boolean deliveryManager;

    /** 회원가입: 권한 USER, 배송담당자 아님, 가입 즉시 활성화 */
    public static User signup(String username, String encodedPassword, String name, String slackId,
                              AffiliationType affiliationType, String affiliationName) {
        return create(username, encodedPassword, name, slackId, affiliationType, affiliationName, UserRole.USER);
    }

    /** 최초 관리자 등 권한을 지정해 만들 때 */
    public static User create(String username, String encodedPassword, String name, String slackId,
                              AffiliationType affiliationType, String affiliationName, UserRole role) {
        User user = new User();
        user.username = username;
        user.password = encodedPassword;
        user.name = name;
        user.slackId = slackId;
        user.affiliationType = affiliationType;
        user.affiliationName = affiliationName;
        user.role = role;
        user.deliveryManager = false;
        return user;
    }
}
