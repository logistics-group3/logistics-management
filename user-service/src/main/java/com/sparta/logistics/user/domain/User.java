package com.sparta.logistics.user.domain;

import com.sparta.logistics.common.context.UserRole;
import com.sparta.logistics.common.jpa.BaseEntity;
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

/**
 * 사용자. username 유니크 제약은 논리 삭제를 고려해 부분 인덱스(db/indexes.sql)로 건다.
 */
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

    @Column(nullable = false, length = 10)
    private String username;

    /** BCrypt 해시 */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "slack_id", length = 50)
    private String slackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "affiliation_type", length = 20)
    private AffiliationType affiliationType;

    @Column(name = "affiliation_name", length = 100)
    private String affiliationName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "is_delivery_manager", nullable = false)
    private boolean deliveryManager;

    /** 배송담당자 소속 허브 (논리 참조) */
    @Column(name = "hub_id")
    private UUID hubId;

    private User(String username, String encodedPassword, String name, String slackId,
                 AffiliationType affiliationType, String affiliationName, UserRole role) {
        this.username = username;
        this.password = encodedPassword;
        this.name = name;
        this.slackId = slackId;
        this.affiliationType = affiliationType;
        this.affiliationName = affiliationName;
        this.role = role;
        this.deliveryManager = false;
    }

    /** 회원가입: 권한은 항상 USER */
    public static User signUp(String username, String encodedPassword, String name, String slackId,
                              AffiliationType affiliationType, String affiliationName) {
        return new User(username, encodedPassword, name, slackId, affiliationType, affiliationName, UserRole.USER);
    }

    /** 초기 MASTER 계정 (기동 시 .env 값으로 생성) */
    public static User master(String username, String encodedPassword) {
        return new User(username, encodedPassword, "관리자", null, null, null, UserRole.MASTER);
    }
}
