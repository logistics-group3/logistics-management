# common 협업 창구

모든 MVC 서비스가 함께 쓰는 `common`과 `common-core`의 계약이다. 바꾸면 모든 서비스에 영향이 가므로, 변경 요청은 아래 협업 로그에 남기고 팀 전체 리뷰를 받는다. 모듈 규칙은 [`common/AGENTS.md`](../../../common/AGENTS.md)에 있다.

## 1. 모듈 정보

| 항목 | 값 |
|---|---|
| 담당자 | 공통 인프라 (변경은 팀 전체 리뷰) |
| 성격 | 라이브러리 모듈. 실행되지 않는다 |
| 쓰는 모듈 | user, hub, company, order, delivery (`common`), gateway (`common-core`만) |

## 2. 제공 기능 (서비스가 의존하는 계약)

| 패키지 | 구성 | 바꾸면 영향 |
|---|---|---|
| `common-core` `response`, `exception` | `ApiResponse`, `ErrorResponse`, `ErrorCode`, `CommonErrorCode`, `BusinessException` | 모든 응답 형식(SA 6.1). gateway도 같은 형식을 쓴다 |
| `common-core` `auth` | `Role`, `UserHeaders`(`X-User-*` 이름) | gateway와 서비스의 헤더 계약 |
| `common` `autoconfigure` | 조건부 자동 설정 6개(ADR 0009). 끄기 `common.<묶음>.enabled=false` | 서비스 기동 시 등록되는 공통 빈 |
| `common` `auth` | `@CurrentUser UserContext`, `@RequireRole`, 소유자 검증(`validateSelfOrMaster` 등) | 인증·인가 |
| `common` `entity` | `BaseEntity`(Audit 6컬럼, `softDelete`) | 모든 테이블의 Audit 컬럼(SA 5.1) |
| `common` `config`, `page`, `querydsl`, `response` | Pageable 규칙(10·30·50, `createdAt,DESC`), `SortAllowList`, `QuerydslSupport`, `PageResponse` | 검색 API |
| `common` `feign` | `FeignHeaderInterceptor`, `FeignErrorDecoder`, `IdempotentRetryConfig` | 서비스 간 호출의 헤더 전달·오류 변환·재시도 |
| `common` testFixtures | `PostgresIntegrationTest` | DB 통합 테스트 |

## 3. 구현 현황

- [x] 위 기능 전체 (common 테스트 48개, common-core 2개)
- [ ] 공통 에러 코드 외 SA 코드는 각 서비스가 정의한다(`docs/error-codes.md`)

## 4. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. ID는 `COMMON-{번호}`다.

아직 기록이 없다.
