# 스텝 27. 레이어별 코드 규칙과 리뷰 체크리스트

- 날짜: 2026-10-14
- 근거: Notion "docs·AI 지침 통합 방안" 4.2(haneol `.claude/rules/`를 정본 문서로), 6장 결정
- 결과: `docs/agents/rules/` 10개 파일, `docs/guides/review-checklist.md`

## 바꾼 것
| 파일 | haneol 원본에서 바꾼 점 |
|---|---|
| `common.md` | `PageRequests.normalize`, `QuerydslSorts` → 실제 API(`Pageable` 자동 규칙, `SortAllowList`, `QuerydslSupport`). domain 하위 패키지는 선택(현재 user는 평평한 `domain`) |
| `controller.md` | 반환 타입을 `ResponseEntity<ApiResponse<…>>` → `ApiResponse<…>` + `@ResponseStatus`(현재 코드). `UserRole` → `Role` |
| `service.md` | 클래스 단위 `readOnly`는 조회 전용 클래스만. `hasCode`(없는 메서드) → `getErrorCode().code()` 비교 |
| `domain.md` | PK를 `@UuidGenerator`의 `{엔티티}Id` 필드 → 현재 코드의 `id` 필드 + `@Column(name = "{단수}_id")`. ErrorCode에 `@Accessors(fluent = true)` |
| `repository.md` | `RepositoryImpl` 연결 클래스 → Spring Data 인터페이스가 domain 인터페이스를 상속(현재 `UserJpaRepository`) |
| `feign.md` | 타임아웃 2초/5초 → 5초/20초(SA 3.5), 재시도는 `IdempotentRetryConfig`, `RemoteCodes` 제거 |
| `erd.md` → `migration.md` | `doc/sql/*.sql` → Flyway `V{n}__*.sql`. 제약 이름은 현재 V1처럼 `p_`를 포함(`uk_p_users_username`) |
| `config.md` | `config/`·프로파일 파일 → `config-repo/`. 자동 refresh용 JavaBean·`@RefreshScope` 규칙 제거(팀이 자동 refresh 제외) |
| `test.md` | "DB 테스트를 기본 빌드에서 뺀다" → `PostgresIntegrationTest`로 기본 빌드에 포함(현재 규칙), `@WebMvcTest` 슬라이스 |
| `review-checklist.md` | 원본 14번 가이드를 계층별로 정리하고 문서 동기화·스킬 변경·에러 코드 검사 항목 추가 |

## 리뷰 포인트
1. **정본 위치**: 규칙 본문은 도구와 관계없는 `docs/agents/rules/`에 둔다. 각 파일 머리말의 `paths:`는 Claude Code가 경로별로 자동으로 불러올 때 쓰고(스텝 28에서 연결), 다른 에이전트는 `rules/README.md` 표를 보고 직접 읽는다.
2. **코드에 맞춘 이유**: 규칙이 실제 코드와 다르면 에이전트가 규칙을 따를수록 기존 코드와 어긋난 코드를 만든다. 원본의 좋은 규칙(DTO 접미사, DDL 이름, SA API ID 표기, 계층별 금지 사항)은 유지하고 표기만 현재 코드에 맞췄다.
