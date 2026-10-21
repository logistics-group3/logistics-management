package com.sparta.logistics.order.application.dto;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.domain.OrderRequestIdentity;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** O-01 입력. 주문자와 상품 가격은 요청에서 받지 않는다. */
public record CreateOrderCommand(UUID supplierCompanyId, UUID receiverCompanyId, UUID productId,
                                 int quantity, String requestNote) {

    public CreateOrderCommand {
        if (supplierCompanyId == null || receiverCompanyId == null || productId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "업체와 상품 ID는 필수입니다.");
        }
        if (supplierCompanyId.equals(receiverCompanyId)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "공급업체와 수령업체는 달라야 합니다.");
        }
        if (quantity < 1 || (requestNote != null && requestNote.length() > 500)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "수량은 1 이상이고 요청사항은 500자 이내여야 합니다.");
        }
    }

    public OrderRequestIdentity identity(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("order-create:v1".getBytes(StandardCharsets.UTF_8));
            byte[] note = null;
            if (requestNote != null) {
                ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(requestNote));
                note = new byte[encoded.remaining()];
                encoded.get(note);
            }
            ByteBuffer fields = ByteBuffer.allocate(56);
            for (UUID id : new UUID[]{supplierCompanyId, receiverCompanyId, productId}) {
                fields.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
            }
            // Note: 파싱한 값으로 지문을 만든다. 길이와 null 표식을 넣어 구분 문자·빈 문자열로 인한 모호함을 막는다.
            fields.putInt(quantity).putInt(note == null ? -1 : note.length);
            digest.update(fields.array());
            if (note != null) digest.update(note);
            return new OrderRequestIdentity(key, "v1:" + HexFormat.of().formatHex(digest.digest()));
        } catch (CharacterCodingException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "요청사항에 올바르지 않은 유니코드가 있습니다.");
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
