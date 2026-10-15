package com.sparta.logistics.order.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CreateOrderCommandTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCommands")
    @DisplayName("외부 서비스를 호출하기 전에 잘못된 주문 입력을 거부한다")
    void rejectsInvalidInput(String scenario, UUID supplierId, UUID receiverId, UUID productId,
                             int quantity, String requestNote) {
        assertThatThrownBy(() -> new CreateOrderCommand(supplierId, receiverId, productId, quantity, requestNote))
                .as(scenario)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    private static Stream<Arguments> invalidCommands() {
        UUID supplier = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        return Stream.of(
                Arguments.of("공급업체 ID 없음", null, receiver, product, 1, null),
                Arguments.of("수령업체 ID 없음", supplier, null, product, 1, null),
                Arguments.of("상품 ID 없음", supplier, receiver, null, 1, null),
                Arguments.of("같은 업체 사이 주문", supplier, supplier, product, 1, null),
                Arguments.of("수량 0", supplier, receiver, product, 0, null),
                Arguments.of("음수 수량", supplier, receiver, product, -1, null),
                Arguments.of("요청사항 501자", supplier, receiver, product, 1, "가".repeat(501))
        );
    }
}
