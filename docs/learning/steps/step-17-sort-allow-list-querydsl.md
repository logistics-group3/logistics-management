# 스텝 17. 정렬 허용 목록과 QueryDSL 도우미

- 날짜: 2026-10-14
- 근거: Notion "팀 합의 대조·추가 검토" 3장 "다 쓸 테니 common이 괜찮다". 원본은 seungkeun 브랜치 `QuerydslSupport`, 근거는 seungkeun ADR-15
- 결과: common 테스트 48개 통과(새 테스트 7개: QuerydslSupport 3, SortAllowList 2, 예외 처리 1, PageResponse 1)

## 바꾼 것
| 파일 | 내용 |
|---|---|
| `common/.../querydsl/QuerydslSupport` | `notDeleted(deletedAt)`, `toOrderSpecifiers(sort, 허용 목록 Map)` |
| `common/.../page/SortAllowList` | Spring Data 리포지토리에 Pageable을 넘기기 전 `check(pageable, 허용 필드 Set)` |
| `common/.../exception/InvalidSortException` | 허용 목록 밖 정렬 필드 |
| `GlobalExceptionHandler` | `InvalidSortException`을 400 `VALIDATION_ERROR`, `errors[0].field = "sort"`로 응답. Entity에 없는 필드(`PropertyReferenceException`)와 같은 형식 |
| `PageResponse` | `from(page, mapper)` 추가 |

## seungkeun 원본과 다른 점
| 항목 | 원본 | 통합 |
|---|---|---|
| 정렬 입력 | `SearchPageRequest`(직접 만든 요청 객체)의 `toPageable(허용 목록)` | 기존 `SearchPageableArgumentResolver`가 만든 `Pageable`의 `getSort()`를 그대로 받는다 |
| 실패 응답 | `INVALID_REQUEST` | `VALIDATION_ERROR` + `errors[sort]`. SA 6.1에서 요청 값 검증 실패는 VALIDATION_ERROR이고, 기존 Entity에 없는 필드 처리와도 맞춘다 |
| Spring Data 경로 | 없음 | `SortAllowList` 추가. QueryDSL을 쓰지 않는 단순 조회에도 허용 목록이 필요하다 |

## 리뷰 포인트
1. **허용 목록이 필요한 이유**: Entity에 있는 필드면 Spring Data와 QueryDSL 모두 정렬해 준다. `sort=password,ASC`처럼 민감한 필드로 정렬하면 값 자체는 안 보여도 순서로 정보를 추측할 수 있다. QueryDSL은 정렬 경로를 문자열로 만드는 방식에서 취약점(CVE-2024-49203)도 보고됐다. 그래서 "어떤 필드로 정렬할 수 있는가"를 API마다 명시한다(SA 6.1).
2. **Map으로 받는 이유**: 필드명 문자열을 Q클래스 경로로 바꾸는 일을 서비스가 직접 정하게 한다. 리플렉션으로 경로를 만들지 않으므로 허용 목록 밖의 경로가 생길 수 없다.

## 사용 예
```java
private static final Map<String, ComparableExpressionBase<?>> SORTS = Map.of(
        "createdAt", hub.createdAt, "name", hub.name);

List<Hub> content = queryFactory.selectFrom(hub)
        .where(notDeleted(hub.deletedAt), nameContains(keyword))
        .orderBy(toOrderSpecifiers(pageable.getSort(), SORTS))
        .offset(pageable.getOffset()).limit(pageable.getPageSize())
        .fetch();
```
