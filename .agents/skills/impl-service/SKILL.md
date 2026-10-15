---
name: impl-service
description: 서비스 기능(외부·내부 API, 엔티티, FeignClient)을 구현할 때 쓴다. 계층마다 멈추며 구현하고 핵심을 설명한 뒤, 처리 과정과 핵심을 서비스 구현 노트로 남긴다. "H-02 구현해줘", "배송 엔티티 만들어줘", "order에 주문 취소 API 추가해줘" 같은 요청에 쓴다.
argument-hint: "[서비스명] [API ID 또는 기능, 예: hub-service H-02]"
---

# impl-service: 서비스 기능 구현 (학습형)

이 프로젝트의 목표는 완성과 학습이다. 사용자가 "무엇을, 왜 이렇게 만들었는지" 설명할 수 있게 구현한다. 규칙 본문은 이 파일에 없다. 여기서는 순서와 결과물 형식만 정한다. 원본은 KMH 브랜치의 같은 스킬이다.

## 0. 진행 방식
- 기본은 계층마다 멈추고 설명한다.
- 사용자가 어떤 주제의 설명을 그만해도 된다고 하면(예: "Entity 설명은 그만") 그 주제는 이 대화에서 다시 설명하지 않고, 구현 노트에 `설명 생략: {주제}`로 남긴다.
- 사용자가 "한 번에 끝까지"를 요청하면 멈추지 않고 구현한 뒤 계층별 설명을 한꺼번에 한다.

## 1. 사전 확인 (코드 작성 전)
1. 루트 `AGENTS.md`, 대상 모듈의 `{module}/AGENTS.md`, `docs/services/{service}/README.md`를 읽는다.
2. API 명세와 테이블은 SA(`docs/SA/sparta-logistics-sa.md`) 6장·5장에서 확인한다. 서비스 결정(`docs/services/{service}/decisions/`)이 있으면 함께 본다.
3. 협업 로그에 이 작업과 관련된 `OPEN` 항목이 있으면 멈추고 사용자에게 결정을 묻는다.
4. 다른 서비스의 내부 API 추가·변경이 필요하면 그 서비스 코드를 고치지 않는다. 그 서비스 `docs/services/{service}/README.md` 협업 로그에 `OPEN` 항목을 남기고 멈춘다(형식은 `docs/services/README.md`).

## 2. 구현 계획을 보여 주고 확인받는다
- API 요약: 경로, 권한, 요청, 응답, 에러 코드
- 계층별로 만들거나 고칠 파일
- 호출하는 내부 API와 재시도 여부

## 3. 계층별 구현 (계층이 끝날 때마다 멈춘다)
순서는 마이그레이션·domain → infrastructure → application → presentation이다. 계층마다 해당 규칙 파일(`docs/agents/rules/`)을 따른다.

| 계층 | 주로 만드는 것 |
|---|---|
| 마이그레이션·domain | `V{n}__*.sql`, Entity(상태 전이 메서드), enum, Repository 인터페이스, `{Service}ErrorCode` |
| infrastructure | Spring Data·QueryDSL 구현, FeignClient |
| application | Service, Command·Result, 트랜잭션 경계, 소유자 검증 |
| presentation | Controller, Request·Response, `@RequireRole`, `@CurrentUser` |

계층을 마치면 아래 형식으로 설명하고 사용자의 "다음"을 기다린다.
```
### {계층} 완료
- 만든 것: 파일과 역할 (경로)
- 핵심: 왜 이렇게 만들었나
- 연결된 규칙: docs/agents/rules/{파일}.md, SA {절}
- 생각해 볼 질문: 1개 (정답은 요청하면 알려 준다)
```

## 4. 확인
- `./gradlew :{module}:test`, 필요하면 `script/run.sh {module}` 후 Gateway(8080)로 정상·에러 케이스를 각각 1개 이상 호출한다. 결과(상태 코드, 응답)를 보여 준다.
- 에러 코드를 추가했으면 `docs/error-codes.md`를 고치고 `script/error-codes-check.sh`를 실행한다.
- 확인하지 못한 부분은 "미확인"이라고 적는다.

## 5. 기록 (사용자 동의를 기다리지 않고 쓰고, 경로를 알린다)
1. `docs/services/{service}/README.md`의 제공 API 상태(예정 → 구현)와 구현 현황을 고친다. 계약이 SA와 다르면 "SA와 다른 점"에 적는다.
2. 구현 노트를 `docs/services/{service}/notes.md` 맨 아래에 추가한다(없으면 `# {service} 구현 노트`로 만든다).
   ```
   ## {API ID} {이름} (YYYY-MM-DD)
   ### 처리 과정
   - 계층별로 무엇을 어떤 순서로 만들었나 (파일, 요청 흐름)
   ### 핵심
   - 왜 이렇게 했나 (3~5개)
   ### 확인 질문
   - 직접 답해 볼 질문 2~3개
   ### 메모
   - 설명 생략: {주제} (있을 때만)
   ```
3. 막혔다가 해결한 문제가 있으면 `troubleshooting` 스킬로 기록한다.
4. 답 끝에 `기록: <경로>`를 남긴다. 사람은 PR에서 기록을 검토한다.
