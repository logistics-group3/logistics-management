package com.sparta.logistics.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 공통 Audit 컬럼 6개 + 논리 삭제 (SA 5.1).
 * <ul>
 *   <li>created_by / updated_by: Gateway가 넣은 {@code X-Username} 헤더. 헤더가 없으면 {@code SYSTEM}</li>
 *   <li>논리 삭제: {@link #softDelete(String)}로 deleted_at·deleted_by만 채운다.
 *       Entity에는 {@code @SQLRestriction("deleted_at IS NULL")}을 붙여 조회에서 제외한다.</li>
 * </ul>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(nullable = false, updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(nullable = false, length = 100)
    private String updatedBy;

    private LocalDateTime deletedAt;

    @Column(length = 100)
    private String deletedBy;

    /** 논리 삭제. 이미 삭제된 경우 처음 삭제 기록을 유지한다. */
    public void softDelete(String deletedBy) {
        if (isDeleted()) {
            return;
        }
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
