# feat/ops-review-fixes

## 지금 하는 일
운영 리뷰 이슈 #11·#12·#14·#16을 한 PR로 수정 중. #11·#14·#16 구현, #12 정책 유지 기록 완료. 최종 검증 후 PR 대기

## 진행 상태
- [x] 사용자 승인: 2026-10-19 배송 행 비관적 잠금, D-04 원격 조회는 트랜잭션 밖으로 이동. 최신 origin/dev `a6313ee`에서 별도 worktree로 시작(다른 AI가 #13·#15를 진행 중인 feat/delivery-T07 작업 트리와 분리).
- [x] 재현 테스트 먼저 작성: 수정 전 코드에서 5개 중 4개 실패(재배정 뒤 구간 MOVING 복귀, 출발·취소 둘 다 성공, 삭제 뒤 출발 성공), 다른 배송 독립 1개는 통과.
- [x] `findByIdForUpdate`·`findByOrderIdForUpdate` 추가, D-03~D-05·I-11~I-13 적용, D-04 구조 변경. 배송 테스트 통과, 동시성 테스트 3회 반복 통과.
- [x] `./gradlew build` 성공(delivery 테스트 44개 통과), `script/docs-check.sh`·`script/error-codes-check.sh`·`git diff --check` 통과. 프런트 계약·API 응답은 바뀌지 않아 프런트 검증은 하지 않았다.
- [x] #11 커밋 완료. 푸시·PR은 #12·#14·#16까지 수정한 뒤 한 번에 한다(사용자 결정 2026-10-19). #13은 feat/delivery-T07, #15는 다른 AI가 진행한다.
- [x] #12: 발제 원본이 "조회·검색은 모든 로그인 사용자 가능(담당자는 본인 배송만)"이라 사용자가 발제 원본 우선으로 일반 USER 전체 조회 유지를 결정(2026-10-19). 행 제한안(주문자 컬럼, 요청업체 관리자 기준)은 폐기했다. 코드 변경 없이 DELIVERY-006·SA 6.8·SA 9장·결정 로그 22에 기록했다. 마스킹은 order I-09에 공급업체 관리자 ID가 필요해 팀 판단 후 별도 과제다. 이슈 #12는 "코드 수정 없음, 정책 유지"로 PR에 설명한다.
- [x] #14 범위 결정(사용자 2026-10-19): 묘비+결과조회. 종료 표시 `p_delivery_creation_closures`(V2, 이전 삭제분 채움), I-13이 항상 표를 남기고 I-09는 표가 있으면 409 DELIVERY_CREATION_CLOSED, I-09 저장·I-13을 orderId advisory lock으로 직렬화, I-17(`GET /internal/deliveries/orders/{orderId}` → ACTIVE·CLOSED·NONE). 시도 토큰은 orderId가 이미 시도 식별자라 두지 않았다. 재현 테스트가 수정 전 실패(고아 배송 1건)를 확인했다. delivery 테스트 72개 통과(3회 반복). order의 복구 API·RECOVERY_REQUIRED 자동 종료는 이번 범위 밖.
- [x] #16(사용자 선택 2026-10-19: 비동기 접수까지, 같은 키 처리 중 재요청은 202): O-01을 접수(202)와 처리(별도 스레드)로 분리했다. 유한 실행기(스레드 4·대기열 50, 포화 시 503), 접수 단계 8초 마감, order 로컬 Feign 시간 예산(order-company 1s/1.5s, order-delivery-create 1s/12s, order-delivery-delete 1s/3s), 진행 조회 O-07(`GET /api/v1/orders/creations/{orderId}`), 실패 사유 저장 V6(`failure_code`·`failure_message`). 기존 동기 흐름은 `OrderService.create`로 유지해 보상 규칙 테스트를 보존했다. 결정은 order ADR 0010.
- [x] #16 검증: order 463개(새 `OrderCreationAsyncIntegrationTest` 4개 포함, 실제 스레드풀로 접수 후 지연·같은 키 재요청 202·4xx 사유·포화 503·권한)와 WebMvc 48개 통과. 프런트 build·lint·format:check·test 56개 통과. 백엔드 전체 `./gradlew build` 통과. 시간 예산 숫자는 설정에서 계산한 상한이며 지연 주입 실측은 하지 않았다.
- [x] E2E(2026-10-19, 전용 `logistics-ui-test` 스택, Slack DM·Webhook·토큰 비움, 임시 UI 5175): 이 브랜치 코드로 이미지를 새로 빌드해 기존 데이터가 있는 DB에 order V6·delivery V2 마이그레이션이 적용되는 것을 확인했다. 첫 전체 실행 17/19 통과. 실패 2건 중 하나는 직접 API로 주문하는 시나리오가 201을 기대한 테스트 수정 누락(수정함), 다른 하나는 UI 주문 생성이 접수 단계 503으로 실패한 것인데 원인 미확인이며 단독 실행 1회와 전체 실행 2회에서 재현되지 않았다. 수정 후 전체 19/19 통과(2회). order 재시작 직후 약 6초간 접수가 503인 구간을 직접 관찰했으나 첫 실패와 같은 원인인지는 확인하지 못했다.
- [x] E2E 환경 정리: 스택 내림(볼륨 `logistics-ui-test_pgdata` 보존, 기존 시드 유지), 임시 UI 서버 종료, worktree의 `.env.ui-test` 사본 삭제. 메인 트리의 `.env.ui-test`(DM true)와 개발 스택은 건드리지 않았다. 주의: 이 볼륨은 order V6·delivery V2가 적용돼 있어, 이 브랜치가 병합되기 전의 코드로 같은 볼륨을 쓰면 Flyway 검증이 실패할 수 있다(미확인).
- [x] 테스트 인프라: 통합 테스트 컨텍스트가 늘어 PostgreSQL 연결이 고갈(`too many clients already`)돼 18개가 실패했다. order 테스트 전용 풀 상한(`order-service/src/test/resources/application.properties`)으로 임시 방어했고 463개가 통과했다. 기록은 [TS-025](../troubleshooting.md)·[STAR 기록](../reviews/2026-10-19-test-connection-exhaustion.md), 정식 해결은 [작업 계획](../reviews/2026-10-19-test-connection-exhaustion-plan.md)이다.
- 면접 포인트: 트랜잭션과 동시성 제어의 차이, 비관적·낙관적 잠금 선택 기준, 잠금 안의 원격 호출 위험.

## 다음 단계
1. 최종 `./gradlew build --rerun-tasks`·프런트 검증을 한 번 더 하고 한 PR로 올린다(사용자 확인 후).
2. 연결 고갈의 정식 대책은 별도 PR로 계획 문서를 따른다. #13(feat/delivery-T07)과 `DeliveryService.java`·`DeliveryServiceTest.java`·delivery README·SA·결정 로그가 겹치므로 먼저 병합되는 쪽에 맞춰 rebase한다.

## 직접 해보기
1. `DeliveryService.changeRouteStatus`에서 `getDeliveryForUpdate`를 `deliveryRepository.findById`로 임시로 바꾼다.
2. `./gradlew :delivery-service:test --tests '*DeliveryConcurrencyIntegrationTest'`를 실행해 4개가 실패하는지 본다(Docker 필요).
3. 되돌린 뒤 다시 실행해 통과를 확인한다.

## 메모
- 결정: [결정 로그 21](../../delivery-service/docs/domain/delivery-decision-log.md), DELIVERY-004([서비스 README](../services/delivery/README.md)), SA 7.5.
- 잠금 타임아웃은 두지 않았다. 장시간 대기가 문제가 되면 별도로 검토한다.
