# 스텝 22. 허브 조회 API (H-01, H-02, I-02)

- 날짜: 2026-10-15
- 결과: hub-service 테스트 20개 통과(서비스 단위 5, MVC 6, PostgreSQL 통합 9)
- 범위: 허브 목록·검색, 허브 단건, 내부 단건. 경로 조회(H-03, H-04, I-03)와 시드 적재는 다음 단계다.

## 만든 것
| 계층 | 파일 | 내용 |
|---|---|---|
| domain | `HubRepository` | `findById`, `search(keyword, pageable)` 선언 |
| infrastructure | `HubQueryRepository`, `HubQueryRepositoryImpl` | QueryDSL 검색·정렬·페이징. Spring Data가 `Impl` 접미사로 `HubJpaRepository`에 합친다 |
| infrastructure | `HubJpaRepository` | `JpaRepository` + `HubRepository` + `HubQueryRepository` |
| application | `HubQueryService`, `HubResult`, `InternalHubResult` | 클래스 단위 `readOnly = true`, 없으면 `HUB_NOT_FOUND` |
| presentation | `HubController`, `InternalHubController` | 외부는 `ApiResponse`/`PageResponse`, 내부는 DTO 그대로(ADR 0010) |

## 리뷰 포인트
1. **QueryDSL을 고른 이유**: 지금은 `keyword` 하나뿐이라 JPQL로도 되지만, 같은 서비스의 H-03이 선택 조건 두 개(`departureHubId`, `arrivalHubId`)를 받는다. `BooleanExpression`을 조건별로 나누고 null이면 `where`가 무시하는 방식으로 허브와 경로 검색을 같은 형태로 맞춘다. common의 `QuerydslSupport`가 정렬 허용 목록 검사를 이미 제공한다.
2. **정렬 허용 목록**: 사용자가 보낸 정렬 문자열을 경로로 바꿔 `orderBy`에 넣으면 의도하지 않은 필드로 정렬할 수 있다(QueryDSL 정렬 취약점). `Map.of("createdAt", ..., "name", ...)`에 있는 필드만 변환하고 나머지는 400 `VALIDATION_ERROR`다. 통합 테스트에서 `sort=latitude,asc`가 400이 되는 것을 확인했다.
3. **정렬 기준에 PK를 덧붙인 이유**: 시드를 한 트랜잭션에 넣으면 `createdAt`이 같은 행이 많다. 정렬 기준이 같으면 DB는 페이지마다 다른 순서를 낼 수 있어 항목이 겹치거나 빠진다. PK를 마지막 기준으로 두고 `pagesDoNotOverlap` 테스트로 확인했다.
4. **count 쿼리 분리**: 목록과 count를 따로 만들고 `PageableExecutionUtils.getPage`에 count를 지연 실행으로 넘긴다. 결과가 한 페이지보다 적으면 count 쿼리를 생략한다.
5. **논리 삭제 제외**: `@SQLRestriction`이 Entity 단위로 JPQL·QueryDSL에도 적용돼 쿼리마다 조건을 붙이지 않아도 된다. 삭제된 허브가 목록·검색·단건에서 빠지는 것을 통합 테스트로 확인했다.
6. **`%` 검색**: QueryDSL의 `contains`는 `%`, `_`를 이스케이프한다. `keyword=%`가 전체 일치가 아닌 0건이 되는 것을 확인했다.

## 검증하며 알게 된 것
- **문자열 정렬 순서는 DB 콜레이션이 정한다.** 컨테이너 PostgreSQL 16에서 `name,asc`가 `부산 → 서울 → 경기` 순서로 나왔다. 코드 문제가 아니라 DB 정렬 규칙이어서, 테스트는 가나다 순서를 단정하지 않고 오름차순과 내림차순이 서로 역순인지로 검증한다. 운영 DB의 콜레이션이 다르면 결과 순서도 달라질 수 있다. 17개 허브 목록을 가나다 순으로 보여줘야 하는 요구가 생기면 콜레이션을 명시하는 방안을 결정 기록과 함께 검토한다.
- **`deleteAll()`은 논리 삭제 행을 지우지 못한다.** `@SQLRestriction`이 조회에 적용돼 삭제된 행이 남고, 다음 테스트의 같은 이름 INSERT가 유니크 제약에 걸렸다. 테스트 초기화는 `DELETE FROM p_hubs` SQL로 한다.
- 잘못된 UUID 경로 변수(`/api/v1/hubs/not-a-uuid`)는 공통 예외 처리로 400 `VALIDATION_ERROR`가 된다.

## 검증하지 못한 것
- 허브 17건 시드가 없어 실제 기동 후 호출 결과는 비어 있다. 실행 검증은 시드 적재 후로 남겼다.
- Gateway 경유 호출, Swagger 확인, `/internal/**` 차단은 확인하지 않았다.
- 허브명·주소 검색의 인덱스 사용 여부(`LIKE '%키워드%'`는 일반 인덱스를 타지 않는다)는 데이터가 17건이라 측정하지 않았다.

## 면접 예상 질문
- Spring Data JPA 메서드 이름 쿼리, JPQL, QueryDSL은 각각 언제 고르는가?
- 사용자 입력 정렬 필드를 그대로 `orderBy`에 넣으면 왜 위험한가?
- 페이징에서 정렬 기준이 유일하지 않으면 어떤 문제가 생기는가?
- count 쿼리를 분리하는 이유는 무엇인가?
