package com.sparta.logistics.company.domain;

import com.sparta.logistics.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 업체. 생산업체·수령업체 구분 없이 어느 쪽이든 될 수 있다(SA 5.4).
 */
@Getter
@Entity
@Table(name = "p_companies")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "company_id")
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    /** 공급업체면 집하지, 수령업체면 배송지로 쓴다. */
    @Column(nullable = false)
    private String address;

    /** 업체 관리 허브. hub-service p_hubs.hub_id 논리 참조(FK 없음) */
    @Column(name = "hub_id", nullable = false)
    private UUID hubId;

    /** 업체 관리자. user-service p_users.user_id 논리 참조(FK 없음) */
    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    private Company(String name, String address, UUID hubId, UUID managerId) {
        this.name = name;
        this.address = address;
        this.hubId = hubId;
        this.managerId = managerId;
    }

    /** 허브·관리자 존재 확인은 호출 전에 application에서 끝낸다(C-01). */
    public static Company create(String name, String address, UUID hubId, UUID managerId) {
        return new Company(name, address, hubId, managerId);
    }

    /** C-04 부분 수정. null인 값은 바꾸지 않는다. */
    public void update(String name, String address) {
        if (name != null) {
            this.name = name;
        }
        if (address != null) {
            this.address = address;
        }
    }

    /** 이미 만들어진 배송은 생성 시점의 허브를 그대로 쓰므로 상품이 있어도 바꿀 수 있다(SA 5.4). */
    public void changeHub(UUID hubId) {
        this.hubId = hubId;
    }

    /** 관리자 변경은 MASTER만 한다. 권한 확인은 application에서 한다. */
    public void assignManager(UUID managerId) {
        this.managerId = managerId;
    }
}
