package com.sparta.logistics.hub.application.dto;

/** I-06 결과. 이번 호출에서 되돌린 수량이며 차감 이력이 없거나 이미 복원했으면 0이다. */
public record StockRestoreResult(int restoredQuantity) {
}
