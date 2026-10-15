---
paths:
  - "*-service/src/main/resources/db/migration/**"
---
# Flyway 마이그레이션 규칙

적용 경로: `*-service/src/main/resources/db/migration/**`. 기본 규칙은 `docs/agents/coding-conventions.md` 5절과 ADR 0007이다. 예시는 `user-service/.../V1__create_p_users.sql`이다.

## 파일
- `V{번호}__{설명}.sql`이다. 번호는 서비스 안에서 1부터 올린다. 같은 서비스에서 두 사람이 같은 번호를 만들면 병합할 때 하나를 올린다.
- 이미 커밋한 파일은 고치지 않는다(체크섬이 달라 기동이 실패한다). 새 번호 파일로 고친다.
- 테이블 이름에 스키마를 붙이지 않는다. 실행 스키마는 `spring.flyway.schemas`가 정한다.
- Entity와 SQL은 같은 커밋에서 바꾼다. 다르면 `ddl-auto: validate`가 기동을 막는다.
- 첫 줄에 SA 절 번호를 주석으로 쓴다: `-- SA 4.3, 5.3 p_hubs`

## 테이블과 컬럼
| 대상 | 규칙 | 예 |
|---|---|---|
| 테이블 | `p_` + 복수 snake_case | `p_hubs`, `p_hub_routes` |
| PK | `{단수}_id UUID` | `hub_id` |
| 다른 테이블 참조 | `{대상}_id UUID` | `manager_id`, `departure_hub_id` |
| boolean | `is_` 접두사, `NOT NULL DEFAULT FALSE` | `is_delivery_manager` |
| enum | `VARCHAR(20)` + CHECK | `status VARCHAR(20) NOT NULL` |
| 문자열 | `VARCHAR(n)`. n은 SA 명세와 Entity `length`와 같게 | |
| 시각 | `TIMESTAMP` | |
| 금액 | `NUMERIC(15,2)` | |

컬럼 순서는 PK → 업무 컬럼 → 참조 컬럼 → Audit 6개(`created_at, created_by, updated_at, updated_by, deleted_at, deleted_by`, `V1__create_p_users.sql`과 같게)다.

## 제약과 인덱스 이름
| 종류 | 이름 | 예 |
|---|---|---|
| UNIQUE | `uk_{테이블}_{컬럼}` | `uk_p_users_username` |
| CHECK | `ck_{테이블}_{내용}` | `ck_p_users_role`, `ck_p_users_delivery_manager_hub` |
| FK | `fk_{테이블}_{대상}` | 같은 서비스 안에서만. 서비스 간 참조는 FK 없이 인덱스만 둔다 |
| INDEX | `idx_{테이블}_{컬럼}` | `idx_p_users_hub_id` |

- 논리 삭제된 행을 제외해야 하는 유니크는 부분 유니크 인덱스로 만든다: `CREATE UNIQUE INDEX uk_p_hubs_name ON p_hubs (name) WHERE deleted_at IS NULL;` 탈퇴 후 재사용 불가처럼 SA가 정한 경우는 일반 유니크 제약이다.
- 서비스 간 참조 컬럼에는 인덱스를 둔다(SA 5.1). 조회 대상이 활성 행뿐이면 `WHERE deleted_at IS NULL` 부분 인덱스로 만든다.
- CHECK, DEFAULT, 부분 인덱스는 Hibernate validate가 확인하지 않는다. 통합 테스트로 확인한다.

## 초기 데이터
- 허브 17개·경로 같은 기준 데이터는 별도 마이그레이션 파일(`V{n}__seed_hubs.sql`)에 `INSERT`로 넣는다. Audit의 `created_by`, `updated_by`는 `'SYSTEM'`이다.
