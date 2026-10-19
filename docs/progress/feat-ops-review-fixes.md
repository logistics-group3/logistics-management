# feat/ops-review-fixes

## 지금 하는 일
운영 리뷰 이슈 #11·#12·#14·#16을 한 PR로 수정 중. #11 구현, #12 정책 유지 기록, #14 구현 완료, #16 비동기 접수 설계 전

## 진행 상태
- [x] 사용자 승인: 2026-10-19 배송 행 비관적 잠금, D-04 원격 조회는 트랜잭션 밖으로 이동. 최신 origin/dev `a6313ee`에서 별도 worktree로 시작(다른 AI가 #13·#15를 진행 중인 feat/delivery-T07 작업 트리와 분리).
- [x] 재현 테스트 먼저 작성: 수정 전 코드에서 5개 중 4개 실패(재배정 뒤 구간 MOVING 복귀, 출발·취소 둘 다 성공, 삭제 뒤 출발 성공), 다른 배송 독립 1개는 통과.
- [x] `findByIdForUpdate`·`findByOrderIdForUpdate` 추가, D-03~D-05·I-11~I-13 적용, D-04 구조 변경. 배송 테스트 통과, 동시성 테스트 3회 반복 통과.
- [x] `./gradlew build` 성공(delivery 테스트 44개 통과), `script/docs-check.sh`·`script/error-codes-check.sh`·`git diff --check` 통과. 프런트 계약·API 응답은 바뀌지 않아 프런트 검증은 하지 않았다.
- [x] #11 커밋 완료. 푸시·PR은 #12·#14·#16까지 수정한 뒤 한 번에 한다(사용자 결정 2026-10-19). #13은 feat/delivery-T07, #15는 다른 AI가 진행한다.
- [x] #12: 발제 원본이 "조회·검색은 모든 로그인 사용자 가능(담당자는 본인 배송만)"이라 사용자가 발제 원본 우선으로 일반 USER 전체 조회 유지를 결정(2026-10-19). 행 제한안(주문자 컬럼, 요청업체 관리자 기준)은 폐기했다. 코드 변경 없이 DELIVERY-006·SA 6.8·SA 9장·결정 로그 22에 기록했다. 마스킹은 order I-09에 공급업체 관리자 ID가 필요해 팀 판단 후 별도 과제다. 이슈 #12는 "코드 수정 없음, 정책 유지"로 PR에 설명한다.
- [x] #14 범위 결정(사용자 2026-10-19): 묘비+결과조회. 종료 표시 `p_delivery_creation_closures`(V2, 이전 삭제분 채움), I-13이 항상 표를 남기고 I-09는 표가 있으면 409 DELIVERY_CREATION_CLOSED, I-09 저장·I-13을 orderId advisory lock으로 직렬화, I-17(`GET /internal/deliveries/orders/{orderId}` → ACTIVE·CLOSED·NONE). 시도 토큰은 orderId가 이미 시도 식별자라 두지 않았다. 재현 테스트가 수정 전 실패(고아 배송 1건)를 확인했다. delivery 테스트 72개 통과(3회 반복). order의 복구 API·RECOVERY_REQUIRED 자동 종료는 이번 범위 밖.
- [ ] #16: 사용자가 "비동기 접수까지"를 선택했다(O-01 202 접수 + 진행 조회). API 응답·프런트 계약 변경이 커서 설계를 설명하고 확인받은 뒤 구현한다.
- 면접 포인트: 트랜잭션과 동시성 제어의 차이, 비관적·낙관적 잠금 선택 기준, 잠금 안의 원격 호출 위험.

## 다음 단계
1. #16 비동기 접수 설계를 설명하고 확인받은 뒤 구현한다. 끝나면 `./gradlew build`, 프런트 검증, E2E를 확인하고 한 PR로 올린다. #13(feat/delivery-T07)과 `DeliveryService.java`·`DeliveryServiceTest.java`·delivery README·SA·결정 로그가 겹치므로 먼저 병합되는 쪽에 맞춰 rebase한다.
2. 최신 dev(`7a7bbf8`, #13·#15 병합)에 rebase를 마쳤다.

## 직접 해보기
1. `DeliveryService.changeRouteStatus`에서 `getDeliveryForUpdate`를 `deliveryRepository.findById`로 임시로 바꾼다.
2. `./gradlew :delivery-service:test --tests '*DeliveryConcurrencyIntegrationTest'`를 실행해 4개가 실패하는지 본다(Docker 필요).
3. 되돌린 뒤 다시 실행해 통과를 확인한다.

## 메모
- 결정: [결정 로그 21](../../delivery-service/docs/domain/delivery-decision-log.md), DELIVERY-004([서비스 README](../services/delivery/README.md)), SA 7.5.
- 잠금 타임아웃은 두지 않았다. 장시간 대기가 문제가 되면 별도로 검토한다.
