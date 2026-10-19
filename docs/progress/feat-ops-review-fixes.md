# feat/ops-review-fixes

## 지금 하는 일
운영 리뷰 이슈 #11·#12·#14·#16을 한 PR로 수정 중. #11 구현·검증 완료

## 진행 상태
- [x] 사용자 승인: 2026-10-19 배송 행 비관적 잠금, D-04 원격 조회는 트랜잭션 밖으로 이동. 최신 origin/dev `a6313ee`에서 별도 worktree로 시작(다른 AI가 #13·#15를 진행 중인 feat/delivery-T07 작업 트리와 분리).
- [x] 재현 테스트 먼저 작성: 수정 전 코드에서 5개 중 4개 실패(재배정 뒤 구간 MOVING 복귀, 출발·취소 둘 다 성공, 삭제 뒤 출발 성공), 다른 배송 독립 1개는 통과.
- [x] `findByIdForUpdate`·`findByOrderIdForUpdate` 추가, D-03~D-05·I-11~I-13 적용, D-04 구조 변경. 배송 테스트 통과, 동시성 테스트 3회 반복 통과.
- [x] `./gradlew build` 성공(delivery 테스트 44개 통과), `script/docs-check.sh`·`script/error-codes-check.sh`·`git diff --check` 통과. 프런트 계약·API 응답은 바뀌지 않아 프런트 검증은 하지 않았다.
- [x] #11 커밋 완료. 푸시·PR은 #12·#14·#16까지 수정한 뒤 한 번에 한다(사용자 결정 2026-10-19). #13은 feat/delivery-T07, #15는 다른 AI가 진행한다.
- 면접 포인트: 트랜잭션과 동시성 제어의 차이, 비관적·낙관적 잠금 선택 기준, 잠금 안의 원격 호출 위험.

## 다음 단계
1. #12 → #14+#16 순서로 수정한 뒤 한 PR로 올린다. #13(feat/delivery-T07)과 `DeliveryService.java`·`DeliveryServiceTest.java`·delivery README·SA·결정 로그가 겹치므로 먼저 병합되는 쪽에 맞춰 rebase한다. 
2. #12는 정책 합의가 필요하므로 선택지 설명부터 시작한다.

## 직접 해보기
1. `DeliveryService.changeRouteStatus`에서 `getDeliveryForUpdate`를 `deliveryRepository.findById`로 임시로 바꾼다.
2. `./gradlew :delivery-service:test --tests '*DeliveryConcurrencyIntegrationTest'`를 실행해 4개가 실패하는지 본다(Docker 필요).
3. 되돌린 뒤 다시 실행해 통과를 확인한다.

## 메모
- 결정: [결정 로그 21](../../delivery-service/docs/domain/delivery-decision-log.md), DELIVERY-004([서비스 README](../services/delivery/README.md)), SA 7.5.
- 잠금 타임아웃은 두지 않았다. 장시간 대기가 문제가 되면 별도로 검토한다.
