# 품질 기준

작업량이 커져도 품질을 같은 수준으로 유지하기 위한 기준이다. 외부 플러그인이 없는 에이전트도 이 문서의 텍스트 규칙만으로 같은 기준을 지킨다.

## 1. 완료 기준 (Definition of Done)
- `./gradlew build`가 통과한다. 실패하면 실패 출력과 함께 보고한다.
- 변경한 동작에 대한 테스트가 있다. 테스트를 쓰지 못했다면 이유를 보고한다.
- 실행이 필요한 변경(설정, 라우팅, 기동)은 실제로 띄워서 확인한다. 확인하지 못했다면 "미확인"이라고 명시한다.
- 검증 근거(실행한 명령과 결과) 없이 완료라고 말하지 않는다.
- progress 파일을 갱신했다. 되돌리기 어려운 결정을 했다면 ADR을 남겼다.
- 학습 가치가 있는 지점에 `Note:` 주석을 달았고, 남발하지 않았다.

## 2. 작업 흐름
1. **이해**: progress 파일, SA 문서의 해당 절, 기존 코드를 먼저 읽는다.
2. **계획**: 파일 3개 이상 바꾸거나 설계 선택이 있으면 계획을 먼저 세운다. 사용자 확인이 필요한 선택은 묻는다.
3. **테스트 먼저**: 도메인 규칙과 버그 수정은 실패하는 테스트로 시작한다.
4. **구현**: 필요한 만큼만 쓴다. 과설계를 경계한다(아래 4절).
5. **검증**: 1절 기준을 확인한다.
6. **리뷰**: 스스로 diff를 다시 읽고 4절 체크리스트를 적용한다.
7. **기록**: progress, 학습 노트, ADR을 갱신한다.

## 3. 디버깅
- 추측으로 고치지 않는다. 재현 → 원인 가설 → 확인(로그, 테스트) → 수정 → 회귀 테스트 순서로 진행한다.
- 같은 수정을 두 번 실패하면 멈추고 가설을 다시 세운다.
- 원인과 해결 과정은 학습 가치가 크다. 사용자가 물으면 노트로 남긴다.

## 4. 리뷰 체크리스트
계층별 확인 항목은 [리뷰 체크리스트](../guides/review-checklist.md) 한 곳에 둔다. 근거 규칙은 [레이어별 규칙](rules/README.md)이다.

## 5. 외부 품질 도구
외부 플러그인은 선택 사항이다. 통합 후보는 `.claude` 개인 설정·학습 패널을 포함하지 않으며, 도구가 없어도 위 텍스트 작업 흐름과 완료 기준을 따른다. 아래 목록과 비용은 원작성 브랜치의 도구 검토 기록이며 팀 설치 의무가 아니다.

| 도구 | Claude Code | Codex | 쓰는 시점 |
|---|---|---|---|
| superpowers (obra/superpowers) | `claude plugin install superpowers@claude-plugins-official --scope project` | Codex CLI `/plugins`에서 superpowers 설치 | 계획, TDD, 디버깅, 완료 전 검증, 리뷰 흐름의 기본 |
| ponytail (DietrichGebert/ponytail) | `claude plugin marketplace add DietrichGebert/ponytail` 후 `claude plugin install ponytail@ponytail --scope project` | `codex plugin marketplace add DietrichGebert/ponytail` 후 `codex plugin add ponytail@ponytail` | 과설계 방지. `lite`로 운용한다. 커밋 전 `/ponytail-review` |
| jdtls-lsp | `brew install jdtls` 후 `claude plugin install jdtls-lsp@claude-plugins-official --scope project` | 해당 없음 | Java 정의 이동, 진단 |

**충돌 조정**
- 선택해서 설치한 경우 작업 흐름은 superpowers가 이끈다. ponytail은 `lite`로 두고 "필요한 것만 만든다"는 관점의 보조 검토로 쓴다.
- 작은 작업(한두 파일, 설계 선택 없음)에는 superpowers의 brainstorm → plan 단계를 생략해도 된다.
- 두 도구가 상충하면 이 문서와 coding-conventions가 우선한다.

**보류 중** (기능 구현이 본격화되면 다시 검토한다)
- security-guidance, pr-review-toolkit: JWT와 인가 구현 시점에 검토한다.
- feature-dev: superpowers와 역할이 겹친다.
- 공식 learning/explanatory output style: 학습 모드(learning-guide, study-mode)와 역할이 겹친다.

**상시 컨텍스트 비용** (2026-10-13, `claude plugin details` 추정치. SessionStart 훅이 주입하는 내용은 별도)
| 플러그인 | 버전 | 상시 | 비고 |
|---|---|---|---|
| superpowers | 공식 마켓 고정 커밋 | 약 840 토큰 | 스킬 15개. 스킬을 호출할 때마다 추가 비용이 든다 |
| ponytail | 5.2.0 | 약 641 토큰 | 원작성 브랜치는 `.claude/settings.json`에 `PONYTAIL_DEFAULT_MODE=lite`를 뒀다. 이 저장소에는 없다 |
| jdtls-lsp | - | 0 | 외부 프로세스(jdtls 1.61.0, Homebrew) |
