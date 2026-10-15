package com.sparta.logistics.delivery.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sparta.logistics.delivery.application.dto.UpdateRecipientCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** D-03 요청. 보낸 필드만 고친다. 보낸 값은 공백일 수 없다. */
public record UpdateDeliveryRequest(
        @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "공백만 입력할 수 없습니다.") String deliveryAddress,
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "공백만 입력할 수 없습니다.") String recipientName
) {

    @JsonIgnore
    @AssertTrue(message = "배송지와 수령인 중 하나 이상을 보내야 합니다.")
    public boolean isAnyFieldPresent() {
        return deliveryAddress != null || recipientName != null;
    }

    public UpdateRecipientCommand toCommand() {
        return new UpdateRecipientCommand(deliveryAddress, recipientName);
    }
}
