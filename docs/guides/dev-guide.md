# 서비스 구현 가이드

서비스 하나를 구현하는 순서를 정리한다. 대상은 hub, company, order, delivery 담당자와 user 관리 API 담당자다. 원본은 haneol `doc/guide/11-dev-guide.md`이고 이 저장소 구조(Flyway, 서비스 협업 창구, 레이어 규칙)에 맞췄다. 코드 규칙 자체는 [레이어별 규칙](../agents/rules/README.md)에 있고, 이 문서는 "무엇을 어떤 순서로"만 다룬다.

## 1. 시작 전에 읽을 것
| 순서 | 문서 | 볼 부분 |
|---|---|---|
| 1 | `docs/services/{service}/README.md` | 제공 API, 호출하는 API, 협업 로그 |
| 2 | `docs/SA/sparta-logistics-sa.md` | 내 서비스의 테이블, API, 서비스 간 호출, 보상 흐름 |
| 3 | `{service}-service/AGENTS.md` | 모듈 규칙과 작업 전에 읽을 것 |
| 4 | [공통 규칙](../agents/rules/common.md) | common에 이미 있는 것(다시 만들지 않는다) |
| 5 | `user-service` 코드 | 4계층이 실제로 어떻게 나뉘는지 보는 참고 구현 |
| 6 | `docs/services/{service}/decisions/` | 서비스 결정(상태 `제안`이면 담당자가 확정한다) |

## 2. 구현 순서
```
마이그레이션 → Entity·ErrorCode → Repository → Application → Controller → 내부 API → Feign → 설정 → 테스트 → 문서
```

| # | 작업 | 위치 | 규칙 | 끝나면 확인 |
|---|---|---|---|---|
| 1 | 테이블 생성 SQL | `src/main/resources/db/migration/V{n}__{설명}.sql` | [migration.md](../agents/rules/migration.md) | 서비스 기동 시 Flyway 적용 |
| 2 | Entity, enum, ErrorCode | `domain` | [domain.md](../agents/rules/domain.md) | 기동 시 `ddl-auto: validate` 통과, `script/error-codes-check.sh` |
| 3 | Repository 인터페이스와 구현 | `domain`, `infrastructure/persistence` | [repository.md](../agents/rules/repository.md) | `PostgresIntegrationTest`로 쿼리 확인 |
| 4 | Command·Result, Service | `application`, `application/dto` | [service.md](../agents/rules/service.md) | 단위 테스트 |
| 5 | Request·Response, Controller | `presentation`, `presentation/dto` | [controller.md](../agents/rules/controller.md) | Swagger(http://localhost:8080/swagger-ui.html)에서 호출 |
| 6 | 내부 API(`/internal/**`) | `presentation/Internal{X}Controller` | [controller.md](../agents/rules/controller.md) | 응답은 DTO 그대로(ADR 0010) |
| 7 | 다른 서비스 호출 | `infrastructure/client` | [feign.md](../agents/rules/feign.md) | 상대 서비스와 계약 합의(3장) |
| 8 | 서비스 고유 설정 | `config-repo/{service}-service.yml` | [config.md](../agents/rules/config.md) | [설정 보안 가이드](config-security.md) 체크리스트 |
| 9 | 테스트 | `src/test` | [test.md](../agents/rules/test.md) | `./gradlew :{service}-service:build` |
| 10 | 문서 | `docs/services/{service}/README.md`, `docs/error-codes.md`, 필요하면 ADR | [문서 작성 가이드](writing.md) | `script/docs-check.sh` |

AI 에이전트로 구현할 때는 `impl-service` 스킬이 이 순서를 계층별로 끊어 진행하고 `docs/services/{service}/notes.md`에 기록한다.

## 3. 다른 서비스와 맞출 것
| 항목 | 누구와 | 어디에 기록 |
|---|---|---|
| 내부 API 경로, 요청·응답 필드 | 호출하는 쪽 담당자 | 제공하는 서비스의 `docs/services/{service}/README.md` 제공 API 표 |
| 보상 분기에 쓰는 에러 코드(예: `STOCK_INSUFFICIENT`) | order 담당자 | `docs/error-codes.md` |
| 보상(되돌리기) API | order 담당자 | 제공하는 서비스 README와 order README 호출 API 표 |

- 다른 서비스 코드를 직접 고치지 않는다. 그 서비스 README 협업 로그에 `OPEN`으로 요청한다([서비스 협업 창구](../services/README.md)).
- DTO 클래스는 서비스끼리 공유하지 않는다. common에 넣지 않고, 호출하는 쪽이 `infrastructure/client/dto`에 필요한 필드만 둔다.

## 4. 로컬에서 확인
```bash
script/run.sh                      # 전체
script/run.sh hub-service          # 내 서비스만 다시 빌드·기동
```
로그인: `POST /api/v1/auth/login`(MASTER 계정은 `.env`) → Swagger Authorize에 토큰만 넣는다. 자세한 방법은 [로컬 실행 가이드](local-run.md)에 있다.

## 5. PR 올리기 전
- [ ] `./gradlew build` 통과(Docker가 켜져 있어야 Testcontainers 테스트가 돈다)
- [ ] `script/docs-check.sh`, `script/error-codes-check.sh` 통과
- [ ] [리뷰 체크리스트](review-checklist.md) 셀프 점검
- [ ] PR 본문은 [Git 작업 가이드](git-workflow.md)의 STAR 형식
