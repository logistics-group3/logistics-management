# fix/order-auto-abort-closed

## 지금 하는 일
이슈 #14의 남은 조건. I-09 결과를 모르는 주문 생성 시도를 I-13 보상 성공과 I-17 `CLOSED`로 확인하면 ABORTED로 닫는다. 구현·검증 완료, PR 리뷰 대기.

## 진행 상태
- [x] 설계 설명 뒤 사용자 승인(2026-10-19): 닫는 조건은 `CLOSED`만, 나머지는 RECOVERY_REQUIRED 유지, 응답은 그대로 503.
- [x] `DeliveryGateway.isCreationClosed`, 전용 Feign `order-delivery-state`(재시도 없음, 1초·2초), `OrderService.handleUnknownDelivery` 분기.
- [x] 테스트: 단위 3개(CLOSED, 미확인·조회 실패, 보상 실패), HTTP 통합 3건(CLOSED·ACTIVE·NONE). `./gradlew :order-service:test` 469개 통과.
- [x] 문서: order ADR 0011, integration-contract, failure-handling, README.
- [ ] 기동 직후 503 완화는 보류. 전용 스택 콜드 재기동 2회에서 재현되지 않았다(5개 서비스 등록 24~25초, 로그인 직후 order 200). LoadBalancer 캐시 가설도 맞지 않았다. 재현 조건 미확인.

## 다음 단계
1. PR 리뷰·병합 뒤 이슈 #14에 남은 조건 해소 코멘트를 남기고 닫을지 팀과 정한다.
2. 후보: 개인정보 마스킹(#12, 팀 합의), 지연 주입 실측과 서버 종료 후 회복 검증(#16), 이미 남은 RECOVERY_REQUIRED의 운영 복구.
