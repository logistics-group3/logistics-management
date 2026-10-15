---
paths:
  - "*-service/src/main/java/**/domain/**"
---
# domain 규칙 (Entity, enum, Repository 인터페이스, ErrorCode)

적용 경로: `*-service/src/main/java/**/domain/**`

## Entity
```java
@Getter
@Entity
@Table(name = "p_hubs")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hub extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "hub_id")
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    /** 허브 관리자. user-service p_users.user_id 논리 참조(FK 없음) */
    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    public static Hub create(String name, UUID managerId) { ... }
    public void update(String name) { ... }
}
```
- `BaseEntity` 상속과 `@SQLRestriction("deleted_at IS NULL")`은 필수다. Entity마다 붙인다(상속되지 않는다).
- `@Getter`만 쓴다. `@Setter`와 public 생성자 대신 정적 팩토리(`create`, `signup`)와 의도가 드러나는 변경 메서드(`changeStatus`, `assignManager`)를 둔다.
- 불변식은 Entity 메서드 안에서 검증하고 `BusinessException`을 던진다.
- PK는 `UUID`다. 저장 전에 ID가 필요하면(주문 Saga의 orderId) 팩토리에서 `UUID.randomUUID()`를 직접 넣는다.
- enum은 `@Enumerated(EnumType.STRING)` + `@Column(length = 20)`이다.
- `@Column`의 `nullable`, `length`는 마이그레이션 SQL과 같게 둔다(`ddl-auto: validate`).
- boolean 컬럼 `is_xxx`는 필드 `xxx` + `@Column(name = "is_xxx")`다(`User.deliveryManager`).
- 연관관계는 같은 서비스 안에서만 `@ManyToOne(fetch = LAZY)`로 둔다. 다른 서비스 리소스는 `UUID` 필드만 둔다.
- `@OneToMany`는 애그리거트 내부(배송-구간)에만 쓰고 `cascade`, `orphanRemoval`도 그 안에서만 쓴다.
- 금액은 `BigDecimal`(DB `NUMERIC`)이다.

## enum
- 상태값은 enum이고 DB는 VARCHAR + CHECK다([migration.md](migration.md)).
- 상태 전이 규칙이 있으면 enum이나 Entity 메서드로 둔다. 위반은 `CommonErrorCode.INVALID_STATUS_TRANSITION`이다.

## Repository 인터페이스
- 순수 Java 인터페이스로 두고 필요한 메서드만 선언한다: `saveAndFlush`, `findById`, `search(condition, pageable)`, `existsBy…`. Spring Data 타입(`JpaRepository`, `Specification`)을 노출하지 않는다(`Page`, `Pageable`은 된다).
- 논리 삭제된 행까지 보는 메서드는 이름에 드러낸다: `existsByUsernameIncludingDeleted`.

## ErrorCode
```java
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum HubErrorCode implements ErrorCode {

    HUB_NOT_FOUND(HttpStatus.NOT_FOUND, "허브를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String code() {
        return name();
    }
}
```
- 상태 코드: 없음 404, 중복·상태 충돌 409, 규칙 위반 400, 권한 403.
- 이름 규칙과 목록 갱신은 `docs/error-codes.md`를 따른다. 다른 서비스가 분기에 쓰는 코드는 이름을 바꾸지 않는다.
