package com.sparta.logistics.order.domain;

import com.sparta.logistics.common.entity.BaseEntity;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/** 한 품목의 주문과 주문 시점 상품 정보를 보관한다(SA 5.5, O-01). */
@Getter
@Entity
@Table(name = "p_orders")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @Column(name = "order_id", nullable = false)
    private UUID id;

    @Column(name = "orderer_id", nullable = false)
    private UUID ordererId;

    @Column(name = "supplier_company_id", nullable = false)
    private UUID supplierCompanyId;

    @Column(name = "receiver_company_id", nullable = false)
    private UUID receiverCompanyId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "request_note", length = 500)
    private String requestNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /**
     * 배송 생성 성공 후 주문을 만든다. 업체·상품의 존재와 주문 권한은 application에서 검증한다.
     * Note: orderId는 배송 호출 전에 application에서 선발급해 전달한다. 여기서 새 ID를 만들면 보상에 쓰는 ID와 달라진다.
     */
    public static Order create(UUID orderId, UUID ordererId, UUID supplierCompanyId, UUID receiverCompanyId,
                               UUID productId, String productName, long unitPrice, int quantity,
                               UUID deliveryId, String requestNote) {
        if (orderId == null || ordererId == null || supplierCompanyId == null || receiverCompanyId == null
                || productId == null || deliveryId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "주문과 참조 리소스의 ID는 필수입니다.");
        }
        if (supplierCompanyId.equals(receiverCompanyId)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "공급업체와 수령업체는 달라야 합니다.");
        }
        if (productName == null || productName.isBlank() || productName.length() > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "상품명은 공백이 아닌 100자 이내의 값이어야 합니다.");
        }
        if (requestNote != null && requestNote.length() > 500) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "요청사항은 500자 이내여야 합니다.");
        }
        if (unitPrice < 0 || quantity < 1) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "단가는 0 이상이고 수량은 1 이상이어야 합니다.");
        }

        Order order = new Order();
        order.id = orderId;
        order.ordererId = ordererId;
        order.supplierCompanyId = supplierCompanyId;
        order.receiverCompanyId = receiverCompanyId;
        order.productId = productId;
        order.productName = productName;
        order.unitPrice = unitPrice;
        order.quantity = quantity;
        order.totalPrice = calculateTotalPrice(unitPrice, quantity);
        order.deliveryId = deliveryId;
        order.requestNote = requestNote;
        order.status = OrderStatus.ORDERED;
        return order;
    }

    /** O-04. 취소되지 않은 주문의 요청사항만 바꾼다. 상품·수량 변경은 취소 후 재주문한다. */
    public void updateRequestNote(String requestNote) {
        if (status != OrderStatus.ORDERED) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (requestNote == null || requestNote.isBlank() || requestNote.length() > 500) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "요청사항은 공백이 아닌 500자 이내의 값이어야 합니다.");
        }
        this.requestNote = requestNote;
    }

    private static long calculateTotalPrice(long unitPrice, int quantity) {
        try {
            // Note: 원 단위 BIGINT 계약(SA 5.5)을 지킨다. 일반 곱셈은 오버플로 때 예외 없이 잘린 금액을 만들 수 있다.
            return Math.multiplyExact(unitPrice, quantity);
        } catch (ArithmeticException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "주문 금액이 허용 범위를 초과했습니다.");
        }
    }
}
