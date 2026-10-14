---
name: impl-service
description: 서비스 기능(외부·내부 API, 엔티티, FeignClient)을 구현할 때 사용한다. 정해진 순서로 계층별로 멈추며 구현·설명하고, 마지막에 처리 과정과 핵심을 구현 노트로 남긴다. "H-06 구현해줘", "배송 엔티티 만들어줘" 같은 요청에 쓴다.
argument-hint: "[서비스명] [API ID 또는 기능, 예: hub-service H-06]"
---

# 서비스 구현 절차 (학습형)

이 프로젝트는 **학습이 주 목적**이다. 구현 자체보다 지시자가 "무엇을, 왜 이렇게 만들었는지" 설명할 수 있게 되는 것이 목표다.
규칙 본문은 이 파일에 없다. 루트 `CLAUDE.md`와 모듈 `CLAUDE.md`가 기준이며, 여기서는 **순서와 결과물 형식**만 정한다.

## 0. 진행 방식
- **기본: 계층별로 멈추고 설명한다.** 같은 설명이 반복되더라도 생략하지 않는다.
- **설명 생략**: 지시자가 특정 주제를 더 설명하지 않아도 된다고 하면(예: "Entity 설명은 그만", "소프트 삭제는 알아") 그 **주제**는 이후 계층과 이후 API에서도 설명하지 않는다.
  - 생략은 주제 단위다. 다른 주제는 계속 설명한다.
  - 생략한 주제는 구현 노트에 `설명 생략: {주제}`로 한 줄 남긴다.
  - 범위는 이 대화 안에서만 유지된다. 새 대화에서는 다시 설명한다.
- 지시자가 "한 번에 끝까지"를 요청하면 멈추지 않고 구현한 뒤 계층별 설명을 한꺼번에 한다.

## 1. 사전 확인 (코드 작성 전)
1. 루트 `CLAUDE.md`, `{서비스}/CLAUDE.md`, `{서비스}/agent.md`를 읽는다.
2. 대상 API 명세는 `Docs/03-API.md`, 테이블은 `Docs/02-ERD.md`에서 확인한다 (최종 근거: `Docs/reference/SA_Document.md`).
3. `agent.md` 로그에 이 작업과 관련된 `OPEN` 항목이 있으면 **멈추고 지시자에게 결정을 묻는다.** 임의로 정하지 않는다.
4. 다른 서비스의 내부 API 추가·변경이 필요하면 **그 서비스의 코드는 건드리지 않는다.** 그 서비스 `agent.md` 로그 맨 아래에 양식대로 `OPEN` 항목을 추가하고 멈춘다.
   ```
   ### {상대서비스}-{번호} 제목
   - 상태: OPEN
   - 작성: {이름} / YYYY-MM-DD
   - 관련: (API ID, 요청하는 서비스, 파일)
   - 내용: 무엇이 왜 필요한지, 제안하는 계약(경로·요청·응답·상태 코드)
   - 결정:
   ```

## 2. 구현 계획 제시 → 확인받기
- API 요약: 경로·권한·요청·응답·에러 코드
- 계층별로 만들거나 수정할 파일 목록
- 의존하는 내부 API(Feign)와 타임아웃·재시도 여부
- 지시자가 확인하면 3단계로 넘어간다.

## 3. 계층별 구현 (각 계층이 끝날 때마다 멈춤)
순서: **domain → infrastructure → application → presentation**

| 계층 | 주로 만드는 것 |
| --- | --- |
| domain | Entity(상태 전이 메서드 포함), enum, VO, Repository 인터페이스, 도메인 ErrorCode |
| infrastructure | JPA·QueryDSL 구현, FeignClient(+ 타임아웃·재시도 설정), `db/indexes.sql` |
| application | Service(UseCase), command/result DTO, 트랜잭션 경계, 소유자 검증 |
| presentation | Controller, request/response DTO, `@MasterOnly`, `@CurrentUser` |

각 계층을 마치면 아래 형식으로 설명하고 **지시자의 "다음" 신호를 기다린다.**
```
### {계층} 완료
- 만든 것: 파일과 역할 (경로:줄)
- 핵심: 왜 이렇게 만들었나 (생략 요청된 주제는 제외)
- 연결된 규칙: CLAUDE.md §..., {서비스}/CLAUDE.md §...
- 생각해 볼 질문: 1개 (정답은 요청 시)
```

## 4. 핵심 체크리스트 (구현 중 확인, 규칙 본문은 CLAUDE.md)
- [ ] Entity: `extends BaseEntity`, `@Table(name = "p_...")`, `@SQLRestriction("deleted_at IS NULL")`, UUID PK, 삭제는 `softDelete` (루트 §8-1)
- [ ] 상태 전이 검증은 Entity 메서드, 위반 시 409 `INVALID_STATUS_TRANSITION` (루트 §7)
- [ ] 유니크 제약은 `db/indexes.sql` 부분 인덱스 (루트 §8-1)
- [ ] 외부 API는 `ApiResponse`/`PageResponse`, 내부 API는 DTO 그대로 (루트 §8-1)
- [ ] 목록 조회는 `Pageables.normalize(pageable)` (루트 §8-1)
- [ ] MASTER 전용은 `@MasterOnly`, 소유자 검증은 서비스 계층 (루트 §8-1)
- [ ] 다른 서비스 데이터는 UUID 참조 + Feign 검증, FK·조인 금지 (루트 §3-3)
- [ ] orderId 기준 API는 멱등 (루트 §5), Feign 재시도는 GET·멱등 API만 (루트 §9)
- [ ] Feign 타임아웃은 바깥 > 안쪽 (루트 §9)
- [ ] Entity를 Controller 밖으로 노출하지 않는다 (루트 §8)
- [ ] 서비스 고유 규칙: `{서비스}/CLAUDE.md` 전체

## 5. 동작 확인
- 빌드: 호스트에 JDK가 있으면 `./gradlew :{서비스}:build`, 없으면 `docker compose up -d --build {서비스}`
- 확인: Swagger(`http://localhost:8080/swagger-ui.html`) 또는 curl로 정상·에러 케이스 각각 1개 이상. 결과(상태 코드·응답)를 지시자에게 보여 준다.
- 확인하지 못한 부분이 있으면 그렇다고 명시한다.

## 6. 마무리
1. `{서비스}/agent.md` §5 구현 현황을 체크한다. 작업 중 내린 결정은 로그에 남긴다 (`DECIDED`/`DONE`).
2. **구현 노트**를 `Docs/team/구현-노트/{서비스}.md` 맨 아래에 추가한다 (파일이 없으면 만든다).
   ```
   ## {API ID} {이름} (YYYY-MM-DD)
   ### 처리 과정
   - 계층별로 무엇을 어떤 순서로 만들었나 (파일, 요청 흐름)
   ### 핵심
   - 왜 이렇게 했나, 발표에서 설명할 포인트 (3~5개)
   ### 확인 질문
   - 직접 답해 볼 질문 2~3개
   ### 메모
   - 설명 생략: {주제} (있을 때만)
   ```
3. 구현 중 막혔다가 해결한 문제가 있으면 `Docs/07-트러블슈팅.md`에 TS 항목으로 추가한다.
