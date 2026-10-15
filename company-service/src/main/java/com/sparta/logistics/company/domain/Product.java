package com.sparta.logistics.company.domain;

import com.sparta.logistics.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 상품. 업체의 하위 리소스이고 허브는 소속 업체의 hubId를 따른다(SA 5.4).
 */
@Getter
@Entity
@Table(name = "p_products")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "product_id")
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 원 단위 정수(company 결정 0003). 0 이상은 요청 검증과 DB CHECK가 막는다. */
    @Column(nullable = false)
    private Long price;

    /** 같은 서비스 안의 참조라 연관관계로 둔다. 상품의 허브와 소유자 검증이 모두 업체를 거친다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    private Product(Company company, String name, String description, Long price) {
        this.company = company;
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public static Product create(Company company, String name, String description, Long price) {
        return new Product(company, name, description, price);
    }

    /** P-04 부분 수정. null인 값은 바꾸지 않는다. 기존 주문은 가격 스냅샷을 갖고 있어 영향이 없다. */
    public void update(String name, String description, Long price) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (price != null) {
            this.price = price;
        }
    }

    public boolean belongsTo(UUID companyId) {
        return company.getId().equals(companyId);
    }
}
