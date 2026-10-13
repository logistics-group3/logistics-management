package com.sparta.logistics.user.domain;

import com.sparta.logistics.common.auth.UserRole;
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

/** p_users (SA 5.2) */
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

    /** 탈퇴 후에도 재사용 불가 → 부분 인덱스가 아닌 일반 UNIQUE */
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
    private UserRole role;

    @Column(name = "is_delivery_manager", nullable = false)
    private boolean deliveryManager;

    /** 배송담당자 소속 허브 (논리 참조: hub_schema.p_hubs). 배송담당자가 아니면 NULL */
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

    /** 회원가입 — 항상 USER 로 생성, 가입 즉시 활성화 (U-01) */
    public static User signup(String username, String encodedPassword, String name, String slackId,
                              AffiliationType affiliationType, String affiliationName) {
        return new User(username, encodedPassword, name, slackId, affiliationType, affiliationName, UserRole.USER);
    }

    /** 초기 MASTER 계정 시드용 */
    public static User master(String username, String encodedPassword, String name, String slackId) {
        return new User(username, encodedPassword, name, slackId, AffiliationType.HUB, "스파르타 물류 본사", UserRole.MASTER);
    }
}
