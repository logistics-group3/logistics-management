# 레이어별 코드 규칙

고치는 코드의 위치에 맞는 규칙 파일을 읽는다. 원본은 haneol 브랜치의 `.claude/rules/`이고, 이 저장소 구조(루트 모듈, Flyway, Gateway Spring Security, 내부 API DTO 반환)에 맞춰 고쳤다. 공통 규칙은 [`docs/agents/coding-conventions.md`](../coding-conventions.md), 리뷰 때는 [리뷰 체크리스트](../../guides/review-checklist.md)를 함께 본다.

| 파일 | 적용 경로 | 내용 |
|---|---|---|
| [common.md](common.md) | 모든 코드 | 계층, common 사용법, 예외, 네이밍 |
| [controller.md](controller.md) | `*-service/src/main/java/**/presentation/**` | URL, 응답, 권한, Request·Response DTO |
| [service.md](service.md) | `*-service/src/main/java/**/application/**` | 트랜잭션, Command·Result, 소유자 검증, 다른 서비스 호출 |
| [domain.md](domain.md) | `*-service/src/main/java/**/domain/**` | Entity, enum, Repository 인터페이스, ErrorCode |
| [repository.md](repository.md) | `*-service/src/main/java/**/infrastructure/persistence/**` | Spring Data, QueryDSL 검색, 논리 삭제, 동시성 |
| [feign.md](feign.md) | `*-service/src/main/java/**/infrastructure/client/**` | FeignClient, 재시도, 원격 에러 |
| [migration.md](migration.md) | `*-service/src/main/resources/db/migration/**` | Flyway SQL, 테이블·제약 이름 |
| [config.md](config.md) | `config-repo/**`, `application*.yml`, `*Properties.java`, compose, `.env.example` | 설정 위치, 비밀값 |
| [test.md](test.md) | `**/src/test/**` | 단위·통합·슬라이스 테스트 |

각 파일의 머리말 `paths:`는 Claude Code가 경로별로 규칙을 자동으로 불러올 때 쓴다(`script/setup-agents.sh`가 `.claude/rules/`에 연결한다). 다른 에이전트는 루트 `AGENTS.md`의 안내대로 이 표를 보고 직접 읽는다.
