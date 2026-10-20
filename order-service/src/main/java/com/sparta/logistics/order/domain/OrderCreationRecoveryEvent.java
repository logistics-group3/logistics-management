package com.sparta.logistics.order.domain;

import com.sparta.logistics.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "p_order_creation_recovery_events")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCreationRecoveryEvent extends BaseEntity {
    @Id @Column(name = "event_id") private UUID id;
    @Column(nullable = false, length = 30) private String step;
    @Column(nullable = false, length = 500) private String detail;
    @Column(name = "execution_id", nullable = false) private UUID executionId;
    @Column(name = "recovery_id", nullable = false) private UUID recoveryId;

    public static OrderCreationRecoveryEvent record(OrderCreationRecovery recovery, String step, String detail) {
        OrderCreationRecoveryEvent event = new OrderCreationRecoveryEvent();
        event.id = UUID.randomUUID();
        event.recoveryId = recovery.getId();
        event.executionId = recovery.getExecutionId();
        event.step = step;
        event.detail = detail.length() > 500 ? detail.substring(0, 500) : detail;
        return event;
    }
}
