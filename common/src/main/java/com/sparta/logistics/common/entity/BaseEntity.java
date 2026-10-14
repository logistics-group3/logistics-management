package com.sparta.logistics.common.entity;

import com.sparta.logistics.common.security.UserContextHolder;
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
 * 감사 6컬럼 + 논리 삭제 (ERD 공통 규칙).
 * 조회에서 삭제 건을 빼려면 각 엔티티에 {@code @SQLRestriction("deleted_at IS NULL")}을 선언한다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    /** 논리 삭제. 삭제자는 X-Username 헤더, 없으면 SYSTEM. */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = UserContextHolder.currentUsernameOrSystem();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
