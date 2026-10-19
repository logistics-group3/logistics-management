# 운영 개선 이슈와 첫 수정 계획

- 날짜: 2026-10-19.
- 요청: 운영 리뷰의 번호 붙인 핵심 문제 6개를 GitHub 이슈로 등록하고, 간단한 것부터 한 항목씩 상세 설명 후 수정한다.
- 진행 방식: 각 항목의 변경 파일·처리 방식·테스트를 먼저 설명한다. 사용자가 “다 봤다”고 하면 설명한 항목을 구현·검증하고 결과를 보고한다. 다음 항목은 다시 설명한 뒤 같은 절차를 따른다.
- 현재 상태: #13은 로컬 구현·검증 완료다. 최종 E2E 19개와 환경 복구를 확인했다. 마지막 소스 원복 관찰은 사용자 재적용 요청 후 같은 수정안을 복원하고 배송 테스트 61개를 강제 새 실행해 해결했다. 결과 정본은 [T07](../../delivery-service/docs/tasks/T07.md), 다음 사용자 확인 대상은 [#15 수정안](2026-10-19-issue-15-plan.md)이다. 커밋·푸시·이슈 종료는 하지 않았다.

## 이슈와 수정 순서

앞선 리뷰에서 번호를 붙인 R01~R06만 등록했다. 추가 운영 위험 R07~R12는 이번 6개에 포함하지 않았다. 아래 순서는 심각도 순위와 구분한 예상 수정 범위 순서이며, 각 항목 시작 시 의존성을 다시 확인한다.

| 순서 | GitHub 이슈 | 리뷰 ID | 간단함을 판단한 근거 |
|---|---|---|---|
| 1 | [#13 지정 해제된 담당자의 배송 변경 권한 차단](https://github.com/logistics-group3/logistics-management/issues/13) | R03 | 기존 UserContext의 플래그를 검사한다. DB·내부 호출 추가 없이 적용 가능하다. |
| 2 | [#15 업체 삭제와 상품 생성의 동시 실행 보호](https://github.com/logistics-group3/logistics-management/issues/15) | R05 | company 서비스 안에서 같은 업체 행의 쓰기 경계를 보호한다. |
| 3 | [#11 동시 변경으로 배송·구간 상태가 덮어써지는 문제 방지](https://github.com/logistics-group3/logistics-management/issues/11) | R01 | 배송·구간 여러 변경 경로를 함께 보호하고 충돌 처리를 정해야 한다. |
| 4 | [#12 일반 사용자 배송 개인정보 조회 범위 제한](https://github.com/logistics-group3/logistics-management/issues/12) | R02 | 조회 정책 합의와 사용자·업체 관계를 읽는 계약이 필요하다. |
| 5 | [#16 주문 처리 전체 시간 예산과 결과 확인 계약 정리](https://github.com/logistics-group3/logistics-management/issues/16) | R06 | 프런트·Gateway·내부 호출·재시도 예산을 함께 검토한다. 결과 조회는 #14와 조율한다. |
| 6 | [#14 지연 생성된 배송의 종료 보장과 복구 경로 추가](https://github.com/logistics-group3/logistics-management/issues/14) | R04 | order·delivery의 영속 상태와 내부 API·운영 복구 절차를 함께 설계해야 한다. |

GitHub 연결 앱의 create_issue는 403 Resource not accessible by integration으로 실패했다. 원격 생성이 되지 않은 것을 확인하고 로그인된 gh로 등록했다. 등록 후 6개 모두 OPEN이며 제목·본문·URL이 작성본과 같은지 확인했다. 담당자 지정·멘션·코드 푸시는 수행하지 않았다.

## #13의 문제와 선택할 정책

현재 D-03·D-05는 “MASTER이거나 저장된 담당자 ID와 요청자 ID가 같으면 허용”한다. 배송담당자 지정이 해제돼 현재 UserContext의 deliveryManager가 false여도 기존 ID가 남아 있으면 허용된다.

제안하는 조건은 다음과 같다.

> MASTER이거나, 현재 배송담당자이면서 해당 구간의 담당자인 사용자만 변경할 수 있다.

| 요청자 | 현재 배송담당자 | 해당 구간 담당자 | 결과 |
|---|---|---|---|
| MASTER | true 또는 false | 본인 또는 타인 | 기존 상태 규칙에 따라 허용 |
| USER | true | 본인 | 기존 상태 규칙에 따라 허용 |
| USER | true | 타인 | 403 FORBIDDEN |
| USER | false | 본인 | 403 FORBIDDEN |
| USER | false | 타인 | 403 FORBIDDEN |

D-03의 “해당 구간”은 업체 배송인 마지막 구간이다. D-05는 요청한 구간이다. 현재 배송담당자 여부와 그 배송의 담당자 여부를 모두 확인해야 한다.

## 백엔드 변경

### DeliveryService.java

파일: `delivery-service/src/main/java/com/sparta/logistics/delivery/application/DeliveryService.java`.

`updateRecipient`의 validateSelfOrMaster와 `changeRouteStatus`의 isSelfOrMaster 검사를 같은 private 검증 메서드로 바꾼다. UserContext의 deliveryManager는 Gateway가 재확인한 사용자 정보에서 전달되므로 추가 user-service 호출 없이 사용할 수 있다.

```java
private void validateAssignedManager(UserContext user, UUID managerId) {
    if (!user.isMaster() && (!user.deliveryManager() || !user.isSelf(managerId))) {
        throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
}
```

- D-03: 배송 조회 → 마지막 구간 managerId로 검증 → 기존 Entity 수정.
- D-05: 배송·요청 구간 조회 → 해당 managerId로 검증 → 기존 Entity 상태 전이.
- 거절은 Entity 변경 전에 실행한다. 기존 공통 FORBIDDEN(403)을 사용한다.
- 배송 담당 자격이 필요한 규칙이므로 common의 일반 소유자 검증 메서드는 바꾸지 않고 delivery 서비스 안에 둔다.

### DeliveryServiceTest.java

파일: `delivery-service/src/test/java/com/sparta/logistics/delivery/application/DeliveryServiceTest.java`.

위 표를 D-03·D-05에 적용한다. 특히 false인데 ID가 같은 요청을 먼저 테스트해 현재 코드에서 실패하는 것을 확인한 뒤 수정한다. 거절 후 수령인·주소·배송·구간 상태가 그대로인지 단언한다. MASTER의 flag=false 허용과 현재 담당자 본인 허용도 확인한다.

### DeliveryIntegrationTest.java

파일: `delivery-service/src/test/java/com/sparta/logistics/delivery/integration/DeliveryIntegrationTest.java`.

실제 PostgreSQL·MockMvc로 D-03·D-05에 검증된 사용자 헤더를 전달한다. false인 기존 담당자에게 403이 반환되고 DB 값이 그대로인 것을 확인한다. 현재 담당자·MASTER의 정상 요청도 확인한다. 이 테스트는 서비스의 헤더 이후 인가·저장 검증이며 실제 Gateway JWT 경로와 구분한다.

## 프런트 변경

파일: `frontend/src/shared/delivery-rules.ts`.

nextRouteAction의 현재 조건은 담당자 ID만 일치하면 버튼을 허용한다. 다음 조건으로 맞춘다.

```typescript
if (
  user.role !== 'MASTER' &&
  (!user.isDeliveryManager || route.managerId !== user.userId)
) return null
```

사용자 정보에 해제가 반영된 뒤 출발·도착 버튼을 숨긴다. 화면의 사용자 정보가 오래된 경우에도 최종 권한은 API가 판단하며 기존 403 오류 표시를 사용한다.

파일: `frontend/src/shared/contracts.test.ts`.

isDeliveryManager=false인데 managerId가 같은 사용자의 nextRouteAction이 null인지 확인한다. 현재 담당자와 MASTER, 앞 구간 미도착·완료·취소의 기존 조건을 함께 검증한다.

기존 테스트의 user 객체에는 isDeliveryManager가 생략돼 있으므로 현재 담당자 fixture는 true를 명시한다. 해제된 담당자 fixture는 false를 명시해 두 상태를 각각 검사한다.

## 인계와 캐시 반영 범위

해제된 사용자에게 자동 재배정하거나 user-service가 delivery를 동기 호출하게 만드는 방식은 이번 작은 수정에 포함하지 않는다. 남은 WAITING·MOVING 구간은 MASTER가 기존 D-04로 현재 배송담당자에게 수동 재배정한다. 이 인계 절차와 책임을 서비스 계약에 적는다.

Gateway의 사용자 재확인 캐시는 팀 결정인 60초다. 따라서 해제 직후 캐시가 true인 동안 최대 60초의 반영 지연이 있다. 이번 변경의 보장은 “false가 전달되면 기존 담당 ID가 남아 있어도 변경을 거절한다”이며 즉시 권한 폐기를 보장하지 않는다.

일반 USER 전체 배송 조회는 별도 #12에서 처리한다. 이번 #13을 완료해도 개인정보 조회 범위 문제가 해결됐다고 표시하지 않는다. 해제 자체를 거절할지·자동 인계를 할지·즉시 차단할지는 추가 정책으로 구분한다.

## 문서와 검증

사용자가 이 계획을 확인한 뒤 구현 시 SA 6.2·6.8·8.4, delivery 서비스 README와 DELIVERY-007, delivery 상세 API·도메인 문서의 해당 권한 설명을 같은 정책으로 맞춘다. 채택한 범위와 남은 정책을 구분하고 구현 노트·progress를 갱신한다. 사용자 검토가 팀 전체 승인이나 병합 완료를 뜻하지는 않는다.

검증 순서는 다음과 같다.

1. 새 권한 회귀 테스트가 현재 코드에서 실패하는지 확인한다.
2. 검증 메서드·UI 조건을 적용하고 해당 테스트를 통과시킨다.
3. `./gradlew build`를 실행해 백엔드 회귀를 확인한다.
4. `npm --prefix frontend run build`, `run lint`, `run format:check`, `run test`를 실행한다. lockfile 변경 없이 의존성이 준비돼 있으면 ci는 반복하지 않는다.
5. 전용 logistics-ui-test 환경에서 Slack을 끄고 담당자 지정 해제 전후·MASTER 재배정을 확인한다. 해제 반영은 실제 Gateway 캐시 만료 후 확인하며 기존 개발 데이터는 사용하지 않는다. 실제 테스트 환경을 확보하지 못하면 API 단위 결과와 E2E 미검증을 구분해 보고한다.
6. `script/docs-check.sh`, `script/error-codes-check.sh`, `git diff --check`를 실행한다.

각 항목의 코드·테스트·SA 변경은 사용자 확인 뒤 실행한다. #13은 2026-10-19 사용자 확인을 받았다. 커밋·푸시·PR·병합·이슈 종료는 구현 검증과 별도로 처리한다.
