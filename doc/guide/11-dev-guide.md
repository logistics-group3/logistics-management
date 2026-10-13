# 11. 서비스 개발 가이드 — 도메인 하나를 구현하는 순서

> 최종 수정: 2026-10-12 · 대상: **서비스 담당자** (hub · company · order · delivery, user 관리 API)
> 코드 규칙 원본: [`.claude/rules/`](../../.claude/rules/README.md) — 이 문서는 "무엇을 어떤 순서로"만 다룬다.

## 1. 시작 전에 읽을 것 (30분)

| 순서 | 문서 | 볼 부분 |
|---|---|---|
| 1 | Notion SA 문서 | 내 서비스의 테이블(5장)·API(6장)·호출 관계(2.3)·보상(7장) |
| 2 | `doc/design/02-architecture.md` | §2 패키지 구조, §3 common 모듈, §5 통신 |
| 3 | `doc/design/04-api.md` §1 | 응답·에러·페이지 공통 규약 |
| 4 | `.claude/rules/common.md` | common에서 가져다 쓸 것 (다시 만들지 않는다) |
| 5 | `backend/user-service` | 4계층이 실제로 어떻게 나뉘는지 보는 참고 구현 |

## 2. 구현 순서

```
DDL → Entity → Repository → Application(Service) → Controller → 내부 API/Feign → 테스트 → 문서
```

| # | 작업 | 위치 | 규칙 | 끝나면 |
|---|---|---|---|---|
| 1 | DDL 작성 | `doc/sql/{NN}-{service}.sql` | `erd.md` | `script/run.sh --profile domain --reset`으로 DB 생성 확인 |
| 2 | Entity·enum·ErrorCode | `domain/model`, `domain/exception` | `domain.md` | 서비스 기동 시 `ddl-auto: validate` 통과 |
| 3 | Repository 인터페이스 + 구현 | `domain/repository`, `infrastructure/persistence` | `repository.md` | |
| 4 | Command/Result DTO + Service | `application`, `application/dto` | `service.md` | 단위 테스트 |
| 5 | Request/Response DTO + Controller | `presentation`, `presentation/dto` | `controller.md` | Swagger(`:8080/swagger-ui.html`)에서 호출 |
| 6 | 내부 API (`/internal/**`) 제공 | `presentation/Internal{X}Controller` | `controller.md` | |
| 7 | 다른 서비스 호출 (Feign) | `infrastructure/client` | `feign.md` | 상대 서비스와 DTO 필드 합의 |
| 8 | 서비스 고유 설정 | `config/{service}-service.yml` | `config.md` | |
| 9 | 문서 | `03-erd.md`, `04-api.md`, `02-architecture.md` §5, CHANGELOG | `doc/README.md` | `./script/docs-check.sh` OK |

## 3. 로컬 실행

상세: [16-local-run.md](16-local-run.md) — 전체/일부 서비스 올리기, IDE 실행, 바꾼 것만 다시 올리기, 내리기.

```bash
script/run.sh --profile domain                                   # 전체
./gradlew :{service}-service:bootJar && docker compose up -d --build {service}-service   # 내 서비스만 다시
```

토큰 받기: `POST :8080/api/v1/auth/login` (MASTER 계정은 `.env`) → Swagger 우측 상단 Authorize에 `Bearer` 없이 토큰만.

## 4. 다른 서비스와 맞출 것

| 항목 | 누구와 | 어디에 기록 |
|---|---|---|
| 내부 API 경로·DTO 필드 | 호출하는 쪽 담당자 | `04-api.md` 내부 API 절, `02-architecture.md` §5 표 |
| 에러 코드 이름 (보상 분기에 쓰임, 예: `STOCK_INSUFFICIENT`) | order-service 담당 | `04-api.md` 에러 코드 표 |
| 보상(롤백) API | order-service 담당 | `02-architecture.md` §5 |

DTO 클래스는 서비스끼리 공유하지 않는다 (common에 넣지 않음). 호출하는 쪽이 `infrastructure/client/dto`에 필요한 필드만 복사해 둔다.

## 5. PR 올리기 전

- [ ] `./gradlew :{service}-service:build` 통과
- [ ] `./script/docs-check.sh` OK
- [ ] [14-review-checklist.md](14-review-checklist.md) 셀프 체크
