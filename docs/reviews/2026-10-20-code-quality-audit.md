# 코드 품질 감사 — 2026-10-20

디렉토리와 백엔드 계층 분리는 대체로 적절하다. 우선 고칠 문제는 구조 이름보다 동시 수정에 의한 데이터 복원·덮어쓰기와 프런트의 응답 유실 처리다.
감사 당시 운영 코드는 수정하지 않았다. 이후 6번은 오탐으로 철회했고 나머지 7개는 모두 수정했다(각 항목 참고). 아래 수정안은 당시 제안이며 팀 설계를 변경한 결정이 아니다.

## 범위와 기준

이 보고서는 `5036505`와 당시 작업 트리를 검사한 기록이다. 이후 병합된 수정이나 UI 개편을 반영한 최신 dev의 결함 현존 여부는 이 문서 정리에서 재검증하지 않았다.

- 기준 브랜치: `feat/order-creation-recovery`, HEAD `5036505`. 기존 문서 변경과 IDE 설정·`bin/` 산출물을 보존했다.
- 운영 Java 344개·11,215줄의 패키지와 import 방향을 검사했다. 프런트 TypeScript·TSX는 테스트 3개를 포함해 37개·5,077줄이다.
- 전수 정적 검색에 더해 주문 생성·취소·삭제·복구, 업체·상품 변경, 배송 상태 전이, Gateway 인증, 공통 오류·사용자 처리와 프런트 등록·조회를 추적했다. 모든 소스와 모든 분기를 정독했다는 뜻은 아니다.
- 동시 HTTP 요청과 여러 서비스 인스턴스가 가능한 MSA를 가정했다. 특정 TPS에서의 안전성·성능을 측정한 감사는 아니다.
- 루트·모듈 AGENTS, SA, 코딩 규칙, 계층별 규칙, 리뷰 체크리스트와 관련 ADR을 기준으로 삼았다.
- 로컬 Ponytail 5.2.0의 `ponytail-audit`를 적용했다. 팀의 `lite` 운용 원칙에 따라 필요한 계층·포트는 유지하고, 실제 중복과 사용하지 않는 부분을 구분했다.

## 디렉토리와 계층 판단

| 대상 | 판단 | 근거·개선 방향 |
|---|---|---|
| 모듈 분리 | 적절 | 서비스별 소스·스키마와 공통 모듈을 분리했다. 프런트는 별도 npm 프로젝트다. |
| presentation | 대체로 적절 | Controller는 검증·Command 변환·응답 변환을 맡고, 소유자 검사와 흐름은 서비스로 넘긴다. |
| application | 대체로 적절 | 외부 호출용 인터페이스와 트랜잭션 Writer를 구분한다. 동시 수정 보호의 일관성이 부족한 곳은 1·2번이다. |
| domain | 대체로 적절 | 주문·배송 상태 전이를 Entity 메서드가 관리한다. 운영 Java import 검사에서 domain → application/presentation/infrastructure 역참조는 0건이다. |
| infrastructure | 적절 | JPA·QueryDSL·Feign 구현을 모았다. application → presentation/infrastructure 직접 import는 0건이다. |
| common-core / common | 적절 | WebFlux Gateway와 MVC 공통을 분리했다. 서비스별 업무를 공통 모듈로 올릴 근거는 발견하지 않았다. |
| frontend | 개선 여지 | `CreateOrder.tsx`는 요청 보존·전송·진행 조회·폼 렌더링을 함께 맡는다. `Catalog.tsx`와 `CatalogDetails.tsx`는 업체·상품·허브·사용자를 분기하고 타입 단언으로 연결한다. |

프런트는 새 계층을 일괄 도입하기보다 다음 기능 변경 때 주문 진행 처리와 폼 표시를 분리하고, Catalog의 `kind`와 `item`을 서로 연결된 타입으로 제한하는 편이 작다.
현재 `kind`와 `item`이 독립적인 합집합이라 잘못된 조합도 타입 검사를 통과할 수 있다. 현재 호출에서 그 조합이 발생한 사례는 확인하지 않아 확정 결함으로 세지 않았다.

다음은 위반으로 세지 않았다.

- delivery의 application 조회 포트·QueryDSL projection·Result 직접 응답·조합 DTO의 Jackson 설정은 모듈 AGENTS와 결정 로그 17·18의 합의다.
- 구현체가 하나인 Feign 포트는 서비스 경계와 외부 호출 테스트를 위한 실제 용도가 있다. 일괄 삭제하지 않는다.
- 주문 Saga의 시작 기록·커밋 재확인·보상 실패 기록은 실패 상태가 서로 다르다. 비슷해 보인다는 이유로 범용 Saga 프레임워크로 합치지 않는다.
- O-06 삭제의 트랜잭션 안 I-13 호출은 SA 7.5의 명시적 선택이다. 일반 규칙만으로 위반 판정하지 않았다.
- `.project`, `.settings`, `bin/`은 로컬 미추적 설정·산출물이다. 버전 관리 중인 운영 코드 구조의 결함과 구분했다.

## 우선 수정 — 재현한 동작 문제

### 1. [P1] 상품 수정이 업체 삭제로 지운 상품을 다시 활성화한다

- 처리(2026-10-21, `feat/company-write-lock`): P-04·P-05가 상품을 읽기 전에 `findByIdForUpdate`로 업체 행을 잠근다. 업체가 이미 삭제됐으면 `PRODUCT_NOT_FOUND`다.

- 위치: [ProductService.java:38](../../company-service/src/main/java/com/sparta/logistics/company/application/ProductService.java#L38), [CompanyService.java:73](../../company-service/src/main/java/com/sparta/logistics/company/application/CompanyService.java#L73).
- 역할: P-04는 상품을 읽어 변경하고, C-05는 업체와 상품을 함께 논리 삭제한다.
- 문제: P-04가 읽은 뒤 C-05가 삭제·커밋하고 P-04가 나중에 커밋하면, 오래된 `deletedAt=null`을 포함한 상품 UPDATE가 삭제를 되돌린다. PostgreSQL 16에서 삭제 직후 `true`였던 삭제 여부가 늦은 수정 뒤 `false`가 됐다.
- 최소 수정: P-04·P-05도 P-01·C-05처럼 같은 업체 행을 먼저 잠그고 권한 검사·상품 조회·변경을 수행한다. 새 공통 추상화 없이 기존 `findByIdForUpdate` 잠금 규칙을 모든 상품 쓰기에 적용한다.
- 방치 시: 삭제된 업체 밑에 활성 상품이 남는다. 논리 삭제 조회 조건만으로 쓰기 경쟁을 막을 수 없다.

### 2. [P1] 업체명 수정이 완료된 관리자 이전을 되돌린다

- 처리(2026-10-21, `feat/company-write-lock`): C-04의 트랜잭션 안 재조회를 `findByIdForUpdate`로 바꿔 잠근 최신 행으로 권한을 검사한다.

- 위치: [CompanyService.java:58](../../company-service/src/main/java/com/sparta/logistics/company/application/CompanyService.java#L58).
- 역할: 원격 검증 뒤 짧은 트랜잭션으로 업체 정보를 수정한다.
- 문제: 트랜잭션 안 재조회도 일반 `findById`라서 읽은 직후 다른 트랜잭션이 관리자를 바꿀 수 있다. 오래된 업체명 수정이 커밋되면 전체 컬럼 UPDATE에 이전 `managerId`가 실려 관리자 변경을 덮는다. PostgreSQL에서 새 관리자 유지 여부가 `false`로 재현됐다.
- 최소 수정: update의 트랜잭션 안 조회를 기존 `findByIdForUpdate`로 바꾸고, 잠근 최신 행으로 권한을 검사한다. 원격 허브·사용자 조회는 지금처럼 잠금 밖에 둔다.
- 방치 시: MASTER가 이전한 관리 권한이 원래 사용자에게 돌아갈 수 있다. 같은 패턴은 업체 삭제와 수정의 경쟁도 함께 점검해야 한다.

### 3. [P1] 응답 본문 수신 실패가 업체·상품 중복 등록 방어를 우회한다

- 처리(2026-10-21, `feat/company-write-lock`): 본문 읽기(`response.text()`)를 통신 오류 변환 try 안으로 옮겼다. 본문 수신 실패 단위 테스트(`api.test.ts`)를 추가했다.

- 위치: [api.ts:59](../../frontend/src/shared/api.ts#L59), [CompanyForm.tsx:72](../../frontend/src/features/CompanyForm.tsx#L72), [ProductForm.tsx:78](../../frontend/src/features/ProductForm.tsx#L78).
- 역할: `api()`는 통신 오류를 `ApiError(0)`로 바꾸고, 등록 폼은 이 오류를 받으면 결과 미확정 상태로 잠근다.
- 문제: try/catch가 `fetch()`까지만 감싸고 `response.text()`는 밖에 있다. 서버가 201 헤더를 보낸 뒤 본문 수신이 끊기면 원시 `TypeError`가 올라와 폼의 `uncertain` 조건이 거짓이 된다. 실제 api.ts와 실패하는 표준 Response 스트림으로 이 분기를 확인했다.
- 최소 수정: 본문 읽기 실패까지 같은 통신 오류 변환에 포함한다. 사용자 취소 신호 처리는 유지한다.
- 방치 시: 이미 저장됐을 수 있는 업체·상품을 다시 등록할 수 있다. 두 등록 API에는 주문과 같은 멱등키 방어가 없다.

## 수정 권장 — 중복과 검증 불일치

### 4. [P2] 배송 담당자 이름 조회가 공통 조회를 복제하면서 첫 50명만 읽는다

- 처리(2026-10-21, `feat/company-write-lock`): `DeliverySheet`가 `useUsersDirectory`를 재사용한다. 별도 `route-names` 조회와 50명 제한을 없앴다.

- 위치: [DeliverySheet.tsx:22](../../frontend/src/features/DeliverySheet.tsx#L22), [queries.ts:34](../../frontend/src/shared/queries.ts#L34).
- 역할: MASTER 배송 상세에서 구간 담당자 이름을 보여 준다.
- 문제: 업체 화면은 전체 페이지를 읽는 `useUsersDirectory`를 쓰지만 배송 화면은 별도 캐시 키로 `/users?size=50` 한 페이지만 읽는다. 담당자가 두 번째 페이지에 있으면 실제 사용자가 있어도 이름 대신 짧은 UUID를 표시한다. 이는 호출·분기 추적으로 확인했으며 51명 브라우저 시나리오는 실행하지 않았다.
- 최소 수정: `useUsersDirectory`를 재사용하고 배열에서 담당자를 찾는다. 서버 API나 새 조회 계층을 추가할 필요가 없다.
- 방치 시: 사용자 수와 화면에 따라 같은 담당자의 이름 표시가 달라지고, 사용자 목록 요청·캐시도 중복된다.

### 5. [P2] 같은 I-17 결과를 읽는 두 함수의 검증이 이미 달라졌다

- 처리(2026-10-21, `feat/company-write-lock`): `isCreationClosed`가 검증된 `getCreationState`를 호출한 뒤 상태만 비교한다. `DeliveryClientAdapterTest`를 추가했다.

- 위치: [DeliveryClientAdapter.java:49](../../order-service/src/main/java/com/sparta/logistics/order/infrastructure/client/DeliveryClientAdapter.java#L49), [DeliveryClientAdapter.java:58](../../order-service/src/main/java/com/sparta/logistics/order/infrastructure/client/DeliveryClientAdapter.java#L58).
- 역할: `isCreationClosed`는 정상 생성 실패 보상에서, `getCreationState`는 운영자 복구에서 배송 결과를 읽는다.
- 문제: `CLOSED`인데 활성 `deliveryId`·`status`가 함께 있는 모순 응답을 앞 함수는 `true`로 인정하고 뒤 함수는 거절한다. 같은 응답을 주입한 단위 재현에서 검증 일치 단언이 실패했다. 실제 delivery 서버가 이런 응답을 보냈다는 뜻은 아니다.
- 최소 수정: `isCreationClosed`가 검증된 `getCreationState`를 호출한 뒤 상태만 비교하게 한다.
- 방치 시: 배송 응답 계약이 깨졌을 때 정상 보상과 운영 복구가 서로 다른 안전 기준으로 생성 시도를 종료한다.

## 작은 정리 — 기능 수정 이후 처리

### 6. [P3] ~~외부 호출이 없는 hub에 Feign 활성화 설정이 남아 있다~~ — 오탐, 철회

- 정정(2026-10-21): hub는 company-service를 호출한다. [ProductClient.java](../../hub-service/src/main/java/com/sparta/logistics/hub/infrastructure/client/ProductClient.java)가 `@FeignClient(name = "company-service")`로 I-07·I-08을 호출하고, `ProductClientAdapter`·`CompanyClientAdapter`와 타임아웃 통합 테스트가 이를 쓴다.
- 원인: 감사 당시 "hub는 다른 서비스를 호출하지 않는 계약"이라는 전제로 검색 결과를 해석했다. 전제를 코드로 확인하지 않았다.
- 조치: [FeignConfig.java](../../hub-service/src/main/java/com/sparta/logistics/hub/infrastructure/config/FeignConfig.java)와 hub의 `spring-cloud-starter-openfeign` 선언은 필요하므로 지우지 않는다. 처리 순서의 6번에서도 뺀다.

### 7. [P3] 운영자 복구가 기존 MASTER 검사 함수를 다시 구현했다

- 처리(2026-10-21, `feat/company-write-lock`): 로컬 `requireMaster`를 지우고 `user.validateMaster()`를 호출한다.

- 위치: [OrderCreationRecoveryService.java:89](../../order-service/src/main/java/com/sparta/logistics/order/application/OrderCreationRecoveryService.java#L89), [UserContext.java:32](../../common/src/main/java/com/sparta/logistics/common/auth/UserContext.java#L32).
- 역할: MASTER가 아니면 403을 던진다.
- 문제: 로컬 `requireMaster`는 이미 있는 `user.validateMaster()`와 같은 역할·오류를 다시 정의한다. 복구 메서드 3곳이 이 사본을 호출한다.
- 최소 수정: 기존 헬퍼로 바꾸고 로컬 함수를 지운다. Controller와 application의 권한 방어 자체는 유지한다.
- 방치 시: 공통 권한 정책을 바꿀 때 별도 구현도 찾아야 한다. 현재 권한 우회가 확인된 것은 아니다.

### 8. [P3] 운영 호출이 없는 논리 삭제 헬퍼와 구현을 되풀이하는 테스트가 남아 있다

- 처리(2026-10-21, `feat/company-write-lock`): `notDeleted`와 전용 테스트·미사용 import를 지우고 학습 문서 step-17에 현재 방식을 표시했다.

- 위치: [QuerydslSupport.java:25](../../common/src/main/java/com/sparta/logistics/common/querydsl/QuerydslSupport.java#L25), [QuerydslSupportTest.java:51](../../common/src/test/java/com/sparta/logistics/common/querydsl/QuerydslSupportTest.java#L51).
- 역할: `notDeleted(path)`가 `path.isNull()`을 반환한다.
- 문제: 전체 검색에서 운영 호출은 0건이고, 같은 표현식과 비교하는 테스트 1개 및 학습 예제만 참조한다. 실제 서비스는 Entity의 `@SQLRestriction`을 사용한다.
- 최소 수정: 팀 공통 모듈 리뷰를 거쳐 헬퍼·전용 테스트·미사용 import를 정리한다. 학습 예제는 당시 기록임을 표시하거나 현재 방식으로 연결한다.
- 방치 시: 쓰지 않는 API와 이를 위한 테스트를 유지한다. 실제 논리 삭제 통합 테스트는 삭제하면 안 된다.

## 검증과 증거

| 확인 | 결과 | 해석 |
|---|---|---|
| 최초 `./gradlew build` | 성공, 7초, 58개 작업 UP-TO-DATE | 기존 결과 재사용이며 새 테스트 실행이 아니다. |
| 별도 company 재현 | 3개 중 2개 실패·1개 통과 | 1·2번은 기대한 불변식이 깨져 실패했다. 동률 생성시각 120개·3페이지 조회는 120개 고유 항목으로 통과해 누락 결함으로 보고하지 않았다. |
| 별도 order 재현 | 1개 실패 | 5번의 두 검증 함수가 같은 모순 응답을 다르게 처리했다. |
| 본문 스트림 재현 | 기대 단언 실패, exit 1 | `TypeError`, 등록 폼의 결과 미확정 판정 `false`. 실제 브라우저·서버 실험은 아니다. |
| 기본 `./gradlew build` 재확인 | 성공, 38초, 2개 실행·56개 UP-TO-DATE | company 50개·order 484개 기존 테스트를 새로 실행했다. 별도 재현 테스트는 기본 소스 경로에 포함하지 않았다. |
| 기본 JUnit XML 전체 집계 | 857개, 실패·오류·skip 0 | 위 534개 새 실행과 다른 모듈의 재사용 결과를 합친 수다. |
| 프런트 build·lint·format:check·test | 모두 성공, 단위 테스트 56개 | 기존 단위 테스트 통과가 위 오류 상황을 검증한다는 뜻은 아니다. |
| 브라우저 E2E·실서버·부하 시험 | 미실행 | 새 서비스 스택이나 실제 Slack 발송은 실행하지 않았다. |

[정제 실행 결과와 소스 SHA-256](code-quality/2026-10-20/results.json), [재현 코드·실행 안내](code-quality/2026-10-20/README.md)를 남겼다.
DB 재현은 기존 프로젝트의 PostgreSQL Testcontainers를 썼다. 감사용 테스트는 임시 소스 경로로만 연결했고 기본 서비스·테스트 소스를 바꾸지 않았다.
재현의 대기 지점은 서비스 변경 후 커밋 전이다. 경쟁 트랜잭션의 커밋을 먼저 끝내도록 순서를 제어한 문제 입증용이며, 잠금 수정 뒤 회귀 테스트로 채택할 때는 잠금 대기를 관측하는 방식으로 조정해야 한다.

## 처리 순서와 한계

1. 1·2번의 업체 행 잠금 규칙을 맞춘다. 경쟁 순서를 보장하는 PostgreSQL 회귀 테스트를 함께 넣는다.
2. 3번을 공통 `api()`에서 고치고 본문 수신 실패 단위 테스트를 추가한다.
3. 4·5번은 기존 조회·검증 함수 재사용으로 해결한다.
4. 7·8번은 공통 모듈 영향과 학습 문서를 확인한 뒤 정리한다. 폴더 전면 개편은 우선하지 않는다.

삭제 가능한 규모는 대략 수십 줄이다(6번 철회로 hub 의존성 선언은 제외). 패치하지 않았으므로 순감 줄 수는 확정하지 않았다.
핵심은 Writer·DTO·서비스 포트를 없애는 것이 아니라, 이미 있는 잠금과 공통 기능을 일관되게 사용하는 것이다.
미확인: 모든 코드 분기의 정독, 실제 브라우저 재현, 운영 데이터 규모의 성능·경쟁 빈도, 외부 라이브러리 취약점·최신 버전 조사다.
