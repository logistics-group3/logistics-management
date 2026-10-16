---
title: GitHub PR의 중대 결함과 리뷰 포인트를 검사해 자동 승인하려면
date: 2026-10-15
updated: 2026-10-15
asker: Junhyun An
level: 중급
tags: [github-actions, code-review, codex, coderabbit, ci]
---

# GitHub PR의 중대 결함과 리뷰 포인트를 검사해 자동 승인하려면

## 질문

깃헙에 자동리뷰 에이전트 다는 방법 알려줘. pr 올리면 검토하고, 일정 점수 넘으면 머지 approve 하도록 해주고싶어.

후속 요청: GitHub Actions + Codex 또는 OpenAI API key로 중대 결함 없음, 빌드·테스트 통과, PR에 명시된 리뷰 포인트 전부 통과일 때 자동 Approve하고 머지는 사용자가 직접 한다. 구현 전에 방법만 설명한다.

## 짧은 답

현재 요청은 GitHub Actions에서 Codex를 OpenAI API key로 실행하고 조건 충족 시 Approve하는 구성이다. 머지는 사용자가 직접 한다.
승인 조건은 중대 결함 없음, 실제 빌드·테스트 성공, PR 리뷰 포인트 전부 PASS다. 초기 비교에 나온 85점 기준은 사용하지 않는다.
검토 대상 커밋과 리뷰 포인트가 현재 PR과 일치해야 한다. 리뷰 누락·확인 불가·API 오류는 승인 보류로 처리한다.
이 문서는 방법 안내다. 학습 기록만 보강했으며 GitHub 설정 변경, 워크플로 구현, 리뷰 제출과 머지는 수행하지 않았다.

## 현재 요청의 구성 방법

### 준비와 설정 순서

1. OpenAI Platform에서 API key를 발급한다. 공식 Codex Action도 이 키로 실행하므로 Codex와 API key를 양자택일하는 구성이 아니다. API key 사용은 ChatGPT 구독 크레딧과 별도로 Platform에서 과금된다.
2. 저장소 `Settings → Secrets and variables → Actions → New repository secret`에서 이름을 `OPENAI_API_KEY`로 저장한다. 키 값은 채팅이나 파일에 쓰지 않는다.
3. `Settings → Actions → General → Workflow permissions`에서 `Allow GitHub Actions to create and approve pull requests`를 허용한다. 기본 권한은 읽기로 두고 승인 작업에만 필요한 쓰기 권한을 부여한다.
4. 빌드 CI, Codex 리뷰, 결과 검증과 승인을 분리해 구현한다. 첫 적용 범위는 신뢰한 팀원의 저장소 내부 PR로 제안한다.
5. 브랜치 보호에서 빌드·문서 검사·AI 승인 조건 검사를 필수로 지정하고 새 코드 커밋의 기존 승인을 무효화한다. 팀의 공통 영역 리뷰 규칙도 유지한다.
6. 정상 승인, 중대 결함, 리뷰 포인트 실패·누락, CI 실패, API 장애, PR 본문 변경, 새 커밋 추가 사례를 검증한 뒤 운영한다.

### 작업의 역할

| 작업 | 실행 내용 | 권한 경계 |
|---|---|---|
| 빌드·테스트 | Java 17과 Docker 환경에서 `./gradlew build --no-daemon` 실행, 실제 테스트 결과 수집 | OpenAI 키와 승인 토큰 없음 |
| AI 리뷰 | diff·관련 코드·팀 규칙·PR 리뷰 포인트를 검토하고 JSON 반환 | OpenAI 키 사용, GitHub 승인 권한 없음 |
| 승인 | CI의 실제 결과와 JSON 검증, PR 최신 상태 재확인, Approve 또는 보류 | `pull-requests: write`, PR 코드·빌드 실행 없음 |

승인 기준과 실행 코드는 보호된 기준 브랜치에서 읽는다. PR이 워크플로·평가 프롬프트·스키마를 바꿔도 자기 승인을 바꾸지 못하게 설계한다.
권한 있는 작업에서 PR의 빌드 스크립트를 실행하지 않는다. `pull_request_target` 같은 신뢰 컨텍스트를 사용할 때도 PR 변경은 읽을 데이터로 취급한다.
Codex에는 읽기 전용 sandbox와 `drop-sudo`를 함께 적용하는 구성을 권장한다. 읽기 전용 설정 하나만으로 키 보호가 완성되는 것은 아니다.

### PR 리뷰 포인트의 형식과 판정

기존 [PR 템플릿](../../../../.github/pull_request_template.md)에 `## 리뷰 포인트`가 있다. 검사할 요구를 한 항목에 하나씩 작성하고 고유 ID를 붙이는 형식을 제안한다.

```markdown
## 리뷰 포인트
- RP-1: 같은 멱등키로 동시 요청해도 주문이 하나만 생성되는가?
- RP-2: 같은 키에 다른 요청 내용을 보내면 충돌로 거부되는가?
- RP-3: 다른 사용자의 주문에는 접근할 수 없는가?
```

AI는 각 항목에 `PASS`, `FAIL`, `UNKNOWN`과 파일·줄·테스트 근거를 반환한다. `UNKNOWN`은 확인 불가다.
작성자가 표시한 체크박스나 주장 자체는 통과 근거가 아니다. 빈 리뷰 포인트와 단순 형식 안내만 남은 PR도 자동 승인하지 않는다.
기존 자유 형식 PR은 항목 추출이 명확할 때만 검토하고, 불명확하면 형식 보완을 요청하는 방식으로 제안한다.

승인 코드는 PR에서 추출한 원래 ID 목록과 AI 결과를 비교한다. 모든 ID가 정확히 한 번씩 존재하고 모두 PASS여야 한다. 빈 목록의 전체 통과, 항목 누락, ID 중복은 실패 처리한다.
PR 포인트 외에도 전체 diff를 중대 결함 관점에서 검토한다. PR 작성자가 인가 검증 항목을 적지 않았다는 이유로 인가 우회 결함을 무시하지 않는다.

### 최종 승인 조건

```text
중대 결함(P0·P1) 0개
AND 빌드·필수 테스트 실제 성공
AND 유효한 PR 리뷰 포인트가 1개 이상이고 전부 PASS
AND 팀의 필수 CI 정책 충족
AND 검토 범위 누락·확인 불가·결과 형식 오류 없음
AND 검토한 head/base와 리뷰 포인트 내용이 현재 PR과 일치
AND PR이 열려 있고 Draft가 아님
```

P0·P1을 중대 결함으로 사용하는 것은 제안이다. 인가 우회, 데이터 손실, 핵심 기능 오류 등 실제 영향 기준을 프롬프트에 정의한다. 점수는 승인 조건에 넣지 않는다.
빌드 종료 코드뿐 아니라 필요한 테스트가 실행됐는지도 확인한다. 테스트가 모두 빠지거나 Docker 미제공으로 통합 검증을 생략했다면 성공으로 인정하지 않는다.
리뷰 실패나 API 할당량 초과이면 승인하지 않고 원인을 남긴다. 기존 팀 정책에서 허용한 문서 검사 예외는 별도로 처리한다.

승인 직전에 PR 상태를 다시 조회한다. head SHA, base SHA, 리뷰 포인트 내용의 해시를 실행 시작 때와 비교하고 바뀌면 재검토한다.
코드·기준 브랜치 변경은 다시 빌드하고, 본문만 바뀌었다면 동일한 코드 버전의 CI 증거를 검증한 뒤 리뷰를 다시 실행할 수 있다.

### 재실행과 이전 승인 처리

- PR 생성·재오픈·새 커밋·Draft 해제에 대응한다. PR 본문 편집(`edited`)도 리뷰 포인트 변경을 감지하는 계기로 포함한다.
- 같은 PR의 이전 실행은 취소하고, 오래된 결과가 늦게 도착해 승인하지 못하도록 최종 버전을 비교한다.
- 새 코드에 대한 기존 승인은 브랜치 보호 규칙으로 무효화한다. 본문 변경만으로 기존 승인이 자동 취소된다고 가정하지 않는다.
- 리뷰 포인트가 바뀌면 AI 검사 상태를 대기로 되돌리고 재검토한다. 봇의 이전 승인을 취소하려면 보호 브랜치의 리뷰 취소 권한도 확인해야 한다. 취소 API 실패를 숨기지 않고 필수 AI 검사를 보류·실패로 유지한다.
- 기준 브랜치에 새 커밋이 합쳐질 때도 오래된 CI를 쓰지 않도록 최신 기준 브랜치 검증과 재실행 경로를 설계한다.

자동화 결과에는 중대 결함 수, 각 RP 항목의 상태·근거, CI 실행 링크, 검토 SHA를 남긴다. 조건이 모두 맞으면 Reviews API에 `APPROVE`를 제출하고, 나머지는 사유와 함께 보류한다.
Merge API와 Auto-merge는 구성하지 않는다. 이 요구에서 마지막 병합 버튼은 사용자가 누른다.

### 구현할 파일의 예시

| 예정 파일 | 역할 |
|---|---|
| `.github/workflows/ci.yml` | 빌드·테스트 실행 |
| `.github/workflows/ai-review.yml` | 리뷰 실행·재실행과 승인 작업 연결 |
| `.github/ai-review/prompt.md` | 중대 결함 정의, 팀 규칙, RP별 판정 기준 |
| `.github/ai-review/result.schema.json` | 결함·리뷰 포인트·근거의 출력 형식 |
| `.github/ai-review/approve.mjs` | 결과 완전성·CI·최신 버전 검사와 리뷰 제출 |

이 목록은 설계 예시이며 해당 파일을 생성하지 않았다. 기존 PR 템플릿과 브랜치 보호 설정을 함께 맞춰야 한다.
도입 PR로 승인 정책 자체를 바꾸는 경우는 기존 팀 리뷰 절차를 통해 먼저 반영한다.

## 자세히

다음은 최초 질문에서 비교한 선택지다. 점수 기준과 Auto-merge 설명은 참고 이력이며 현재 적용 요청은 앞의 조건부 Approve와 수동 머지다.

### 1. 리뷰, 승인, 머지는 각각 다르다

| 동작 | 결과 |
|---|---|
| 리뷰 | 변경 내용을 읽고 문제와 개선점을 남긴다 |
| Approve | GitHub 리뷰 상태를 승인으로 제출한다 |
| Merge | PR 코드를 대상 브랜치에 합친다 |
| Auto-merge | 활성화한 PR이 필수 승인과 검사를 만족하면 GitHub가 합친다 |

자동 승인까지만 원하면 Merge API나 Auto-merge를 연결할 필요가 없다. 실제 자동 머지까지 원하면 저장소에서 Auto-merge를 허용하고 대상 PR에도 활성화한다.
브랜치 보호 규칙과 필수 검사는 그대로 만족해야 한다. 봇의 Approve가 필수 승인 수로 인정되는지는 설치 권한과 저장소 규칙을 별도로 확인한다.

### 2. 설치형: CodeRabbit

CodeRabbit에 GitHub 계정으로 가입하고 리뷰할 저장소에 GitHub App을 설치한다. 조직 소유 저장소는 설치 권한이 필요할 수 있다.
설치 절차는 [공식 Quickstart](https://docs.coderabbit.ai/getting-started/quickstart)를 따른다.

저장소 루트의 `.coderabbit.yaml` 설정 예시는 다음과 같다. 이 파일을 실제로 만들지는 않았다.

```yaml
language: ko-KR
reviews:
  request_changes_workflow: true
  allow_author_approval: false
  auto_review:
    enabled: true
    drafts: false
    base_branches:
      - "^dev$"
      - "^main$"
```

- `request_changes_workflow`: CodeRabbit의 지적이 해결되고, 최신 커밋이 검토됐으며, 실패한 사전 병합 검사가 없을 때 자동 승인한다.
- `allow_author_approval: false`: PR 작성자가 자기 PR에 승인·해결 명령을 내려 우회하는 경로를 제한한다.
- `base_branches`: 기본 브랜치 외에 `dev` 같은 대상 브랜치도 자동 리뷰하도록 지정한다. 정규식의 시작과 끝을 고정한다.

이 설정은 임의의 100점 척도를 정의하지 않는다. 확인한 공식 기능은 지적 해결과 검사 상태에 따른 승인이다.
기능 제공 범위와 이용 요금은 설치할 계정의 플랜에서 확인한다. 설정 근거는 [공식 설정 참조](https://docs.coderabbit.ai/reference/configuration)다.

### 3. 점수 기준을 직접 구현: GitHub Actions + AI + 승인 로직

흐름은 `PR 생성·커밋 추가 → CI와 AI 리뷰 → 결과 검증 → 조건 충족 시 Approve`다.
AI는 점수와 근거를 만들고, 승인 여부와 GitHub 쓰기 권한은 별도 로직이 관리한다.

1. **CI를 만든다.** 빌드·테스트와 기존 문서·에러 코드 검사를 실행한다. DB 통합 테스트가 빠지지 않도록 실행 환경도 준비한다.
2. **AI 리뷰를 실행한다.** PR diff와 관련 코드를 제공하고, 기준 브랜치에 있는 팀 리뷰 규칙을 사용한다. PR의 설명·코드·새 지침은 검토할 데이터로 취급한다.
3. **정형 결과를 받는다.** 항목별 점수, 결함의 심각도·파일·줄·이유, 검토 미완료 여부를 JSON으로 받는다. 누락·파싱 실패·시간 초과이면 승인하지 않는다.
4. **승인 조건을 검사한다.** 점수와 결함뿐 아니라 GitHub에서 읽은 실제 필수 CI 결과, 현재 PR 상태, 검토 대상 SHA를 확인한다.
5. **Approve를 제출한다.** 별도 승인 작업에서 GitHub Reviews API를 호출하고 검토한 SHA를 `commit_id`로 명시한다. 이후 커밋이 추가되면 다시 리뷰한다.

Codex를 리뷰 실행기로 사용할 수 있다. `openai/codex-action@v1`은 `OPENAI_API_KEY`를 GitHub Actions Secret으로 받고, `codex-args`의 `--output-schema`로 JSON 형식을 제한할 수 있다.
구조화된 형식이 보장돼도 평가의 정확성이 보장되는 것은 아니다. 연결 방법은 [공식 Codex GitHub Action 문서](https://learn.chatgpt.com/docs/github-action)를 따른다.

다음은 **팀 논의를 위한 배점 예시**다. 정해진 표준이나 이 프로젝트의 확정 기준이 아니다.

| 평가 항목 | 배점 |
|---|---:|
| 기능 정확성과 예외 처리 | 40점 |
| 인증·인가와 데이터 보호 | 25점 |
| 성공·실패·동시성 테스트의 적절성 | 20점 |
| 계층 규칙과 유지보수성 | 15점 |
| 합계 | 100점 |

각 항목은 점수와 이유를 함께 반환하게 한다. 합산과 범위 검증은 승인 로직이 수행한다.
테스트의 적절성에 대한 AI 평가는 실제 CI 성공 여부와 별도로 본다.

```text
자동 승인 조건 예시
- 총점 85점 이상
- P0·P1 결함 0개
- 검토하지 못한 파일·미확인 판단 없음
- 지정한 필수 CI 모두 성공
- 리뷰 대상 head SHA == 현재 PR head SHA
- PR이 열려 있고 Draft가 아님
- 팀이 별도 검토 대상으로 정한 변경 경로가 아님
```

P0는 긴급 차단 결함, P1은 우선 수정할 중대 결함으로 정의하는 제안이다. 보안이나 데이터 손실 결함은 다른 항목의 높은 점수로 상쇄하지 않는다.
85점도 초기 운영 가설이다. 실제 PR을 사람이 함께 검토해 잘못 승인하거나 불필요하게 막는 사례를 보고 기준을 조정한다.

승인 요청의 형태는 다음과 같다. 단독 실행하는 전체 워크플로가 아니라, 앞의 조건을 확인한 뒤 사용하는 API 요청 예시다.

```text
POST /repos/{owner}/{repo}/pulls/{pull_number}/reviews

{
  "commit_id": "<실제로 검토한 PR head SHA>",
  "event": "APPROVE",
  "body": "평가 90/100, 중대 결함 0개, 필수 CI 성공"
}
```

API는 `Pull requests: write` 권한이 필요하다. PR 작성자와 승인 주체를 분리한다.
`GITHUB_TOKEN`을 쓰면 저장소 `Settings → Actions → General → Workflow permissions`에서 `Allow GitHub Actions to create and approve pull requests`를 허용해야 한다. 조직 정책이 제한할 수도 있다.
워크플로의 승인 작업에는 필요한 `pull-requests: write`만 부여한다. 더 엄격히 분리하려면 승인 전용 GitHub App을 사용하고, 승인 작업에는 PR 코드 실행과 API 키 접근을 넣지 않는다.

리뷰 작업에는 승인 토큰을 주지 않는다. PR 코드·빌드 스크립트를 실행하는 CI에는 리뷰 API 키와 승인 토큰을 주지 않는다.
공개 fork PR은 기본적으로 Secret과 쓰기 토큰을 받을 수 없으므로 첫 적용 대상을 신뢰한 저장소 내부 PR로 제한할 수 있다. 이를 우회하려고 권한 있는 `pull_request_target` 작업에서 PR 코드를 checkout·실행하지 않는다.
승인 판단 코드와 배점·스키마도 PR 작성자가 바꿔 자기 PR을 통과시키지 못하도록 보호된 기준 브랜치에서 가져온다. 모든 작업은 같은 검토 대상 SHA에 연결한다.

브랜치 보호에서 새 커밋이 올라오면 기존 승인을 무효화하도록 설정한다. 필수 검사도 등록해 이전 커밋의 성공이나 단순 댓글로 머지 조건을 만족시키지 않게 한다.
별도 사람 승인이 필요하면 승인 수만 늘리는 것으로 끝내지 않고, 해당 경로의 CODEOWNERS 승인 등 실제 담당자 검토를 요구하는 규칙을 사용한다.

### 4. Codex 기본 GitHub 연동만 사용할 때

GitHub 저장소를 Codex에 연결하고, Codex 설정에서 저장소의 `Review code → Automatic review`를 켠다. 수동으로는 PR 댓글에 `@codex review`를 쓸 수 있다.
공식 문서는 `AGENTS.md`의 코드 리뷰 규칙도 지원한다고 설명한다. [공식 GitHub 연동 문서](https://learn.chatgpt.com/docs/third-party/github)

확인한 문서에는 임의의 점수 임계값으로 GitHub Approve를 제출하는 설정이 없다. 자동 리뷰 연동과 점수 기반 승인 구현을 구분한다.

## 이 프로젝트에서는

2026-10-15의 로컬 `feat/order-create` 체크아웃을 확인했다. 원격 저장소 설정과 설치된 GitHub Apps는 조회하지 않았다.

- [docs-guard 워크플로](../../../../.github/workflows/docs-guard.yml)는 문서 동기화와 에러 코드를 검사한다. 현재 체크아웃의 `.github/workflows/`에는 이 워크플로만 있으며 Gradle 빌드·테스트 CI는 없다.
- [Git 작업 가이드](../../../guides/git-workflow.md)는 리뷰어 1명 이상 승인과 공통 영역의 팀 전체 리뷰를 요구한다. 봇 승인으로 팀 검토를 대체하는 것은 별도 팀 합의 대상이다.
- [리뷰 체크리스트](../../../guides/review-checklist.md), [코딩 규칙](../../../agents/coding-conventions.md), [SA](../../../SA/sparta-logistics-sa.md)를 리뷰 입력으로 연결한다.
- `common`, `common-core`, 공통 설정, 빌드, 인프라는 기존 전체 리뷰 규칙을 유지한다. 보안·마이그레이션·자동화 정책 변경도 자동 승인에서 제외하는 방안을 제안한다.
- 먼저 `feat/* → dev`의 조건부 리뷰를 검증한다. 자동 Approve 뒤 머지는 사용자가 직접 한다. `dev → main` 적용 범위는 팀 검토 기준을 따른다.

구현한다면 필요한 파일의 예시는 `.github/workflows/ci.yml`, `.github/workflows/ai-review.yml`, `.github/ai-review/prompt.md`, `.github/ai-review/result.schema.json`이다.
승인 정책은 보호된 기준 브랜치의 코드로 실행하도록 설계해야 한다. 이 문서에서는 해당 파일을 만들지 않았다.

## 면접 포인트

- **AI 점수만으로 머지를 결정하면 왜 문제가 되는가?** 평가가 비결정적이고, 점수로 결함이 상쇄되거나 변경 일부를 놓칠 수 있다. 실제 CI, 중대 결함 차단, 검토 커밋 일치, 보호 규칙을 함께 사용한다.
- **리뷰와 승인 권한을 왜 분리하는가?** 리뷰 입력은 PR 작성자가 통제할 수 있다. 입력 속 명령이 승인 권한까지 행사하지 못하도록 읽기·평가와 쓰기·승인 경계를 분리한다.

## 참고

확인일은 2026-10-15다. 예시는 설계 안내이며 실제 PR에서의 동작은 미검증이다.

- [CodeRabbit Quickstart](https://docs.coderabbit.ai/getting-started/quickstart)
- [CodeRabbit 설정 참조](https://docs.coderabbit.ai/reference/configuration)
- [Codex GitHub Action](https://learn.chatgpt.com/docs/github-action)
- [Codex GitHub 리뷰 연동](https://learn.chatgpt.com/docs/third-party/github)
- [Codex 인증과 API 과금](https://learn.chatgpt.com/docs/auth)
- [GitHub Actions Secret 등록](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [GitHub Actions 이벤트](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)
- [GitHub 리뷰 생성 API](https://docs.github.com/en/rest/pulls/reviews#create-a-review-for-a-pull-request)
- [GitHub Actions 저장소 권한 설정](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository)
- [GitHub 브랜치 보호](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
- [GitHub Auto-merge](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/automatically-merging-a-pull-request)
