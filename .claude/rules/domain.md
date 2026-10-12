---
paths:
  - "backend/**/domain/**"
---
# Domain 규칙 (Entity · enum · Repository 인터페이스 · ErrorCode)

## Entity (`domain/model`)
```java
@Entity
@Table(name = "p_hubs")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hub extends BaseEntity {

    @Id
    @UuidGenerator
    private UUID hubId;

    @Column(nullable = false, length = 100)
    private String name;

    /** 허브 관리자 (user-service p_users.user_id 논리 참조, FK 없음) */
    @Column(nullable = false)
    private UUID managerId;

    public static Hub create(String name, String address, UUID managerId) { ... }

    public void update(String name, String address) { ... }
}
```
- `BaseEntity` 상속 + `@SQLRestriction("deleted_at IS NULL")` 필수
- `@Getter`만. `@Setter`·public 생성자 금지 → 정적 팩토리(`create`, 의미 있는 이름 `signup`)와 의도가 드러나는 변경 메서드(`changeStatus`, `decreaseStock`)
- 불변식 검증은 Entity 메서드 안에서 `BusinessException`으로 (예: 재고 음수 금지)
- PK: `UUID` + `@UuidGenerator`, 필드명 `{엔티티}Id`. 저장 전 ID가 필요하면(주문 Saga) 팩토리에서 `UUID.randomUUID()` 직접 할당
- enum: `@Enumerated(EnumType.STRING)` + `@Column(length = 20)`
- `@Column`의 `nullable`, `length`는 DDL과 동일하게 (`ddl-auto: validate`)
- boolean 컬럼 `is_xxx` → 필드 `xxx` + `@Column(name = "is_xxx")`
- 연관관계: **같은 서비스 안에서만** `@ManyToOne(fetch = LAZY)`. 다른 서비스 리소스는 `UUID` 필드만
- `@OneToMany`는 꼭 필요할 때만, `cascade`·`orphanRemoval`은 aggregate 내부(주문-주문상품)에 한정
- 수량은 `int`/`long`, 금액은 `BigDecimal` (DB `NUMERIC`)

## enum (`domain/model`)
- 상태값은 enum. DB는 VARCHAR + CHECK (`erd.md`)
- 상태 전이 규칙이 있으면 enum 메서드로: `boolean canTransitTo(DeliveryStatus next)`

## Repository 인터페이스 (`domain/repository`)
- 순수 Java 인터페이스. Spring Data 타입(`JpaRepository`, `Specification`)을 노출하지 않는다 (`Page`, `Pageable`은 허용)
- 필요한 메서드만 선언: `save`, `findById`, `search(condition, pageable)`, `existsBy...`
- 삭제 포함 조회가 필요하면 이름에 드러낸다: `existsByUsernameIncludingDeleted`

## ErrorCode (`domain/exception`)
```java
@Getter
@RequiredArgsConstructor
public enum HubErrorCode implements ErrorCode {
    HUB_NOT_FOUND(HttpStatus.NOT_FOUND, "허브를 찾을 수 없습니다."),
    STOCK_INSUFFICIENT(HttpStatus.CONFLICT, "허브 재고가 부족합니다.");

    private final HttpStatus status;
    private final String message;
}
```
- 상태 코드: 없음 404, 중복·상태 충돌 409, 규칙 위반 400, 권한 403
- 다른 서비스가 분기에 쓰는 코드는 이름을 바꾸지 않는다 (바꾸면 `04-api.md` + 호출자 담당에게 공유)

## 문서
Entity·컬럼을 바꾸면 `doc/sql/{NN}-{service}.sql` + `doc/design/03-erd.md`.
