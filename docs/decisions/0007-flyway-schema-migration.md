# 0007. DB 스키마를 Flyway 마이그레이션으로 관리

- 상태: 채택
- 날짜: 2026-10-14
- 결정자: junhyun (공통 인프라 개인 구현안. 채택 시 팀 공통 규칙이 된다)

## 맥락
- 지금까지는 Hibernate `ddl-auto: update`가 Entity를 보고 테이블을 만들었다.
- SA 5.1~5.2의 CHECK 제약(배송담당자면 hub_id 필수), DEFAULT 값, 부분 인덱스를 표현하지 못했다(리뷰 1 minor 14).
- 스키마 변경 이력이 없고, 컬럼 삭제·변경은 반영되지 않으며, 팀원마다 DB 모양이 달라질 수 있다.

## 결정
- Flyway(Boot 3.5 관리 버전, `flyway-core` + `flyway-database-postgresql`)를 JPA를 쓰는 서비스 5개에 공통으로 붙인다(루트 build.gradle).
- 마이그레이션은 서비스마다 `src/main/resources/db/migration/V{번호}__{설명}.sql`에 둔다. 테이블 이름에 스키마를 붙이지 않는다.
- 실행 스키마는 config-repo의 `spring.flyway.schemas: {서비스}_schema`. 스키마 자체는 init.sql이 만들고, Flyway는 만들지 않는다(`create-schemas: false`).
- Hibernate는 `ddl-auto: validate`로 바꾼다. Entity와 테이블이 다르면 기동이 실패한다.
- Testcontainers 통합 테스트도 같은 마이그레이션으로 스키마를 만든다.

## 검토한 대안
- ddl-auto 유지: 위 문제가 그대로 남는다.
- Liquibase: 되돌리기와 DB 독립 형식이 장점이지만, SA에 이미 SQL 스키마가 있고 팀이 SQL로 리뷰하기 쉽다.
- Alembic: Python·SQLAlchemy 도구라 Spring Boot 기동 흐름과 JPA 모델에 맞지 않는다.

## 결과
- user-service V1에 SA 5.2의 CHECK 3개, DEFAULT, 유니크 제약, 허브 부분 인덱스를 넣었다. 리뷰 1 minor 14가 해결됐다.
- 이미 적용한 마이그레이션 파일은 고치지 않는다. 바꿀 때는 새 버전 파일을 추가한다.
- 기존 로컬 DB(ddl-auto로 만든 테이블)는 이력이 없어 Flyway가 실패한다. 처음 한 번 `docker compose down -v`가 필요하다.
