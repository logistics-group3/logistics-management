---
paths:
  - "backend/**/infrastructure/persistence/**"
---
# Repository 구현 규칙 (JPA · QueryDSL)

## 구조
```
domain/repository/HubRepository.java            ← application이 쓰는 인터페이스 (public)
infrastructure/persistence/HubJpaRepository.java ← Spring Data (package-private)
infrastructure/persistence/HubRepositoryImpl.java← @Repository, 위 둘을 연결 (package-private)
infrastructure/persistence/HubQueryRepository.java← QueryDSL 검색 (package-private, 필요할 때)
```
- `{X}JpaRepository`, `{X}RepositoryImpl`, `{X}QueryRepository`는 **package-private** (`interface`/`class` 앞에 public 없음) → 다른 계층에서 직접 못 쓰게
- `{X}RepositoryImpl`은 위임만. 비즈니스 분기 금지

## 논리 삭제
- `@SQLRestriction`은 JPQL·QueryDSL·`findById`에 자동 적용된다
- **native query에는 적용되지 않는다** → `WHERE deleted_at IS NULL`을 직접 쓴다 (삭제 포함 조회가 목적이면 메서드 이름에 `IncludingDeleted`)
- 물리 삭제(`delete`, `deleteById`) 금지

## 검색 (QueryDSL)
```java
@Repository
@RequiredArgsConstructor
class HubQueryRepository {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "name");
    private final JPAQueryFactory queryFactory;

    Page<Hub> search(HubSearchCondition cond, Pageable pageable) {
        List<Hub> content = queryFactory.selectFrom(hub)
                .where(nameContains(cond.name()), regionEq(cond.region()))
                .orderBy(QuerydslSorts.toOrderSpecifiers(pageable.getSort(), hub, SORTABLE))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        JPAQuery<Long> count = queryFactory.select(hub.count()).from(hub)
                .where(nameContains(cond.name()), regionEq(cond.region()));
        return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
    }

    private BooleanExpression nameContains(String name) {
        return StringUtils.hasText(name) ? hub.name.containsIgnoreCase(name) : null;
    }
}
```
- 조건 메서드는 값이 없으면 `null` 반환 (where에서 무시됨)
- 정렬은 `QuerydslSorts` + API별 `SORTABLE` 허용 필드만 (임의 컬럼 정렬 방지)
- count 쿼리는 분리하고 `PageableExecutionUtils`로 필요할 때만 실행
- 목록 조회에서 연관 엔티티를 쓰면 fetch join 또는 `default_batch_fetch_size`(100, 공통 설정)로 N+1 방지. 컬렉션 fetch join + 페이징 금지

## 동시성
- 재고 차감처럼 같은 행을 동시에 바꾸는 경우: `@Lock(LockModeType.PESSIMISTIC_WRITE)` 조회 메서드(`findByIdForUpdate`) 또는 조건부 UPDATE (`UPDATE ... SET quantity = quantity - :n WHERE ... AND quantity >= :n`) 중 하나를 쓰고 주석에 이유를 남긴다
- 락 조회는 반드시 `@Transactional` 안에서

## 금지
- Repository에서 `BusinessException` 던지기 (→ application에서 `Optional` 처리)
- 다른 서비스 DB 테이블 조회 (접속 정보 자체가 없다, Feign 사용)
