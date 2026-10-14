---
title: Flyway와 JaCoCo는 무엇이고 이 프로젝트에 왜 필요한가
date: 2026-10-14
updated: 2026-10-14
asker: Junhyun An
level: 중급
tags: [flyway, database, migration, jacoco, test-coverage, gradle]
---

# Flyway와 JaCoCo는 무엇이고 이 프로젝트에 왜 필요한가

## 질문
Flyway와 JaCoCo가 무엇인가.
(추가 질문) Alembic을 많이 들어 봤는데 Spring에서도 쓸 수 있나. 쓸 수 있다면 Flyway와 비교하면 어떤가.

## 짧은 답
- **Flyway**: DB 스키마 변경을 **버전이 붙은 SQL 파일**로 관리하는 도구다. 앱이 뜰 때 아직 실행하지 않은 파일만 순서대로 실행하고, 실행 기록을 DB에 남긴다. "DB 스키마용 Git"에 가깝다.
- **JaCoCo**: 테스트가 실행되는 동안 **어떤 코드 줄과 분기가 실제로 실행됐는지** 기록해 커버리지 리포트를 만드는 도구다.
- 지금 프로젝트는 테이블을 Hibernate가 Entity를 보고 자동으로 만들고(`ddl-auto: update`), 테스트가 코드를 얼마나 덮는지 숫자가 없다. 두 도구가 각각 그 빈자리를 채운다.

## 자세히

### Flyway

**지금 방식(ddl-auto: update)의 문제**
- Hibernate가 Entity를 보고 테이블과 컬럼을 만든다. 새 컬럼 추가는 하지만, 컬럼 삭제·이름 변경·타입 변경은 하지 않거나 위험하게 한다.
- SA 5.1~5.2의 CHECK 제약(배송담당자면 hub_id 필수), DEFAULT 값, 부분 유니크 인덱스(`WHERE deleted_at IS NULL`)를 Entity 어노테이션만으로는 정확히 표현하기 어렵다. 리뷰 1의 minor 14가 바로 이 문제다.
- 언제, 누가, 어떤 스키마 변경을 했는지 기록이 남지 않는다. 팀원 PC마다 DB 모양이 달라질 수 있다.
- 그래서 실무에서는 운영 DB에 `update`를 쓰지 않는 것이 보통이다.

**Flyway 방식**
```
user-service/src/main/resources/db/migration/
├── V1__create_p_users.sql
├── V2__add_delivery_manager_check.sql
└── V3__add_partial_unique_index.sql
```
```sql
-- V2__add_delivery_manager_check.sql
ALTER TABLE p_users
    ADD CONSTRAINT ck_delivery_manager_hub
    CHECK (is_delivery_manager = FALSE OR hub_id IS NOT NULL);
```
- 의존성(`flyway-core`, `flyway-database-postgresql`)을 넣으면 Boot가 기동할 때 자동으로 실행한다.
- 실행한 파일은 `flyway_schema_history` 테이블에 버전과 체크섬으로 기록한다. 다음 기동 때는 새 파일만 실행한다.
- 이미 실행한 파일을 고치면 체크섬이 달라져 기동이 실패한다. 스키마 이력을 몰래 바꾸지 못하게 막는 장치다. 바꾸려면 새 버전 파일을 추가한다.
- Hibernate는 `ddl-auto: validate`로 바꾼다. 테이블을 만들지 않고, Entity와 실제 스키마가 다르면 기동을 멈춘다.
- 스키마 변경이 SQL 파일로 Git에 남으니 코드 리뷰를 받을 수 있다.

**이 프로젝트에 적용하면**
- 서비스마다 자기 스키마(`user_schema` 등)에 자기 마이그레이션을 둔다. `spring.flyway.schemas=user_schema`를 config-repo에 둔다. 서비스 계정은 이미 자기 스키마에 CREATE 권한이 있다(init.sql).
- Testcontainers 통합 테스트도 같은 마이그레이션으로 스키마를 만든다. 테스트 DB와 운영 DB의 모양이 같아진다.
- 대안으로 Liquibase가 있다. 롤백 기능과 XML·YAML 형식을 지원하지만, Flyway는 SQL을 그대로 써서 더 단순하다.
- 모든 서비스가 영향을 받는 결정이라, 공통 인프라 채택 전에 팀과 정하는 게 좋다.

### Alembic을 Spring에서 쓸 수 있나

**결론: Spring Boot와 함께 쓰는 도구가 아니다.** 기술적으로 억지로 붙일 수는 있지만 얻는 게 없다.
- Alembic은 Python의 SQLAlchemy용 마이그레이션 도구다. 마이그레이션 파일이 Python 스크립트이고, `alembic upgrade head` 같은 Python CLI로 실행한다.
- Spring Boot가 기동할 때 자동으로 실행해 주는 도구는 Flyway와 Liquibase뿐이다. Alembic은 이 흐름에 들어오지 못한다.
- 마이그레이션은 결국 DB에 SQL을 실행하는 일이라, Alembic을 별도 Python 컨테이너로 돌려 같은 PostgreSQL에 적용할 수는 있다. 하지만 Java 프로젝트에 Python 실행 환경을 하나 더 들여야 한다.
- Alembic의 가장 큰 장점인 자동 생성(`--autogenerate`)은 SQLAlchemy 모델과 DB를 비교하는 기능이다. 우리 모델은 JPA Entity라서 이 기능을 쓸 수 없다.
- Testcontainers 통합 테스트에서도 앱과 같은 마이그레이션을 자동으로 적용할 수 없다.

**Alembic, Flyway, Liquibase 비교**
| | Alembic | Flyway | Liquibase |
|---|---|---|---|
| 생태계 | Python, SQLAlchemy | Java(JVM), Spring Boot 자동 실행 | Java(JVM), Spring Boot 자동 실행 |
| 마이그레이션 형식 | Python 스크립트(`upgrade()`, `downgrade()` 안에 `op.add_column(...)`) | SQL 파일(`V2__add_check.sql`). Java 클래스로도 작성 가능 | XML, YAML, JSON, SQL 변경 묶음(changeSet) |
| 순서 관리 | 리비전 ID와 이전 리비전(`down_revision`)을 연결한 그래프. 브랜치와 병합 가능 | 파일 이름의 버전 번호 순서(일직선) | changeSet의 id·작성자와 파일 안 순서 |
| 자동 생성 | `--autogenerate`: SQLAlchemy 모델과 DB를 비교해 초안 생성 | 기본 기능 없음. IntelliJ 플러그인(JPA Buddy) 등으로 Entity 차이를 SQL로 생성 | `diffChangeLog`: liquibase-hibernate 확장으로 Entity와 DB 비교 |
| 되돌리기 | `downgrade()`를 기본으로 작성 | 무료판에는 없다(유료 에디션 기능). 되돌리는 내용을 새 버전 파일로 추가 | rollback 기본 지원 |
| 이력 테이블 | `alembic_version`: 현재 리비전만 저장 | `flyway_schema_history`: 실행한 모든 버전과 체크섬 | `DATABASECHANGELOG`: 실행한 모든 changeSet과 체크섬 |
| 실행 시점 | 보통 배포 단계에서 CLI로 따로 실행 | 앱 기동 시 자동(Gradle·CLI로 따로도 가능) | 앱 기동 시 자동(따로도 가능) |

**Alembic에 익숙하다면**
- Alembic의 "되돌리기 기본 지원"과 "모델 비교 자동 생성"이 좋았다면, Java 쪽에서 가장 비슷한 것은 Liquibase다.
- Flyway는 그 두 기능이 기본으로 없는 대신 SQL을 그대로 쓰고 규칙이 단순하다.
- 이 프로젝트에는 Flyway를 권한다. SA에 이미 SQL 형태의 스키마(init.sql, DBML)가 있고, 팀이 SQL을 직접 읽고 리뷰하기 쉽다. 되돌리기는 운영에서 실제로 쓰기 어려운 경우가 많아(데이터 손실 위험), 실무에서도 "되돌리는 새 버전을 추가"하는 방식을 많이 쓴다.

### JaCoCo

> 더 자세한 설명(원리, 리포트 읽는 법, 실제 예시, 함정)은 [JaCoCo 노트](2026-10-14-jacoco-coverage.md)에 있다.

**동작 방식**
- 테스트를 실행할 때 JVM에 에이전트를 붙여 클래스를 읽어 들이는 순간 바이트코드에 "여기 실행됨" 표시를 심는다.
- 테스트가 끝나면 어떤 줄과 분기(if의 참·거짓 양쪽)가 실행됐는지 리포트(HTML, XML, CSV)를 만든다.
```groovy
plugins { id 'jacoco' }
tasks.named('test') { finalizedBy 'jacocoTestReport' }
// ./gradlew test → build/reports/jacoco/test/html/index.html
```
- `jacocoTestCoverageVerification`에 최소 기준(예: 라인 70%)을 걸면, 기준 아래로 떨어질 때 빌드가 실패한다. CI에서 품질 하한선으로 쓴다.

**이 프로젝트를 실제로 재 보니 (2026-10-14, 저장소를 바꾸지 않고 임시로 측정)**
| 모듈 | 라인 | 분기 |
|---|---|---|
| common | 72% | 64% |
| gateway-service | 87% | 73% |
| user-service | 89% | 100% |
- common의 `BaseEntity`, `ApiResponse`가 0%로 나왔다. 실제로는 user-service 통합 테스트가 실행하는 코드다. 기본 리포트는 모듈마다 자기 테스트만 센다. 멀티 모듈에서는 `jacoco-report-aggregation`으로 합쳐 봐야 정확하다.

**커버리지의 한계**
- 커버리지는 "실행됐는가"이지 "검증했는가"가 아니다. assert가 하나도 없는 테스트도 커버리지를 올린다.
- 그래서 앞서 동시 가입 테스트에서 한 것처럼, 일부러 코드를 망가뜨려 테스트가 실패하는지 보는 **뮤테이션 테스트**(PIT)가 보완 관계다.
- 100%를 목표로 하면 설정 코드나 단순 getter까지 테스트하느라 비용만 든다. 도메인 규칙과 보안 경계처럼 중요한 코드를 높게 유지하는 편이 낫다.

## 이 프로젝트에서는
- Flyway: 2026-10-14 도입(스텝 12, ADR 0007). `ddl-auto: validate`, user-service V1에 SA 5.2 제약 반영
- JaCoCo: 도입 보류. 측정 결과는 리뷰 2에 있다
- 검사 기록: [리뷰 2](../steps/review-02-reinvention-check.md) (커버리지 측정 결과 포함)
- Flyway는 모든 서비스의 스키마 관리 방식을 바꾸므로, 공통 인프라 리뷰 때 팀에 함께 공유한다.

## 면접 포인트
- "운영에서 `ddl-auto`를 쓰면 안 되는 이유와 대안": 컬럼 삭제·변경 불가, 이력 없음, 예측 불가능 → Flyway/Liquibase + `validate`
- "이미 적용한 마이그레이션 파일을 수정하면?": 체크섬 불일치로 기동 실패. 새 버전 파일을 추가해야 한다.
- "커버리지가 높으면 테스트가 좋은가?": 아니다. 실행과 검증은 다르다. 뮤테이션 테스트로 보완한다.
- "Flyway와 Liquibase 중 무엇을 고르나": SQL을 직접 다루고 단순함을 원하면 Flyway, 되돌리기·DB 독립 형식·자동 비교가 필요하면 Liquibase.

## 참고
- https://documentation.red-gate.com/fd (Flyway)
- https://docs.spring.io/spring-boot/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway
- https://docs.gradle.org/current/userguide/jacoco_plugin.html
- https://docs.gradle.org/current/userguide/jacoco_report_aggregation_plugin.html
