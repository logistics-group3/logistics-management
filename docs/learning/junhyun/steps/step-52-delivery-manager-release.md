# 스텝 52 — 지정 해제된 배송담당자의 변경 권한 차단

- 날짜: 2026-10-19.
- 브랜치: feat/delivery-T07, 기준 origin/dev a6313ee.
- 사용자 요청: #13 상세 계획 확인 후 구현.
- 상태·검증 정본: [T07](../../../../delivery-service/docs/tasks/T07.md).

## 무엇을 바꿨나

배송지·수령인 수정(D-03)과 출발·도착(D-05)은 현재 배송담당자이면서 해당 구간 담당자인 사용자 또는 MASTER만 허용한다. 배정 ID가 남아 있어도 자격이 false면 403이고 데이터는 그대로다.

DeliveryService의 validateAssignedManager를 두 경로에서 호출한다. 프런트 nextRouteAction도 자격을 확인한다. 추가 DB 컬럼·원격 호출은 없으며 common의 일반 소유자 검사를 바꾸지 않았다.

## 왜 이렇게 했나

managerId는 배정 관계다. 현재 배송 업무를 할 자격은 user의 isDeliveryManager다. 자격을 해제해도 ID는 남기 때문에 둘을 함께 확인해야 한다.

MASTER의 감독·인계 권한은 별도로 허용한다. 남은 WAITING·MOVING 구간은 MASTER가 기존 D-04로 현재 담당자에게 수동 인계한다.

## 검증 과정과 한계

1. 기존 코드에서 거절 회귀 테스트가 단위·HTTP 4건, 프런트 2건 실패하는 것을 확인했다.
2. 배송 61개·프런트 48개가 통과했다. PostgreSQL·HTTP로 거절 후 값 보존, 현재 담당자와 MASTER 허용을 확인했다.
3. ./gradlew build가 성공했다. 전체 JUnit XML 788개 중 이번 배송 61개를 새로 실행했고 다른 모듈은 UP-TO-DATE를 사용했다. 프런트 build·lint·format:check도 성공했다.
4. 실제 JWT·Gateway의 60초 캐시가 만료된 뒤 기존 담당자의 두 변경 API가 403인 것, 재배정 후 새 담당자가 출발하는 회귀를 단독 실행했다. 최종 전체 E2E 결과는 T07에 기록한다.

해제 직후 즉시 차단은 보장하지 않는다. Gateway가 true를 캐시한 동안 최대 60초 지연이 있다. /users/me가 false여도 Gateway 캐시는 true일 수 있어 배송 API의 무변경 요청(금지된 WAITING 전이)으로 실제 인가의 409→403을 확인했다.

## 직접 확인

1. IntelliJ에서 DeliveryServiceTest의 rejectsUnauthorizedRouteChange·rejectsUnauthorizedRecipientChange를 연다. ID는 같고 자격만 false인 입력을 확인한다.
2. DeliveryIntegrationTest의 HTTP 권한 표를 보고 403 뒤 before와 after가 같은지 확인한다.
3. frontend/e2e/delivery-authorization.spec.ts에서 JWT를 다시 발급하지 않고 Gateway 캐시 반영을 기다리는 부분을 읽는다.

면접 포인트: 소유자와 현재 자격의 차이, 최신 DB 조회와 캐시된 인증 정보의 차이, 같은 API의 403과 409가 확인하는 경계.
