# CLAUDE.md — 스파르타 물류 MSA (공통 인프라)

## 프로젝트
MSA 기반 국내 B2B 물류 관리·배송 시스템. 비즈니스 서비스 5개(user, hub, company, order, delivery) + eureka, config, gateway.
**기준 문서는 Notion SA 문서(스파르타물류_SA문서_Master)** 이고, `doc/`은 그중 실제 구현된 내용을 코드 기준으로 정리한다.
Notion과 다르게 구현해야 하면 `doc/etc/06-adr.md`에 이유를 남긴다.

코드 위치: `backend/{module}` (`common`, `eureka-server`, `config-server`, `gateway-service`, `{domain}-service`).
설정은 루트 `config/`(config-server가 내려줌), DB DDL은 `doc/sql/{NN}-{service}.sql` (서비스별 DB 컨테이너가 하나씩 실행).
빌드: 루트에서 `./gradlew build`. 실행: `script/run.sh` (공통) / `script/run.sh --profile domain` (전체).

작업 전 관련 문서를 먼저 읽는다:
- `doc/design/01-overview.md` 범위·일정·담당
- `doc/design/02-architecture.md` 서비스 구성·프로젝트 구조·common 모듈·인증·통신·인프라
- `doc/design/03-erd.md` + `doc/sql/{NN}-{service}.sql` 작업 대상 서비스의 테이블
- `doc/design/04-api.md` 공통 규약 + 작업 대상 서비스 섹션
- `doc/guide/11-dev-guide.md` 구현 순서, `doc/guide/12-config-ops.md` 설정(config/·비밀값)을 건드릴 때

## 코드 컨벤션
`.claude/rules/`에 레이어별 규칙이 있다 (common 항상 적용, 나머지는 경로별 자동 적용).
공통 기능(응답 래퍼, 예외, 사용자 헤더, 역할 확인, Audit, Feign 설정)은 `common` 모듈 것을 쓰고 서비스에서 다시 만들지 않는다.

## 문서 동기화 규칙 (필수)
코드를 수정하면 같은 작업 안에서 문서도 수정한다. 상세: `doc/README.md`

| 바꾼 것 | 갱신할 곳 |
|---|---|
| `{service}` Entity·컬럼 | `doc/sql/{NN}-{service}.sql` + `doc/design/03-erd.md` |
| `{service}` Controller·URL·권한·DTO | `doc/design/04-api.md` → `{service}-service` 섹션 |
| FeignClient, Slack, 보상 로직 | `doc/design/02-architecture.md` → 5. 서비스 간 통신 |
| gateway, JWT, 인가 | `doc/design/02-architecture.md` → 4. 인증 / 인가 |
| common 모듈 | `doc/design/02-architecture.md` → 3. common 모듈 (+ `.claude/rules/common.md`) |
| docker-compose, Dockerfile, config-server | `doc/design/02-architecture.md` → 6. 인프라·실행 |
| 서비스 추가/삭제, 포트, `config/*.yml`(설정·프로파일) | `doc/design/02-architecture.md` → 1. 서비스 구성 |
| Notion SA와 다른 결정 | `doc/etc/06-adr.md` 맨 위에 ADR 추가 |

- 문서를 바꾸면 `doc/etc/07-changelog.md` 맨 위 오늘 날짜 섹션에 한 줄: `- [영역/서비스] 내용 (관련 파일) — 작성자`
- 문서는 실제 코드 기준으로 쓴다. 추측으로 채우지 않는다
- 작업 종료 시 Stop 훅(`script/docs-check.sh --hook`)이 누락을 알려주면 문서를 보완한다
