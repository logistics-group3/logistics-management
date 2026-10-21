package com.sparta.logistics.order.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.dsl.StringTemplate;

import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.annotations.Generated;
import com.querydsl.core.types.Path;


/**
 * QOrderDeletion is a Querydsl query type for OrderDeletion
 */
@SuppressWarnings("this-escape")
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QOrderDeletion extends EntityPathBase<OrderDeletion> {

    private static final long serialVersionUID = -1268816973L;

    public static final QOrderDeletion orderDeletion = new QOrderDeletion("orderDeletion");

    public final com.sparta.logistics.common.entity.QBaseEntity _super = new com.sparta.logistics.common.entity.QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdAt = _super.createdAt;

    //inherited
    public final StringPath createdBy = _super.createdBy;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> deletedAt = _super.deletedAt;

    //inherited
    public final StringPath deletedBy = _super.deletedBy;

    public final ComparablePath<java.util.UUID> deliveryId = createComparable("deliveryId", java.util.UUID.class);

    public final EnumPath<DeletionEligibility> eligibility = createEnum("eligibility", DeletionEligibility.class);

    public final EnumPath<DeletionFailureReason> failureReason = createEnum("failureReason", DeletionFailureReason.class);

    public final ComparablePath<java.util.UUID> id = createComparable("id", java.util.UUID.class);

    public final ComparablePath<java.util.UUID> orderId = createComparable("orderId", java.util.UUID.class);

    public final EnumPath<OrderDeletionStatus> status = createEnum("status", OrderDeletionStatus.class);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> updatedAt = _super.updatedAt;

    //inherited
    public final StringPath updatedBy = _super.updatedBy;

    public QOrderDeletion(String variable) {
        super(OrderDeletion.class, forVariable(variable));
    }

    public QOrderDeletion(Path<? extends OrderDeletion> path) {
        super(path.getType(), path.getMetadata());
    }

    public QOrderDeletion(PathMetadata metadata) {
        super(OrderDeletion.class, metadata);
    }

}

