package com.sparta.logistics.user.domain;

import com.sparta.logistics.common.auth.Role;
import com.sparta.logistics.common.entity.BaseEntity;
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
}
