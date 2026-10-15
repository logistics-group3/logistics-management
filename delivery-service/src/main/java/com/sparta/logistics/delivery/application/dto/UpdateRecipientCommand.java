package com.sparta.logistics.delivery.application.dto;

/** D-03 입력. null인 값은 바꾸지 않는다. */
public record UpdateRecipientCommand(String deliveryAddress, String recipientName) {
}
