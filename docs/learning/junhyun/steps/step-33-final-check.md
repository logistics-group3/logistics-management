# 스텝 33. 문서·AI 지침 범위 최종 확인

- 날짜: 2026-10-14
- 결과: 문서·AI 지침 범위(스텝 22~32)를 더한 브랜치 전체를 다시 확인했다. 빌드, 링크, 문서 동기화, 에러 코드, 줄바꿈 모두 통과했다. 확인 중 찾은 중복과 오래된 표기를 고쳤다.

## 확인 결과
| 검사 | 결과 |
|---|---|
| `./gradlew clean build` | 성공(55초). 테스트 98개: common 48, gateway 24, user 23, common-core 2, config-server 1. 실패·오류·skip 0 |
| 저장소 전체 Markdown 상대 링크(URL 인코딩 해제 후) | 깨진 링크 0개 |
| `script/docs-check.sh origin/main` | OK |
| `script/error-codes-check.sh` | PASS, 에러 코드 15개 |
| `git ls-files --eol` | `gradlew`와 `script/*.sh` 8개 모두 LF |
| `git diff origin/main --check` | 공백 오류 없음 |
| 문서 속 `경로` 표기 존재 여부 | 없는 경로 14개는 모두 의도한 것(haneol 원본 경로, 예시 파일, 개인 설정 파일) |

## 고친 것
| 파일 | 내용 |
|---|---|
| `docs/agents/quality-gates.md` | 4장 리뷰 체크리스트를 `docs/guides/review-checklist.md` 링크로 바꿨다(기준 위치 하나). 저장소에 없는 `.claude/settings.json`을 원작성 브랜치 설정이라고 적었다 |
| `docs/guides/review-checklist.md` | quality-gates에만 있던 두 항목(계층 의존 방향, 불필요한 추상화)을 옮겼다 |
| `step-23`, `step-24` 문서 | 스텝 번호 참조를 실제 번호로 고쳤다(링크 점검 31 → 33, 모듈 지침 27 → 25) |
| `docs/reviews/base-setting-integration-pr.md` | PR 제목·과제·행동·결과에 문서·AI 지침 범위를 더했다 |
| `docs/progress/integration-base-setting.md` | 스텝 23~33 완료, 다음 단계를 한 PR 기준으로 고쳤다 |

## 리뷰 포인트
1. **한 PR로 합친 이유**: Notion에서는 문서·AI 지침을 dev 대상 별도 PR로 나누기로 했었다. 담당자들이 dev에서 바로 같은 규칙으로 시작하도록 같은 브랜치에 담았다(2026-10-14 결정). 커밋을 스텝별로 나눠 두어 문서 범위만 따로 볼 수 있다(`git log 575a411..HEAD`).
2. **확인하지 못한 것**: Windows clone·실행과 `setup-agents.sh` 복사 경로, GitHub Actions 실제 실행(PR을 올리면 처음 돈다), Claude Code 종료 훅 연결.
