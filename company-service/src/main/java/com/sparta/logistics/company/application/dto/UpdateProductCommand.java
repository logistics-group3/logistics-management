package com.sparta.logistics.company.application.dto;

/** P-04 입력. null인 값은 바꾸지 않는다. */
public record UpdateProductCommand(
        String name,
        String description,
        Long price
) {
}
