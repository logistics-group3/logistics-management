package com.sparta.logistics.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * 공통 Audit 컬럼 6개 + 논리 삭제 (SA 5.1)
 * created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
 *
 * 각 엔티티에는 아래를 반드시 함께 붙인다. (@MappedSuperclass 에 두면 상속되지 않음)
 * <pre>
 * &#64;Entity
 * &#64;Table(name = "p_xxx")
 * &#64;SQLRestriction("deleted_at IS NULL")
 * public class Xxx extends BaseEntity { ... }
 * </pre>
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseCreatedEntity {

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

    /** 논리 삭제. DELETE API 는 이 메서드만 호출하고 실제 행은 지우지 않는다. */
    public void softDelete(String username) {
        if (isDeleted()) {
            return;
        }
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = username;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
