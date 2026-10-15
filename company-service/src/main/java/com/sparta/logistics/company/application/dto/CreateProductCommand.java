package com.sparta.logistics.company.application.dto;

/** P-01 입력 */
public record CreateProductCommand(
        String name,
        String description,
        Long price
) {
}
