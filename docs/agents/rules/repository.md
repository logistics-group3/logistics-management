---
paths:
  - "*-service/src/main/java/**/infrastructure/persistence/**"
---
# persistence 규칙 (Spring Data, QueryDSL)

적용 경로: `*-service/src/main/java/**/infrastructure/persistence/**`

## 구조
```
domain/HubRepository.java                            ← application이 쓰는 인터페이스
infrastructure/persistence/HubJpaRepository.java     ← JpaRepository<Hub, UUID>, HubRepository를 함께 상속
infrastructure/persistence/HubQueryRepository.java   ← QueryDSL 검색(필요할 때)
```
- Spring Data 인터페이스가 domain 인터페이스를 상속해 구현을 대신한다(`UserJpaRepository extends JpaRepository<User, UUID>, UserRepository`). 연결용 구현 클래스를 따로 만들지 않는다.
- QueryDSL 검색은 별도 클래스로 두고 domain 인터페이스의 `search` 구현에서 쓴다.

## 논리 삭제
- `@SQLRestriction`은 JPQL, QueryDSL, `findById`에 자동으로 적용된다.
- 네이티브 쿼리에는 적용되지 않는다. `WHERE deleted_at IS NULL`을 직접 쓰고, 삭제된 행까지 보는 것이 목적이면 메서드 이름에 `IncludingDeleted`를 붙인다.
- 물리 삭제(`delete`, `deleteById`)를 쓰지 않는다.

## 검색 (QueryDSL)
```java
private static final Map<String, ComparableExpressionBase<?>> SORTS = Map.of(
        "createdAt", hub.createdAt, "name", hub.name);

public Page<Hub> search(HubSearchCondition condition, Pageable pageable) {
    List<Hub> content = queryFactory.selectFrom(hub)
            .where(nameContains(condition.name()))
            .orderBy(QuerydslSupport.toOrderSpecifiers(pageable.getSort(), SORTS))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
    JPAQuery<Long> count = queryFactory.select(hub.count()).from(hub).where(nameContains(condition.name()));
    return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
}

private BooleanExpression nameContains(String name) {
    return StringUtils.hasText(name) ? hub.name.containsIgnoreCase(name) : null;
}
```
- 조건 메서드는 값이 없으면 `null`을 돌려준다(where에서 무시된다).
- 정렬은 API별 허용 목록으로만 한다(`QuerydslSupport.toOrderSpecifiers`). Spring Data에 `Pageable`을 그대로 넘길 때는 `SortAllowList.check(pageable, Set.of(...))`를 먼저 부른다. 허용 목록 밖이면 400 `VALIDATION_ERROR`다.
- count 쿼리는 분리하고 `PageableExecutionUtils`로 필요할 때만 실행한다.
- 연관 엔티티를 쓰는 목록은 fetch join이나 batch fetch로 N+1을 막는다. 컬렉션 fetch join과 페이징을 함께 쓰지 않는다.

## 동시성
- 같은 행을 동시에 바꾸는 경우 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 조회나 조건부 UPDATE 중 하나를 쓰고 `Note:` 주석에 이유를 남긴다. 락 조회는 트랜잭션 안에서만 한다.

## 하지 않는 것
- Repository에서 `BusinessException` 던지기(→ application에서 `Optional`을 처리한다)
- 다른 서비스 테이블 조회(접속 권한도 없다. Feign을 쓴다)
