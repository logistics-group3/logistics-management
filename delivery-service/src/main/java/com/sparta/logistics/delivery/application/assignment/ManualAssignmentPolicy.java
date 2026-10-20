package com.sparta.logistics.delivery.application.assignment;

import java.time.Duration;

/**
 * 수동 지정으로 넘기는 기준(결정 로그 29). 확정 뒤 이 시간이 지나도록 후보가 0명이면 넘긴다.
 * Note: "2번 더 시도"를 횟수 컬럼 대신 시간으로 센다. 주기 작업이 30초마다 돌면 60초는 확정 뒤 2~3번째 시도다.
 * 횟수로 세면 주문이 몰릴 때 새 주문 신호마다 같은 허브의 기존 대기 배송까지 횟수가 올라 너무 빨리 넘어간다.
 */
public record ManualAssignmentPolicy(Duration after) {
}
