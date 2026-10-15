# feat/delivery-T01

## 지금 하는 일
T01~T05 구현 기준선 커밋 완료, 사용자가 직접 리뷰·수정 중

## 진행 상태
- [x] `delivery-service/docs/` 작업 규칙, 태스크 목록, T01 문서 작성 (문서만 변경)
- [x] `docs/domain/` 정리(이모지, 옛 경로, 저장소 상태 반영)와 결정 로그 규칙, 결정 로그 11 추가 (문서만 변경)
- [x] 개인 훅(`.claude/hooks/context-guard.sh`, `post-compact.sh`) 단독 실행 테스트 통과. context-guard는 실제 세션에서 142k 토큰에 발동 확인(10/15), post-compact는 압축 전이라 미확인
- [x] ADR 0003 채택(SA v2.0 3구간), 0001 대체됨 (문서만 변경)
- [x] V1 마이그레이션과 엔티티, CHECK 검증 통합 테스트 (`:delivery-service:test` 2개 통과)
- [x] 상태 전이, 에러 코드, Repository (T01)
- [x] I-09 생성·자동 배정·Feign(T02), D-01~03 조회·수정(T03), D-04·D-05(T04), I-10~I-13(T05). `:delivery-service:test` 37개 통과
- [ ] Gateway 경유 호출: user I-14, hub I-03이 제공되기 전이라 미확인
- 면접 포인트: 상태 전이를 서비스가 아니라 엔티티 메서드에서 검증하는 이유, 배송 상태를 구간 상태에서 계산하는 이유

## 다음 단계
1. 사용자가 전체 코드를 직접 리뷰하며 주석·코드를 고친다(기준선 커밋 `feat(delivery): 배송 API 구현 (T01~T05)` 이후). 수정은 `refactor(delivery): ... (T01)`처럼 별도 커밋으로 남기고 `tasks/T01.md`에 기록한다.
2. 리뷰에서 나온 후보: 구간 생성 파라미터가 헷갈림(`addRoute(PICKUP, null, departureHubId, ...)`) → 유형별 팩토리(`pickup`, `hubTransfer`, `lastMile`)로 바꾸기.
3. HUB-001(hub I-03), USER-001(user I-14) 답을 받으면 `script/run.sh`로 Gateway 경유 I-09 → D-05 흐름을 확인한다.

검증(2026-10-15, 이 세션에서 직접 실행): `./gradlew :delivery-service:test` 37개 통과, `./gradlew build` 통과, `script/docs-check.sh` OK, `script/error-codes-check.sh` PASS.

## 직접 해보기
상태 전이는 사용자가 직접 구현하기로 했으나(2026-10-15) 이후 전체 구현 요청으로 에이전트가 구현했다. 아래 단계는 구현된 코드(`Delivery`, `DeliveryRoute`, `DeliveryTest`)를 읽으며 복습하는 순서로 쓴다. 규칙은 `delivery-service/docs/domain/delivery.md` 3-1, 3-2.

1. `DeliveryRoute`에 `start()`, `arrive(RouteInfo actual)`, `cancel()`을 만든다. 접근 제한자는 package-private(Delivery만 호출).
   - 허용되지 않는 상태면 `throw new BusinessException(CommonErrorCode.INVALID_STATUS_TRANSITION)`.
   - 확인: `delivery-service/src/test/java/com/sparta/logistics/delivery/domain/DeliveryRouteTest.java`를 만들고 `./gradlew :delivery-service:test --tests "*DeliveryRouteTest"` 통과.
2. `Delivery.create(...)`를 만든다. 출발 허브와 목적지 허브가 같으면 PICKUP·LAST_MILE 2구간, 다르면 3구간. 상태는 `PICKUP_WAITING`, 구간은 모두 `WAITING`.
3. `Delivery.startRoute(routeId)`, `arriveRoute(routeId, actual)`를 만든다. 앞 구간이 ARRIVED인지 확인하고, 구간을 바꾼 뒤 배송 상태를 바꾼다.
4. `Delivery.cancel()`, `revertCancel()`을 만든다.
5. 테스트는 `delivery-service/docs/workflow.md` 7절 기준: 3구간·2구간 전체 흐름, 순서 위반, 집하 시작 후 취소 불가.
6. 다 되면 "리뷰해줘"로 `learn-review`를 받는다.

IntelliJ: 테스트 클래스에서 클래스 이름 옆 실행 버튼(Ctrl+Shift+R / ^⇧R)으로 단위 테스트만 빠르게 돌린다. 단위 테스트는 Docker가 필요 없다.

## 메모
- `docs/design/05-infra*`는 공통 작업 브랜치로 올린다(스테이징 해제함).
- 루트 `.gitignore`에 개인용 `blog` 줄이 커밋되지 않은 채 남아 있다. 개인 제외 경로는 `.git/info/exclude`로 옮기는 것이 팀 규칙이다.
