package com.sparta.logistics.order.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.dsl.StringTemplate;

import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.annotations.Generated;
import com.querydsl.core.types.Path;


/**
 * QOrderCreation is a Querydsl query type for OrderCreation
 */
@SuppressWarnings("this-escape")
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QOrderCreation extends EntityPathBase<OrderCreation> {

    private static final long serialVersionUID = -268112188L;

    public static final QOrderCreation orderCreation = new QOrderCreation("orderCreation");

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

    public final EnumPath<CreationFailureReason> failureReason = createEnum("failureReason", CreationFailureReason.class);

    public final ComparablePath<java.util.UUID> id = createComparable("id", java.util.UUID.class);

    public final StringPath idempotencyKey = createString("idempotencyKey");

    public final ComparablePath<java.util.UUID> ordererId = createComparable("ordererId", java.util.UUID.class);

    public final ComparablePath<java.util.UUID> orderId = createComparable("orderId", java.util.UUID.class);

    public final StringPath requestHash = createString("requestHash");

    public final SimplePath<OrderCreationSnapshot> responseSnapshot = createSimple("responseSnapshot", OrderCreationSnapshot.class);

    public final EnumPath<OrderCreationStatus> status = createEnum("status", OrderCreationStatus.class);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> updatedAt = _super.updatedAt;

    //inherited
    public final StringPath updatedBy = _super.updatedBy;

    public QOrderCreation(String variable) {
        super(OrderCreation.class, forVariable(variable));
    }

    public QOrderCreation(Path<? extends OrderCreation> path) {
        super(path.getType(), path.getMetadata());
    }

    public QOrderCreation(PathMetadata metadata) {
        super(OrderCreation.class, metadata);
    }

}

