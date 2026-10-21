package com.sparta.logistics.order.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.dsl.StringTemplate;

import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.annotations.Generated;
import com.querydsl.core.types.Path;


/**
 * QOrder is a Querydsl query type for Order
 */
@SuppressWarnings("this-escape")
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QOrder extends EntityPathBase<Order> {

    private static final long serialVersionUID = 2041557189L;

    public static final QOrder order = new QOrder("order1");

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

    public final ComparablePath<java.util.UUID> id = createComparable("id", java.util.UUID.class);

    public final ComparablePath<java.util.UUID> ordererId = createComparable("ordererId", java.util.UUID.class);

    public final ComparablePath<java.util.UUID> productId = createComparable("productId", java.util.UUID.class);

    public final StringPath productName = createString("productName");

    public final NumberPath<Integer> quantity = createNumber("quantity", Integer.class);

    public final ComparablePath<java.util.UUID> receiverCompanyId = createComparable("receiverCompanyId", java.util.UUID.class);

    public final StringPath requestNote = createString("requestNote");

    public final EnumPath<OrderStatus> status = createEnum("status", OrderStatus.class);

    public final ComparablePath<java.util.UUID> supplierCompanyId = createComparable("supplierCompanyId", java.util.UUID.class);

    public final NumberPath<Long> totalPrice = createNumber("totalPrice", Long.class);

    public final NumberPath<Long> unitPrice = createNumber("unitPrice", Long.class);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> updatedAt = _super.updatedAt;

    //inherited
    public final StringPath updatedBy = _super.updatedBy;

    public QOrder(String variable) {
        super(Order.class, forVariable(variable));
    }

    public QOrder(Path<? extends Order> path) {
        super(path.getType(), path.getMetadata());
    }

    public QOrder(PathMetadata metadata) {
        super(Order.class, metadata);
    }

}

