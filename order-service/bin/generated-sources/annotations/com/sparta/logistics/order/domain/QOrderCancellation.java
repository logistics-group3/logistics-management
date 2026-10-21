package com.sparta.logistics.order.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.dsl.StringTemplate;

import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.annotations.Generated;
import com.querydsl.core.types.Path;


/**
 * QOrderCancellation is a Querydsl query type for OrderCancellation
 */
@SuppressWarnings("this-escape")
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QOrderCancellation extends EntityPathBase<OrderCancellation> {

    private static final long serialVersionUID = -780693912L;

    public static final QOrderCancellation orderCancellation = new QOrderCancellation("orderCancellation");

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

    public final EnumPath<CancellationFailureReason> failureReason = createEnum("failureReason", CancellationFailureReason.class);

    public final ComparablePath<java.util.UUID> id = createComparable("id", java.util.UUID.class);

    public final ComparablePath<java.util.UUID> orderId = createComparable("orderId", java.util.UUID.class);

    public final EnumPath<OrderCancellationStatus> status = createEnum("status", OrderCancellationStatus.class);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> updatedAt = _super.updatedAt;

    //inherited
    public final StringPath updatedBy = _super.updatedBy;

    public QOrderCancellation(String variable) {
        super(OrderCancellation.class, forVariable(variable));
    }

    public QOrderCancellation(Path<? extends OrderCancellation> path) {
        super(path.getType(), path.getMetadata());
    }

    public QOrderCancellation(PathMetadata metadata) {
        super(OrderCancellation.class, metadata);
    }

}

