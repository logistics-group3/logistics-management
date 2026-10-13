---
name: learning-note
description: 사용자가 개념, 원리, 방법, 비교를 물었을 때 답을 docs/learning/qna/에 날짜별 학습 노트로 저장한다. 작업 지시나 단순 상태 확인에는 쓰지 않는다.
---

# learning-note

## 언제
- 사용자가 지식이나 방법을 묻는 질문을 했을 때 (판단 기준: `docs/agents/learning-guide.md` 2절)

## 절차
1. 채팅에서 핵심 답을 짧게 먼저 준다.
2. `docs/learning/qna/`에서 같은 주제의 노트를 찾는다. 있으면 그 노트를 보강하고 `updated`를 갱신한다.
3. 없으면 `docs/learning/qna/YYYY-MM-DD-<영문-kebab-slug>.md`를 만든다. 날짜는 질문한 날이다.
4. `docs/learning/README.md` 표에 한 줄을 추가하거나 갱신한다. 최신 노트가 위로 오게 한다.
5. 채팅 마지막 줄에 `노트 저장: <경로>`를 남긴다.

## 노트 형식
```markdown
---
title: <질문을 한 문장 제목으로>
date: YYYY-MM-DD
updated: YYYY-MM-DD
asker: <git config user.name>
level: 기초 | 중급 | 고급
tags: [spring, gradle, ...]
---

# <title>

## 질문
<사용자 질문 원문. 필요하면 맥락을 한 줄 덧붙인다>

## 짧은 답
<3~5줄 결론>

## 자세히
<원리, 동작 방식, 대안 비교. 코드 예시>

## 이 프로젝트에서는
<실제 파일 경로와 적용 방식. 없으면 생략>

## 면접 포인트
- <예상 질문과 답변 요지>

## 참고
- <공식 문서, 확인한 실행 결과>
```

## 규칙
- 대화 맥락 없이 독립적으로 읽히게 쓴다. 블로그 초안으로 그대로 옮겨 쓸 수 있어야 한다.
- 한국어 평서체로 쓴다. 이모지와 AI 말투를 쓰지 않는다.
- 버전에 따라 달라지는 내용은 버전과 근거를 적는다.
