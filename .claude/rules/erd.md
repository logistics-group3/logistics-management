---
paths:
  - "doc/sql/**"
  - "backend/**/domain/model/**"
---
# DDL · ERD 규칙

원본 형식: `doc/sql/01-user.sql`. Entity와 DDL은 항상 함께 바꾼다 (`ddl-auto: validate` — 다르면 기동 실패).

## 파일
- `doc/sql/{NN}-{service}.sql` 하나에 그 서비스 DB의 테이블 전부. 다른 서비스 테이블 금지
- 머리 주석: 파일 역할, SA 절 번호, 실행 시점, 반영 방법 (`script/run.sh --profile domain --reset`)
- 순서: 참조되는 테이블 → 참조하는 테이블. 테이블마다 `CREATE TABLE` → `COMMENT` → `CREATE INDEX`
- DB·계정 생성문(`CREATE DATABASE`, `CREATE USER`)은 쓰지 않는다 (compose 환경 변수가 만든다)

## 테이블 · 컬럼
| 대상 | 규칙 | 예 |
|---|---|---|
| 테이블 | `p_` + 복수 snake_case | `p_hubs`, `p_hub_routes`, `p_order_items` |
| PK | `{단수}_id UUID` | `hub_id` |
| 다른 테이블 참조 | `{대상 단수}_id UUID` | `manager_id`, `source_hub_id` |
| boolean | `is_` 접두사, `NOT NULL DEFAULT FALSE` | `is_delivery_manager` |
| enum | `VARCHAR(20)` + CHECK | `status VARCHAR(20) NOT NULL` |
| 문자열 | `VARCHAR(n)` — n은 SA 명세, Entity `length`와 동일 | |
| 시각 | `TIMESTAMP` (타임존 없음, Asia/Seoul) | |
| 수량 / 금액 / 좌표 | `INTEGER`·`BIGINT` / `NUMERIC(15,2)` / `NUMERIC(10,7)` | |
| 거리·시간 | 단위를 컬럼명에 | `distance_km`, `duration_min` |

컬럼 순서: PK → 업무 컬럼 → 참조 컬럼 → **Audit 6개** (`created_at, created_by, updated_at, updated_by, deleted_at, deleted_by` — `01-user.sql` 그대로 복사).

## 제약 · 인덱스 이름
| 종류 | 이름 | 비고 |
|---|---|---|
| PK | `pk_{테이블(p_ 제외)}` | `pk_hubs` |
| UNIQUE | `uk_{테이블}_{컬럼}` | 논리 삭제 고려 → **부분 유니크 인덱스** `CREATE UNIQUE INDEX uk_hubs_name ON p_hubs (name) WHERE deleted_at IS NULL;` |
| CHECK | `ck_{테이블}_{컬럼}` | enum 값 목록, 수량 `>= 0` |
| FK | `fk_{테이블}_{대상}` | **같은 서비스 DB 안에서만**. 서비스 간 참조는 FK 없이 인덱스만 |
| INDEX | `idx_{테이블}_{컬럼}` | 조회 조건 컬럼, 서비스 간 참조 컬럼. 대부분 `WHERE deleted_at IS NULL` 부분 인덱스 |

## 주석
- `COMMENT ON TABLE` 필수, 의미가 이름으로 안 드러나는 컬럼은 `COMMENT ON COLUMN` (단위, 논리 참조 대상, 규칙)
- 서비스 간 논리 참조 컬럼: `'user-service p_users.user_id (FK 없음)'`

## 초기 데이터
- 허브 17개·경로 같은 기준 데이터는 DDL 파일 끝 `INSERT` 또는 별도 `{NN}-{service}-data.sql` (compose 마운트 추가). Audit `created_by`는 `'SYSTEM'`

## 문서
DDL을 바꾸면 `doc/design/03-erd.md`의 해당 DB 절(mermaid + 컬럼 표 + 인덱스)도 함께.
