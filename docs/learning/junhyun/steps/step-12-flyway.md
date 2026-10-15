# 스텝 12. Flyway로 DB 스키마 관리

- 날짜: 2026-10-14
- 결정 기록: [ADR 0007](../../../decisions/0007-flyway-schema-migration.md)
- 결과: 테스트 65개 통과(통합 테스트 2개 추가), 빈 상태 e2e PASS 14/0. 컨테이너에서 `user_schema`에 `flyway_schema_history`, `p_users`, CHECK 3개·유니크 제약 생성 확인

## 바꾼 것
| 파일 | 내용 |
|---|---|
| 루트 `build.gradle` | JPA 서비스 5개에 `flyway-core`, `flyway-database-postgresql` |
| `config-repo/application.yml` | `ddl-auto: update` → `validate`, `spring.flyway.create-schemas: false` |
| `config-repo/{서비스}.yml` | `spring.flyway.schemas: {서비스}_schema` |
| `user-service/.../db/migration/V1__create_p_users.sql` | SA 5.2 p_users: CHECK(role, affiliation_type, 배송담당자 hub_id 필수), DEFAULT('USER', FALSE), 유니크, `hub_id` 부분 인덱스 |
| 다른 서비스 4개 | 빈 `db/migration/` 폴더. 담당자가 V1을 추가한다 |
| `PostgresIntegrationTest` | `ddl-auto=create-drop` → `validate`. 테스트도 Flyway로 스키마를 만든다 |

## 확인한 것
- 통합 테스트: Flyway가 V1을 적용하고(`Successfully applied 1 migration`), Hibernate validate가 통과했다. 소속 허브 없이 배송담당자로 바꾸면 DB가 `ck_p_users_delivery_manager_hub`로 거부하고, 허브를 넣으면 통과한다. `flyway_schema_history`에 V1 성공 기록이 남는다.
- 컨테이너: user-service가 `user_app` 계정으로 `user_schema`에 이력 테이블을 만들고 V1을 적용했다. 마이그레이션이 없는 hub-service 등은 `No migrations found` 경고만 남기고 정상 기동했다.

## 리뷰 포인트
1. **스키마 이름을 SQL에 쓰지 않는 이유**: 같은 V1이 운영에서는 `user_schema`에, Testcontainers에서는 기본 스키마(public)에 적용된다. 어느 스키마인지는 설정(`spring.flyway.schemas`)이 정한다.
2. **`create-schemas: false`**: 서비스 계정은 자기 스키마 안에서만 CREATE 권한이 있다(init.sql). 스키마 생성은 DB 관리자 몫으로 남긴다.
3. **validate의 범위**: Hibernate validate는 테이블과 컬럼의 존재와 타입을 본다. CHECK 제약이나 인덱스는 보지 않는다. 그래서 제약은 통합 테스트로 따로 확인한다.
4. **Entity의 `unique = true`는 남겨 둔다**: 스키마는 Flyway가 만들지만, Entity만 읽어도 제약을 알 수 있게 문서 역할로 둔다.

## 팀원에게 알릴 것
- 테이블을 추가·변경할 때는 Entity만 고치지 말고 `V{다음 번호}__{설명}.sql`을 함께 추가한다. 하나만 고치면 validate가 기동을 막는다.
- 이미 커밋한 마이그레이션 파일은 고치지 않는다(체크섬 불일치로 기동 실패). 새 파일로 고친다.
- 기존 로컬 DB를 쓰고 있었다면 처음 한 번 `docker compose down -v`.
- 같은 서비스에서 두 사람이 동시에 V2를 만들면 충돌한다. 번호는 브랜치를 합칠 때 확인한다.
