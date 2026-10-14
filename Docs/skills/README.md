# AI 학습·구현 skill 가이드

> 이 프로젝트는 **학습이 주 목적**이다. AI(Claude Code)에게 일을 맡기더라도 "무엇을, 왜 이렇게 만들었는지" 팀원이 설명할 수 있도록 공용 skill을 둔다.
> 이 디렉터리는 **사람이 읽는 사용 설명서**다. AI가 따르는 절차 원본은 `.claude/skills/{이름}/SKILL.md`이고, 둘이 어긋나면 `SKILL.md`가 우선한다.

## skill이란
- Claude Code가 **필요할 때만 불러오는 작업 절차**다. 항상 읽히는 `CLAUDE.md`(규칙)와 달리, 호출했을 때만 동작한다.
- 저장소의 `.claude/skills/`에 있으므로 이 저장소를 Claude Code로 열면 팀원 누구나 바로 쓸 수 있다 (추가·수정 후에는 Claude Code 재시작).
- 호출 방법
  - **직접 호출**: 프롬프트에 `/impl-service hub-service H-06`처럼 입력
  - **자동 선택**: "H-06 구현해줘", "이거 왜 이렇게 돼?"처럼 요청하면 AI가 맞는 skill을 고른다 (`learn-note`는 직접 호출만)

## 분류와 목록

| 분류 | skill | 한 줄 설명 | 남는 기록 |
| --- | --- | --- | --- |
| 구현 `impl-` | [impl-service](impl-service.md) | AI가 기능을 구현하되 **계층마다 멈추고 설명**한다 | `Docs/team/구현-노트/{서비스}.md` |
| 학습 `learn-` | [learn-explain](learn-explain.md) | 개념·코드·설계 이유를 **우리 코드에 연결해** 설명한다 | 없음 (대화) |
| 학습 `learn-` | [learn-review](learn-review.md) | 직접 짠 코드를 **힌트부터 단계적으로** 리뷰한다 | 없음 (대화) |
| 학습 `learn-` | [learn-note](learn-note.md) | 하루 동안 배운 것과 해결한 문제를 정리한다 | `Docs/team/학습-노트/{이름}.md`, `Docs/07-트러블슈팅.md` |

## 언제 무엇을 쓰나

```mermaid
flowchart LR
    start["작업 시작"] --> q{"누가 코드를 짜나?"}
    q -->|"AI에게 맡김"| impl["/impl-service<br/>계층별 설명 + 구현 노트"]
    q -->|"내가 직접"| self["직접 구현"]
    self --> review["/learn-review<br/>커밋 전 힌트 리뷰"]
    impl --> any["이해 안 되는 부분"]
    review --> any
    any --> explain["/learn-explain"]
    impl --> done["하루 마무리"]
    review --> done
    done --> note["/learn-note<br/>학습 노트 + 트러블슈팅"]
```

| 상황 | 쓰는 skill |
| --- | --- |
| API·엔티티·Feign 구현을 AI와 함께 진행 | `impl-service` |
| 처음 보는 개념, 남이 짠 코드, 설계 이유가 궁금함 | `learn-explain` |
| 직접 짠 코드를 커밋하기 전에 점검 | `learn-review` |
| 오늘 작업을 마치며 정리 | `learn-note` |

## 공통 원칙
- skill은 **절차**만 담는다. 규칙 본문은 루트·모듈 `CLAUDE.md`에 있고 skill은 그 위치를 가리킨다.
- skill을 써도 협업 규칙은 그대로다. 다른 서비스 코드는 고치지 않고, 계약 변경은 상대 `agent.md`에 `OPEN`으로 요청한다.
- AI가 만든 결과물(코드, 노트)은 **본인이 읽고 설명할 수 있을 때** 커밋한다.

## skill 추가·수정 방법
1. `.claude/skills/{분류}-{이름}/SKILL.md` 작성 (분류 접두어: 학습 `learn-`, 구현 `impl-`)
   - 맨 위 frontmatter: `name`, `description`(AI가 언제 쓸지 판단하는 문장), 필요하면 `argument-hint`, `disable-model-invocation: true`(직접 호출만)
2. 이 디렉터리에 `{이름}.md` 사용 설명서를 추가하고 위 목록 표에 한 줄 추가
3. `common/agent.md` 로그에 기록 (`.claude/`는 모든 팀원에게 영향을 주는 공유 파일)
4. `SKILL.md`의 동작을 바꾸면 이 디렉터리의 설명서도 함께 고친다
