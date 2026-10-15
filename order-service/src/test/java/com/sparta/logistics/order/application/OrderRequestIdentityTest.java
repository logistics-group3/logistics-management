package com.sparta.logistics.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.logistics.common.exception.CommonErrorCode;
import com.sparta.logistics.order.application.dto.CreateOrderCommand;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OrderRequestIdentityTest {

    private final UUID supplier = UUID.randomUUID();
    private final UUID receiver = UUID.randomUUID();
    private final UUID product = UUID.randomUUID();

    @Test
    @DisplayName("같은 파싱 결과는 같은 v1 지문이며 키 값은 본문 지문에 섞지 않는다")
    void fingerprintsParsedValues() {
        CreateOrderCommand command = command(2, "요청: 줄바꿈\n보존");

        assertThat(command.identity("key-1").requestHash()).matches("v1:[0-9a-f]{64}")
                .isEqualTo(command.identity("key-2").requestHash())
                .isEqualTo(new CreateOrderCommand(UUID.fromString(supplier.toString().toUpperCase()), receiver, product, 2,
                        "요청: 줄바꿈\n보존").identity("key-1").requestHash());
    }

    @Test
    @DisplayName("요청의 다섯 필드 중 하나가 바뀌면 지문도 달라진다")
    void distinguishesEveryRequestField() {
        String hash = command(2, "요청").identity("key").requestHash();

        assertThat(new CreateOrderCommand(UUID.randomUUID(), receiver, product, 2, "요청").identity("key").requestHash()).isNotEqualTo(hash);
        assertThat(new CreateOrderCommand(supplier, UUID.randomUUID(), product, 2, "요청").identity("key").requestHash()).isNotEqualTo(hash);
        assertThat(new CreateOrderCommand(supplier, receiver, UUID.randomUUID(), 2, "요청").identity("key").requestHash()).isNotEqualTo(hash);
        assertThat(command(3, "요청").identity("key").requestHash()).isNotEqualTo(hash);
        assertThat(command(2, "다른 요청").identity("key").requestHash()).isNotEqualTo(hash);
    }

    @Test
    @DisplayName("null·빈 문자열·문자열 null을 서로 다른 요청사항으로 구분한다")
    void distinguishesNullAndText() {
        assertThat(command(2, null).identity("key").requestHash())
                .isNotEqualTo(command(2, "").identity("key").requestHash())
                .isNotEqualTo(command(2, "null").identity("key").requestHash());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "key key", "키", "key,key", "key/key"})
    @DisplayName("키는 필수이며 공백·구분 쉼표·허용되지 않은 문자를 거부한다")
    void rejectsInvalidKeys(String key) {
        assertThatThrownBy(() -> command(2, null).identity(key)).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("128자 키까지 허용하고 129자는 거부한다")
    void limitsKeyLength() {
        assertThat(command(2, null).identity("a".repeat(128)).key()).hasSize(128);
        assertThatThrownBy(() -> command(2, null).identity("a".repeat(129))).extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("잘못된 유니코드가 대체 문자로 바뀌어 같은 지문이 되지 않도록 거부한다")
    void rejectsMalformedUnicode() {
        assertThatThrownBy(() -> command(2, "\uD800").identity("key"))
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }

    private CreateOrderCommand command(int quantity, String note) {
        return new CreateOrderCommand(supplier, receiver, product, quantity, note);
    }
}
