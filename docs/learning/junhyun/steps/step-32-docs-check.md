# 스텝 32. 문서 동기화 검사

- 날짜: 2026-10-14
- 근거: Notion "docs·AI 지침 통합 방안" 문서 동기화 항목(haneol `docs-check.sh` 채택, changelog는 요구하지 않음, `skip-docs` 라벨)
- 결과: `docs/doc-map.tsv`, `script/docs-check.sh`, `.github/workflows/docs-guard.yml`을 추가했다. 9가지 경우를 실행해 기대한 종료 코드를 확인했다.

## 바꾼 것
| 파일 | 내용 |
|---|---|
| `docs/doc-map.tsv` | 코드 경로 glob → 함께 고칠 문서. 서비스별 presentation·client·migration → `docs/services/{service}/README.md`, `*ErrorCode.java` → `docs/error-codes.md`, gateway·common → 협업 창구, `.env.example` → 설정 보안 가이드, compose → 로컬 실행 가이드, `settings.gradle` → README·AGENTS.md, 스킬 → 스킬 README |
| `script/docs-check.sh` | 세 가지 모드: 인자 없음(작업 트리), 기준 ref(`base...HEAD`, CI), `--hook`(에이전트 종료 훅, 누락 시 exit 2) |
| `.github/workflows/docs-guard.yml` | PR마다 `docs-check`(skip-docs 라벨이면 건너뜀)와 `error-codes-check`(항상) |
| `CLAUDE.md` | 개인 설정으로 종료 훅을 거는 예시 |
| `README.md` | 빠른 실행을 `script/run.sh` 기준으로 바꿨다 |

## haneol 원본과 다른 점
| 항목 | haneol | 이 저장소 |
|---|---|---|
| CHANGELOG | 코드가 바뀌면 항상 요구 | 요구하지 않는다(팀 결정). 매핑된 문서만 본다 |
| 매핑 없는 코드 변경 | CHANGELOG 요구로 실패 | 통과 |
| 경로 | `backend/*`, `doc/*` | 루트 모듈, `docs/` |
| 훅 | 저장소 `.claude/settings.json`에 연결 | 연결하지 않는다. 개인이 `settings.local.json`에 건다 |
| 에러 코드 검사 | 없음 | 같은 워크플로에서 `error-codes-check.sh` |

## 리뷰 포인트
1. **매핑을 좁게 잡았다**: 검사가 자주 실패하면 `skip-docs`를 습관처럼 붙이게 된다. 그래서 다른 담당자가 읽는 문서(API 계약, 에러 코드, 실행·비밀값 안내)만 매핑했다. domain·application 내부 변경은 검사하지 않는다.
2. **훅을 팀 설정으로 넣지 않은 이유**: 종료 훅은 응답이 끝날 때마다 돈다. 팀 전원의 에이전트 동작을 바꾸는 설정이라 개인 선택으로 두었다. Codex에는 같은 방식의 훅을 걸지 않았다.
3. **CI는 `origin/<base>...HEAD`**: PR의 기준 브랜치와 갈라진 뒤의 변경만 본다. `fetch-depth: 0`이 있어야 기준 브랜치 이력이 있다.

## 검증
임시 worktree(검증 뒤 삭제)에서 실행했다.

| # | 경우 | 결과 |
|---|---|---|
| 1 | 현재 작업 트리(문서·스크립트만 변경) | OK, exit 0 |
| 2 | `origin/main...HEAD` (이 브랜치 전체) | OK, exit 0 |
| 3 | hub presentation 파일 추가, 문서 없음 | 누락 1건과 근거 출력, exit 1 |
| 4 | 같은 상태에서 `--hook` | exit 2 |
| 5 | `--hook`, 입력에 `stop_hook_active: true` | exit 0 |
| 6 | `docs/services/hub/README.md`도 수정 | OK, exit 0 |
| 7 | 테스트 파일만 변경 | OK, exit 0 |
| 8 | 범위 모드에서 누락, `/bin/bash` 3.2 | exit 1 |
| 9 | 없는 ref | exit 2 |

- 워크플로 YAML은 Ruby YAML 파서로 읽어 job 두 개를 확인했다. GitHub에서의 실제 실행은 PR을 올린 뒤 확인한다.
- `skip-docs` 라벨은 아직 저장소에 없다. 관리자가 만든다([Git 작업 가이드](../../../guides/git-workflow.md) 5장).
