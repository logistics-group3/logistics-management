# 배송 도메인 설계 대화 기록

> 기간: 2026-10-14 · 담당: 한얼 · 대상 문서: [`delivery.md`](delivery.md)
> 배송 설계를 Claude와 이야기하며 정한 것을, **왜 이야기했나 → 어떻게 알아갔나 → 결론** 순서로 남긴다.
> 결론이 설계 문서와 다르면 설계 문서(`delivery.md`)가 기준이다.
> 주의: 1~9는 SA v2.0 이전 문서(Notion 설계 문서 페이지)를 보고 나눈 대화다. **10번에서 SA v2.0을 확인하고 많은 결론이 바뀌었다.** 각 항목의 "→ 10" 표시를 볼 것.

## 한눈에 보기

| # | 주제 | 결론 |
|---|---|---|
| 1 | 배송 설계 문서 만들기 | 상태·배정·아키텍처·API·ERD·권한을 한 문서(`delivery.md`)로 |
| 2 | API 없이 바뀌는 상태? | 배송 생성은 내부 API(I-D1)로 일어나는 전이. 표에 "(생성)"으로 표기 |
| 3 | 담당자 배정 방식 | 내 제안(명부 테이블 + 유형 + 순번)을 버리고 **SA 규칙** 채택 → 10에서 구간 단위로 확정 |
| 4 | user의 HUB/COMPANY는 뭔가 | 소속 종류일 뿐 배정 기준이 아님 → **배송 용어(유비쿼터스 언어)** 를 따로 정함 |
| 5 | `hub_id`와 배송담당자 | `is_delivery_manager = true` **그리고** `hub_id` 있음 = 배정 대상. 없으면 배송 못 함 |
| 6 | user-service에 API가 있나 | ~~단건 조회만 있음~~ → 10: SA에 I-14(허브별 배송담당자)·I-01 `hubId` 있음 |
| 7 | 버튼(API) 2개의 의도 | ~~양방향 구현~~ → 10: SA v2.0에서 구간 상태 API(D-05) 하나로 통합, 질문 자체가 사라짐 |
| 8 | 주문 기준 취소(I-D3) | 고아 배송 정리용 → 10: SA의 I-13 `DELETE /internal/deliveries/orders/{orderId}`가 같은 역할 |
| 9 | 배정 정보 저장 | ~~스냅샷~~ → 10: SA는 구간 `manager_id`만, I-14도 이름을 안 줌 → SA대로 |
| 10 | SA v2.0 확인 | 배송 3구간(집하·허브 이동·업체 배송), 구간 상태만 바꾸면 배송 상태가 따라감. `delivery.md`를 SA v2.0으로 다시 씀 |
| 11 | 작업 관리와 기록 방식 | 태스크(T01) 단위 브랜치 + 커밋 단위 기록, `delivery-service/docs/`에 규칙·도메인 문서, 결정은 즉시 기록하고 개인 훅을 안전망으로 |
| 12 | 배송 구간 모델 확정 | SA v2.0 3구간 채택, ADR 0001(2구간) 대체 → [ADR 0003](../../../docs/services/delivery/decisions/0003-delivery-three-legs-sa-v2.md) |
| 13 | V1 DDL의 제약과 인덱스 범위 | 정합성 제약(PK·UNIQUE·FK)과 SA 5.6 인덱스만. 방어용 CHECK와 부분 인덱스는 기능 완성 후 실행계획 보고 추가 |
| 14 | 테스트 기준 | 없으면 잡지 못하는 버그가 있는 테스트만. 상태 전이·멱등·배정·Saga 분기·권한 필터 중심, 단순 확인 테스트는 쓰지 않음 |
| 15 | enum 컬럼 타입 | PostgreSQL enum 타입을 검토하고 구현까지 했다가 팀 규칙(VARCHAR + CHECK)으로 되돌림 |
| 16 | 구현하며 정한 것(D-01~05, I-09~13) | cascade PERSIST만, 생성은 트랜잭션 밖 오케스트레이션 + 유니크 위반 시 기존 배송 반환, 수령인 404면 Slack ID NULL, 원복은 취소 상태일 때만, 에러 코드는 `DeliveryErrorCode` |
| 17 | 조회와 명령 저장소 분리 | 조회는 QueryDSL `@QueryProjection` DTO를 바로 반환(Result로 다시 감싸지 않음), 명령은 JPA로 Entity. 조회 DTO가 application에 있어 팀 레이어 규칙과 다름 |
| 18 | 1:1 복사 Response 없애기 | Result 하나면 그대로 응답, 여러 Result를 합칠 때만 `Response`(`DeliveryResponse`). delivery만의 코드 규칙으로 `delivery-service/AGENTS.md`에 둠 |
| 19 | 주문 DM용 수신자 조회 | I-15로 활성 구간 담당자만 제공. 로컬 구현·담당자 리뷰 대기(ADR 0016) |
| 20 | 배송담당자 지정 해제 후 변경 권한 | D-03·D-05는 현재 자격과 구간 배정을 함께 검사한다. #13 사용자 확인 범위의 로컬 수정이다. |
| 24 | 부하 테스트 방법과 테스트 전용 API | k6로 기준선 → 하나씩 변경 → 재측정. 배송 생성만 재려고 `POST /api/v1/deliveries/load-test`(기본 꺼짐, MASTER, 문서 제외)를 두고 덧붙임 compose 파일로만 켠다 |
| 25 | 로컬 부하 측정 방식 | 예열 2회 후 측정, 측정 사이에는 배송 테이블만 비움(`--reset` 금지), 본 측정 속도 초당 10+10, 추적 100% 고정, 서버 지표 자동 수집 |
| 26 | user 장애 격리 | user·hub 호출 read 2초·connect 1초, 연결 실패만 1번 재시도, user 호출에 벌크헤드 10·서킷 브레이커, I-01은 user 장애 때 Slack ID 없이 진행. 네 가지를 한 번에 넣고 S1로 잰다 |

---

## 1. 배송 설계 문서 만들기

**왜** — 공통 인프라 작업을 끝내고 배송 도메인을 맡았다. 구현 전에 order·user·hub 담당과 맞출 것이 많아서, 말로 하기 전에 한 장으로 볼 수 있는 문서가 필요했다.

**어떻게** — 요구사항을 6개로 나눠 요청했다: ① 상태 흐름 ② 배송 도메인의 역할 ③ 구간별 담당자 자동 배정 ④ 요청·응답 아키텍처 ⑤ API·ERD·권한 ⑥ 한눈에 보이는 구성. Notion 설계 문서(03 ERD · 04 API · 05 서비스 흐름)를 읽고 초안을 만들었다.

**결론** — `delivery.md` 초안. SA에 있는 것은 [SA], 내 제안은 [제안], 정해야 할 것은 [미정]으로 구분해서 합의할 곳이 바로 보이게 했다.

## 2. API 없이 바뀌는 상태가 있나

**왜** — 전이표 첫 줄이 "(없음) → `HUB_WAITING`"으로 되어 있어서, 누가 어떤 API로 바꾸는지 없는 상태 변화처럼 보였다.

**어떻게** — 첫 상태는 사용자가 버튼으로 바꾸는 게 아니라, order가 주문을 만들면서 내부 API `POST /internal/deliveries`로 배송을 **생성**할 때 정해진다. 출발 = 목적지 허브면 바로 `DEST_HUB_ARRIVED`로 시작한다.

**결론** — 표기를 "(생성) → …"으로 바꿨다. 상태는 한 방향, 한 단계씩만 움직인다.

## 3. 담당자 배정 방식 — 내 제안 vs SA

**왜** — 배송이 만들어질 때 담당자를 누가 어떻게 정하는지가 SA에 없다고 생각했다. 매번 MASTER가 손으로 지정하면 주문이 들어올 때마다 사람 손이 필요하다.

**어떻게**
1. 처음 제안: 배송 서비스에 **배송담당자 명부 테이블**(`p_delivery_managers`)을 두고, 담당자를 `HUB`(허브 이동)/`COMPANY`(업체 배송) 유형으로 나눠, "가장 오래 쉰 사람"(마지막 배정 시각) 순으로 배정. 후보가 없으면 담당자 없이 배송을 만들고 MASTER가 나중에 지정.
2. 명부가 왜 필요한지, 허브 담당자와 업체 담당자가 왜 나뉘는지 질문이 나왔다.
3. 내가 "SA에는 집하 단계가 없다"고 했는데, 확인해 보니 **SA에 배정 규칙이 이미 있었다**:
   - 배송담당자는 user에 담당 허브(`hub_id`)를 가진다
   - 구간 **출발 허브**의 배송담당자 중 진행 중 건수가 가장 적은 사람에게 배정
   - 후보가 없으면 404로 **주문 실패**
   - MASTER가 재배정
   - 집하 구간이 있다
4. 내 제안의 `HUB`/`COMPANY` 유형은 SA가 아니라 내가 만든 것이었다.

**결론** — SA 규칙을 그대로 쓴다. 명부 테이블과 명부 관리 API(D-09~12)는 지웠다. 담당자 정보의 원본은 user-service다. 후보가 없으면 배송을 만들지 않고 주문을 실패시킨다(담당자 없는 배송이 생기지 않음). "집하" 구간의 상태·담당 정의는 아직 확인하지 못해서 [미정]로 남겼다.

> 배운 점: 제안하기 전에 SA 원문에 같은 규칙이 있는지 먼저 찾는다. 내가 "없다"고 말한 것이 틀렸고, 사용자가 SA에서 찾아서 바로잡았다.

## 4. user의 HUB/COMPANY는 뭔가 → 배송 용어 정하기

**왜** — user 테이블에 `affiliation_type`(`COMPANY`/`HUB`)이 있는데, 내 제안의 담당자 유형(`HUB`/`COMPANY`)과 이름이 같아서 같은 것인지 헷갈렸다.

**어떻게** — user의 `affiliation_type`·`affiliation_name`은 가입할 때 적는 "어디 소속인가(업체명·허브명)"일 뿐 배정 기준이 아니다. 배송 쪽 유형은 내가 만든 말이었다. 같은 단어가 서비스마다 다른 뜻으로 쓰이면 코드와 대화에서 계속 섞인다. 배송 관점에서 사람을 다시 정리했다:
- 배송담당자 = 배정될 수 있는 사람 (사람의 종류)
- 구간 담당자 / 업체 배송 담당자 = 배정된 **자리** (같은 사람이 둘 다 될 수 있음)

**결론** — `delivery.md` §2에 **배송 용어(유비쿼터스 언어)** 를 추가했다: 용어·코드명 표, 다른 서비스 용어 → 배송 용어 변환표(Feign DTO에서 변환), 쓰지 않는 말("허브/업체 담당자 유형", "업체 담당자", "배송 경로", "담당자 명부").

## 5. `hub_id`와 배송담당자 여부

**왜** — 배정 규칙이 "담당 허브"를 기준으로 하는데, user에 어떤 컬럼이 있는지 정확히 맞춰야 했다.

**어떻게**
- SA에서 user에 `hub_id`(NULL 허용) 컬럼을 두기로 합의했다.
- 배송담당자는 role(`DELIVERY_MANAGER`)이 아니라 boolean(`is_delivery_manager`)이다. (Notion 설계 문서 03 ERD에는 role로 적혀 있어 헷갈렸지만, 레포·SA는 boolean)
- 당시 개인 저장소의 `doc/sql/01-user.sql`·`User` 엔티티에는 아직 `hub_id`가 없다.

**결론** — 배정 대상 = `is_delivery_manager = true` **그리고** `hub_id IS NOT NULL`. `hub_id`가 없으면 배정할 수 없고, 그 허브에 배정할 사람이 없으면 **배송을 만들 수 없다**(404 `DELIVERY_MANAGER_NOT_FOUND` → 주문 실패). `hub_id` 컬럼 반영은 user 담당에게 요청.

## 6. user-service에 배송담당자 목록 API가 있나

**왜** — 배정하려면 "허브 X의 배송담당자 목록"을 user에서 받아야 한다.

**어떻게** — 당시 개인 저장소 API 문서(`doc/design/04-api.md`)와 Notion 04 API를 확인했다. user의 내부 API는 단건 조회 `GET /internal/users/{userId}`(I-01, 응답: userId·username·role·isDeliveryManager·slackId) 하나뿐이다. 외부 `GET /api/v1/users`는 MASTER 전용이라, 주문자(USER) 헤더로 들어오는 배송 생성 흐름에서는 403이 난다. (팀 SA Master 원문은 이 대화에서 직접 열어 보지 못했다.)

**결론** — user 담당에게 요청할 것 2가지 (`delivery.md` §4-5):
- [제안] `GET /internal/users/delivery-managers?hubId=` → `[{ userId, name, slackId }]`
- I-01 응답에 `hubId`, `name` 추가 (재배정 검증용)
SA에 같은 용도의 내부 API가 이미 있으면 그 이름을 따른다.

## 7. 같은 상태를 바꾸는 버튼(API)이 2개인 이유

**왜** — SA 05에서 허브 이동 상태를 **구간 API**(`PATCH …/routes/{routeId}` `MOVING`)로도, **배송 API**(`PATCH /deliveries/{id}` `HUB_MOVING`)로도 바꿀 수 있고, 서로 맞춰진다고 되어 있다. 처음엔 "구간 → 배송 한 방향만" 쓰자고 제안했는데, 그러려면 SA가 왜 2개를 뒀는지 알아야 했다.

**어떻게** — SA에서 근거를 찾아 추정했다:
- 실제 거리·시간은 구간 API에만 있다 → 구간 API는 현장 기록용
- `OUT_FOR_DELIVERY`/`DELIVERED`는 배송 API에만 있다 → 업체 배송은 구간 기록이 없으니 배송 API가 꼭 필요
- "구간을 바꾸면 배송이, 배송을 바꾸면 구간이 맞춰진다" → 두 입구를 모두 인정하고 애그리거트가 동기화. 구간이 1개(P2P)라 배송 API로 바꿔도 어느 구간인지 모호하지 않다
- 문제는 구간이 2개 이상일 때뿐이다

**결론** — 의도는 SA 작성자에게 확인한다. 이번 구현은 **구간이 항상 1개**이므로 SA대로 두 API 모두 열고 양방향으로 맞춘다. 배송 API로 `DEST_HUB_ARRIVED`를 바꿀 때도 실제 거리·시간을 받는다([제안]). 구간 없는 배송에 `HUB_MOVING` 요청은 409. API URL도 내가 나눴던 `/status`·`/manager` 대신 SA 04 그대로(`PATCH /deliveries/{id}`, `PATCH /deliveries/{id}/routes/{routeId}`)로 되돌렸다.

## 8. 주문 기준 취소(I-D3)는 왜 필요한가

**왜** — 배송 ID로 취소하는 I-D2가 있는데 주문 ID로 취소하는 API를 왜 또 두는지 질문이 나왔다.

**어떻게** — order가 배송 생성을 호출했는데 **응답을 받기 전에 타임아웃**이 나면, order는 배송 ID를 모른다. 그런데 배송은 이미 만들어졌을 수 있다(고아 배송). 이때 order가 아는 건 주문 ID뿐이다. SA "6. 개선하기"에도 "배송도 orderId로 취소할 수 있게 해서 고아 배송을 정리"가 있다.

**결론** — I-D3 `POST /internal/deliveries/by-order/{orderId}/cancel` 유지 (멱등). order 담당과 맞출 항목.

## 9. 배정 정보를 어떻게 남기나

**왜** — 명부 테이블을 지우면 "누가, 언제, 누구에게 배정했나"를 어디에 남길지 정해야 했다. 목록 화면마다 user를 불러 이름을 받아오는 것도 부담이다.

**어떻게** — 배송지·수령인처럼 **배정 시점 값을 복사**(스냅샷)하는 방식을 제안했고 동의했다.

**결론** — 배송·구간 모두에 `manager_user_id`, `manager_name`, `manager_slack_id`, `assigned_at`, `assigned_by`(`SYSTEM` 또는 MASTER username). 재배정 이력 테이블은 필요해지면 추가([미정]).

---

## 10. SA Master v2.0을 확인하니 — 설계를 다시 맞춤

**왜** — 사용자가 "SA 문서에는 I-01 응답에 `hubId`가 이미 있다"며 SA Master 링크를 줬다. 6번에서 "I-01에 `hubId`가 없다"고 한 것과 달랐다.

**어떻게** — Notion 연결로는 팀 SA Master가 열리지 않아 앱 브라우저로 원문을 읽었다. 그동안 본 문서는 **SA Master가 아니라** 레포 문서를 옮긴 "스파르타 물류 MSA — 설계 문서"(10/12 기준)였다. SA Master는 10/13 **v2.0**(멘토 피드백 반영)에서 배송을 크게 바꿨다:
- 배송을 **집하(PICKUP) · 허브 이동(HUB_TRANSFER) · 업체 배송(LAST_MILE) 3구간**으로 기록. 같은 허브면 허브 이동 생략
- 배송 상태에 `PICKUP_WAITING`, `PICKING_UP` 추가. **배송 상태는 구간 상태를 바꾸면 자동으로 따라간다** (직접 바꾸는 API 없음)
- 구간 담당 허브 = 구간 출발지를 관리하는 허브 (집하·허브 이동: 공급업체 허브, 업체 배송: 요청업체 허브)
- 자동 배정: I-14로 후보를 받고 담당 구간(`WAITING`·`MOVING`) 수 최소, 동점이면 userId 순. 없으면 404로 주문 실패
- API: D-03 배송지·수령인 수정, D-04 `POST …/manager` 재배정, D-05 `PATCH …/status` 구간 상태 변경
- 내부: I-09 생성, I-10 조회, I-11 취소(집하 전만), I-12 취소 원복, I-13 orderId 기준 삭제
- 에러: `INVALID_STATUS_TRANSITION`, `MANAGER_HUB_MISMATCH`, `DELIVERY_MANAGER_NOT_FOUND` (공통 코드)
- I-01 응답: `userId, username, role, isDeliveryManager, hubId, slackId`

**결론** — `delivery.md`를 SA v2.0 기준으로 다시 썼다.
- 이전 결론 중 바뀐 것: 버튼 2개 문제(7)는 SA가 이미 구간 API 하나로 통합해서 사라졌다. 배정 스냅샷(9)은 SA에 없고 I-14가 이름도 주지 않아 뺐다. 주문 기준 취소(8)는 I-13이 맡는다. user API 요청(6)은 SA에 이미 있으니 **레포 구현(`hub_id`, I-01 `hubId`, I-14)만 user 담당에게 확인**하면 된다
- 그대로인 것: 배정 대상(`is_delivery_manager` + `hub_id`), 담당자 없으면 주문 실패, 유비쿼터스 언어의 필요성 (용어는 v2.0에 맞게 고침: "배송 경로 기록" → "배송 구간", 사람은 "소속 허브", 구간은 "구간 담당 허브")
- 앞서 "D-03·D-05는 누구와 논의?"에 답할 때 D-03·D-05를 내 옛 번호(배송 상태 변경·구간 목록)로 답했다. SA v2.0 기준 D-03(배송지·수령인 수정)·D-05(구간 상태 변경)도 배송 안에서 끝나는 API라 **다른 서비스와 논의할 것은 없다**는 결론은 같다

> 배운 점: 기준 문서의 **이름과 버전**부터 확인한다. 비슷한 제목의 설계 문서가 여러 개일 때 최신 SA(버전·변경 이력)를 먼저 찾지 않아, 이미 해결된 문제(배정 규칙·버튼 2개·고아 배송)를 다시 설계했다.

## 11. 작업 관리와 기록 방식 (2026-10-15)

**왜** — 팀 규칙대로 기능마다 `feat/{모듈}-{작업}` 브랜치를 따면 브랜치가 너무 많아진다. 하루 작업을 번호로 묶어 관리하고, 에이전트와 나눈 설계 대화가 컨텍스트 압축으로 사라지지 않게 하고 싶었다.

**어떻게**
1. 처음 생각은 `#1`, `#2`로 작업 번호를 붙이는 것이었다. GitHub는 `#1`을 이슈·PR 번호로 링크하고, 이 저장소에는 이미 PR #1이 있어 `T01` 형식으로 바꿨다.
2. GitHub Issue로 관리하는 안과 문서 표로 관리하는 안을 비교했다. 배송 도메인 문서 안에서 관리하고 싶어서 문서 방식을 골랐다.
3. 태스크 하나 = 브랜치 하나(`feat/delivery-T01`), 세부 작업 = 커밋으로 정했다. `dev`에는 squash로 합쳐 커밋 하나만 남으므로 세부 기록은 태스크 문서(`tasks/T01.md`)에 커밋마다 남긴다.
4. 문서 위치는 모듈 안 `delivery-service/docs/`로 정했다. 문서 지도(`docs/README.md`)에 위치를 등록했다.
5. 컨텍스트 70%에서 자동 정리하는 안을 검토했다. Claude Code에는 사용량 기준 이벤트가 없어서 세 겹으로 바꿨다: 결정 즉시 기록(규칙) → 프롬프트 제출 훅이 transcript의 토큰 수를 보고 기준(140k)을 넘으면 정리 알림 → 압축 직후 훅이 기록 문서를 다시 읽게 함. 1M 컨텍스트에서는 비율이 의미가 없어 토큰 수로 정했다.
6. 기존 도메인 문서의 이모지 표기(체크·전구·물음표)는 팀 규칙(이모지 금지)에 맞춰 `[SA]`, `[제안]`, `[미정]`으로 바꿨다.

**결론** — 규칙은 [`../workflow.md`](../workflow.md), 태스크는 [`../tasks/`](../tasks/README.md)에 있다. 훅은 개인 설정(`.claude/settings.local.json`, `.claude/hooks/`)이라 저장소에 없다. 공통 설계 문서(`docs/design/05-infra*`)는 delivery 브랜치가 아니라 공통 작업 브랜치로 올린다.

## 12. 배송 구간 모델 확정 (2026-10-15)

**왜** — 이관된 ADR 0001은 2구간(모두 `HUB_WAITING` 시작)이고, SA v2.0과 `delivery-service/AGENTS.md`는 3구간이었다. 엔티티와 마이그레이션 전에 하나로 정해야 했다.

**어떻게** — 0001의 근거를 다시 읽었다. 2구간의 이유는 "재고가 이미 허브에 있으니 집하가 필요 없다"였다. SA v2.0은 재고를 고도화로 옮기고 공급업체가 항상 출고한다고 바꿨으므로 그 전제가 없다. 0001에서 쓸 만한 생각(배송 상태를 구간 상태에서 계산, 취소는 시작 상태 하나로 판단)은 3구간에서도 그대로 성립한다.

**결론** — SA v2.0 3구간으로 확정하고 [ADR 0003](../../../docs/services/delivery/decisions/0003-delivery-three-legs-sa-v2.md)으로 남겼다. 0001은 원문을 보존하고 상태만 "대체됨 (0003)"으로 바꿨다.

## 13. V1 DDL의 제약과 인덱스 범위 (2026-10-15)

**왜** — 마이그레이션 계획에서 SA에 없는 CHECK(상태 값, 구간 유형, 구간별 허브 NULL 규칙)와 담당 건수용 부분 인덱스를 제안했다. 나는 CHECK와 인덱스는 기능을 만든 뒤 분석해서 넣는 편이 낫다고 봤다.

**어떻게** — 제약을 성격별로 나눴다.
- 정합성(PK, `order_id` UNIQUE, `(delivery_id, sequence)` UNIQUE, 구간 → 배송 FK)은 성능이 아니라 기능이다. `order_id` UNIQUE가 없으면 I-09 재시도 때 배송이 두 건 생길 수 있고, 나중에 넣으면 이미 쌓인 중복 때문에 실패할 수 있다.
- 방어용 CHECK는 Entity 메서드와 enum이 같은 규칙을 지키므로 미뤄도 된다.
- 성능 인덱스를 모두 빼면 SA 5.6과 팀 마이그레이션 규칙(서비스 간 참조 컬럼 인덱스)과 달라져 ADR이 필요하다.

**결론** — V1에는 정합성 제약과 SA 5.6에 적힌 인덱스만 둔다. 방어용 CHECK와 부분 인덱스(담당 건수 등)는 기능을 다 만든 뒤 실제 쿼리의 `EXPLAIN ANALYZE`를 보고 새 마이그레이션으로 추가하거나 바꾼다.

## 14. 테스트 기준 (2026-10-15)

**왜** — V1 검증용으로 "테이블이 있고 기동된다"만 확인하는 테스트를 만들었다. 나는 단순 컨트롤러 테스트나 확인용 테스트는 의미가 적고, 복잡한 통합 테스트처럼 의미 있는 테스트를 써야 한다고 봤다.

**어떻게** — 팀 `test.md`와 비교했다. 컨트롤러 테스트는 방법만 있고 의무가 아니다. Entity 불변식 단위 테스트는 필수다. 서비스는 "public 메서드마다 성공 1개와 실패 케이스"라서, 실패 케이스를 의미 있는 분기로 고르면 규칙과 생각이 둘 다 맞는다. 스키마 테스트는 Repository 통합 테스트가 같은 검증(ddl-auto=validate)을 하므로 그때 대체한다.

**결론** — "없으면 잡지 못하는 버그가 무엇인가"를 기준으로 쓴다. 규칙은 [`../workflow.md`](../workflow.md) 7절이다.

## 15. enum 컬럼 타입: PostgreSQL enum을 검토하고 VARCHAR + CHECK로 (2026-10-15)

**왜** — 팀 마이그레이션 규칙은 enum 컬럼을 `VARCHAR(20)` + CHECK로 정한다. 13에서 CHECK를 미루기로 했는데, enum 값 CHECK는 내 제안이 아니라 팀 규칙이었다(에이전트가 처음에 둘을 섞어 설명했다). CHECK를 둘 거라면 DB enum 타입으로 만드는 편이 낫지 않나 생각했다.

**어떻게** — 두 방식을 비교했다. 둘 다 잘못된 값을 막는다. PostgreSQL enum 타입은 Entity에 `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`이 더 필요하고, 값 삭제가 어렵고(타입 재생성), 팀 규칙과 달라 ADR이 필요하다. 한 번 enum 타입으로 구현하고 임시 테스트로 저장·조회·JPQL 비교까지 확인했지만, ADR을 쓰고 팀 규칙과 다르게 가는 부담을 보고 되돌렸다.

**결론** — 팀 규칙대로 `VARCHAR` + CHECK(`ck_p_deliveries_status`, `ck_p_delivery_routes_route_type`, `ck_p_delivery_routes_status`)로 둔다. 13에서 미룬 것은 팀 규칙에 없는 CHECK(`sequence >= 1`, 구간 유형별 허브 NULL 규칙)뿐이다.

## 16. 구현하며 정한 것 (2026-10-15)

**왜** — D-01~05, I-09~13을 한 번에 구현하면서 `[제안]`·`[미정]`으로 남아 있던 항목과 문서에 없던 세부를 코드로 정해야 했다.

**어떻게** — SA와 충돌하지 않는 범위에서 가장 단순한 쪽을 골랐다.
- `Delivery.routes` cascade: ALL → PERSIST. 구간은 처음 저장할 때만 따라 저장되면 되고 이후는 변경 감지다. 물리 삭제가 없으니 REMOVE·orphanRemoval은 두지 않고, `Delivery.softDelete`가 구간까지 논리 삭제한다.
- I-09 멱등: 생성 서비스에 `@Transactional`을 두지 않는다. orderId 조회 → 원격 호출(I-03, I-14, I-01) → `saveAndFlush`(Spring Data 자체 트랜잭션). 동시 요청의 `order_id` 유니크 위반을 잡아 새 조회로 먼저 저장된 배송을 돌려준다(200). 트랜잭션 안에서 잡으면 rollback-only라 다시 읽을 수 없다.
- 같은 배송 안 연속 배정 반영(§4-2 [제안]): 채택. 허브 단위 advisory lock: 생략(편차 1~2건).
- 수령인 I-01 404(api.md §4 [미정]): Slack ID NULL로 진행. 503·타임아웃은 그대로 올린다.
- I-12 원복: 취소 상태일 때만 바꾸고 그 밖에는 no-op 200.
- D-05에 `WAITING`·`CANCELED`를 보내면 409 `INVALID_STATUS_TRANSITION`.
- D-03 두 필드 모두 없음: 400 `VALIDATION_ERROR`(§D-03 [제안] 채택).
- 에러 코드 위치(§9 [미정]): `DELIVERY_MANAGER_NOT_FOUND`, `MANAGER_HUB_MISMATCH`는 배송만 만드는 코드라 `DeliveryErrorCode`에 둔다(`docs/error-codes.md`도 delivery 표). `INVALID_STATUS_TRANSITION`은 이미 `CommonErrorCode`다.
- I-09~I-12 응답은 하나의 DTO(`deliveryId, orderId, status`)로 맞췄다. SA보다 필드가 많을 뿐 빠진 것은 없다.

**결론** — 위대로 구현했다. 요약은 [`docs/services/delivery/notes.md`](../../../docs/services/delivery/notes.md), 계약 차이는 [`docs/services/delivery/README.md`](../../../docs/services/delivery/README.md) 2절이다.

## 17. 조회와 명령 저장소 분리 (2026-10-15)

**왜** — 기준선 코드는 하나의 `DeliveryRepository`가 저장(`saveAndFlush`)과 조회(`search`, `countActiveRoutes`)를 함께 맡았고, 조회도 Entity를 읽은 뒤 서비스에서 DTO로 바꿨다. 나는 조회 성향이면 QueryDSL 프로젝션으로 DTO를 바로 내보내고, 생성·변경·삭제 성향이면 JPA(`findById`, `save`)로 Entity를 다루도록 명확히 나누는 것을 선호한다.

**어떻게**
- 명령: domain `DeliveryRepository`는 `save`, `saveAndFlush`, `findById`, `findByOrderId`만 둔다. `infrastructure/persistence/DeliveryJpaRepository`가 Spring Data로 구현한다(팀 규칙 그대로). 리뷰 중 JPA 인터페이스를 application으로 옮긴 것은 실수라 되돌렸다.
- 조회: `application/DeliveryQueryRepository` 인터페이스를 두고 `infrastructure/persistence/DeliveryQueryRepositoryImpl`(QueryDSL)이 구현한다. 검색, 단건(배송 + 구간을 쿼리 두 번), I-10 상태, 담당 건수를 모두 DTO로 돌려준다.
- `@QueryProjection` DTO를 어디에 둘지 세 안을 비교했다. infrastructure DTO를 application Result로 다시 바꾸는 안은 계층 규칙을 지키지만, 조회에서 값을 조작하지도 않는데 한 번 더 감싸는 것은 의미가 없다고 봤다. `Projections.constructor`는 어노테이션이 없지만 생성자 인자가 틀려도 컴파일에서 잡지 못한다.
- 그래서 application/dto의 Result record에 `@QueryProjection`을 붙여 그대로 반환한다. application이 QueryDSL 어노테이션(`querydsl-core`)에 의존하게 되어 팀 레이어 규칙("application은 JPA·QueryDSL에 의존하지 않는다")과 다르다. common이 이미 `querydsl-jpa`를 api로 내보내 의존 추가는 없다.
- 조회 범위 판단(배송담당자는 담당 구간이 있는 배송만)도 Entity 메서드 대신 구간 DTO로 한다. 쓰는 곳이 없어진 `Delivery.hasRouteManagedBy`, `DeliveryRoute.isManagedBy`는 지웠다.

**결론** — 조회는 프로젝션 DTO 직행, 명령은 JPA Entity. 확인: `./gradlew :delivery-service:test` 37개 통과(통합 테스트가 프로젝션 검색, 단건 조립, 논리 삭제·취소 구간 제외 집계를 실제 DB로 확인), `./gradlew build` 통과. 팀 규칙과 다른 점은 PR에 적는다.

## 18. 값을 바꾸지 않는 변환 없애기: delivery 코드 규칙 (2026-10-15)

**왜** — 코드를 읽어 보니 데이터를 옮기기만 하는 곳이 많아 가독성이 떨어졌다. D-01은 프로젝션 DTO(13필드)를 `DeliverySummaryResponse`로 그대로 복사했고, D-02는 Result 두 개를 래퍼로 묶었다가 `DeliveryResponse`와 `RouteResponse`로 다시 복사했다.

**어떻게** — 변환을 하나씩 따졌다. Feign DTO → 배송 용어는 다른 서비스 이름을 막는 의미가 있다. Request → Command는 웹 검증을 application에 넘기지 않는 분리다. Result → Response(조회·명령 응답)는 1:1 복사라 얻는 것이 없었다. 팀 `controller.md`는 "Result를 그대로 응답하지 않는다"고 정해 이 복사가 생겼다. 응답 형식만 맞으면 된다고 보고, 공통 규칙을 바꾸는 대신 배송 담당자로서 delivery만의 코드 규칙으로 정했다.

**결론** — `DeliveryResponse`, `DeliverySummaryResponse`, `DeliveryStatusResponse`를 지우고 컨트롤러가 Result를 그대로 응답한다. `DeliveryResult`는 `@JsonUnwrapped`로 배송 필드를 최상위로 펼쳐 기존 응답 모양을 복사 코드 없이 유지한다. 바꾸기 전후 D-01, D-02, D-03, I-10, I-11 응답 JSON 구조를 MockMvc로 저장해 비교했고 같았다. 규칙은 `delivery-service/AGENTS.md` "delivery 코드 규칙"에 있다.

이어서 이름 기준을 정했다. Result 하나를 감싸는 Response는 낭비지만, 여러 Result를 합쳐야 응답이 되는 경우에는 그 조합을 `Response`라고 부른다. 배송과 구간을 합치던 `DeliveryResult`를 `DeliveryResponse`로 바꿨다. 합치는 일이 서비스 안에서 일어나서(D-02 구간으로 권한 확인, D-03~D-05 Entity) presentation으로 옮기면 서비스가 재료를 넘길 묶음이 하나 더 필요하므로 `application/dto`에 둔다.

## 19. 주문 DM용 수신자 조회 (2026-10-16)

**왜** — 사용자가 이메일로 배송담당자에게 DM을 보내는 기능을 요청했다. 기존 I-10은 주문 삭제 때 쓰는 상태 조회이며 구간 담당자가 없다.

**어떻게** — 기존 I-10에 필드를 섞는 대신 I-15를 추가했다. 기존 상태·구간 조회를 재사용해 WAITING·MOVING 담당자 ID를 중복 제거한다. 완료·취소 배송은 빈 배열, 없음·삭제는 404다. 이메일과 Slack 호출은 user·order가 맡는다.

**결론** — [ADR 0016](../../../docs/decisions/0016-delivery-manager-slack-dm.md)의 로컬 확장으로 구현했다. 39개 delivery 테스트와 같은 허브 실제 주문의 DM 1건을 확인했다. DELIVERY-003은 담당자 리뷰 대기이며, 발송 직전 재배정·취소와의 원자성을 추가한 것은 아니다. 작업은 [T06](../tasks/T06.md)에 있다.

## 20. 배송담당자 지정 해제 후 변경 권한 (2026-10-19)

**왜** — 현재 자격이 false여도 배정 ID만 일치하면 출발·도착·수령 정보 수정이 허용됐다. 단위·PostgreSQL HTTP 회귀 테스트에서 이 요청들이 거절되지 않는 것을 먼저 확인했다.

**어떻게** — 사용자가 #13 상세 계획을 읽고 구현을 요청했다. DeliveryService의 두 변경 경로가 `validateAssignedManager`를 사용하고, MASTER 또는 현재 배송담당자인 해당 구간 담당자만 허용한다. 프런트 nextRouteAction도 같은 조건을 사용한다. UserContext에 값이 있으므로 원격 호출·스키마 변경은 추가하지 않는다.

**결론** — 이 사용자 확인 범위로 로컬 구현한다. Gateway 60초 캐시 지연과 MASTER 수동 인계를 유지한다. 자동 재배정·해제 자체 차단·일반 USER 조회 제한은 별도 정책이다. 팀 전체 승인·병합 완료를 뜻하지 않는다. 기록은 [T07](../tasks/T07.md)이다.

## 21. 배송 변경 동시 실행 보호: 배송 행 비관적 잠금 (2026-10-19)

**왜** — 이슈 #11. 명령 조회가 일반 `findById`라 같은 배송의 두 요청이 낡은 상태를 읽고 모두 커밋됐다. 재배정이 MOVING을 읽은 채 도착이 커밋되면 배송 DELIVERED에 마지막 구간 MOVING이 남았고, 출발이 WAITING을 읽은 채 취소가 커밋되면 둘 다 성공해 배송 PICKING_UP에 구간 CANCELED가 남았다. `@Transactional`은 한 요청 안의 원자성만 보장하므로 요청 사이의 순서는 막지 못한다.

**어떻게** — `@Version`(낙관적)과 `PESSIMISTIC_WRITE`(비관적)를 비교했다. 낙관적은 늦은 요청이 예외로 실패해 새 충돌 응답과 재조회·재시도 규약이 필요하고, 구간만 바꾸는 D-04까지 덮으려면 `DeliveryRoute` 버전도 필요하다. 비관적은 늦은 요청이 기다렸다가 갱신된 상태를 읽고 기존 규칙으로 409·404를 낸다. 배송 1건의 구간은 최대 3개이고 변경이 드물어 같은 배송의 직렬화 비용이 작다. 구간은 `Delivery`가 애그리거트 루트로서만 바꾸므로 배송 행 하나를 잠그면 충분하다. D-04가 트랜잭션 안에서 user-service를 부르면(최대 20초) 그동안 배송 행이 잠기므로, I-01 조회를 먼저 끝내고 `TransactionOperations`로 잠금 트랜잭션을 연다. 대가로 없는 구간이어도 담당자 404가 먼저 나올 수 있다.

**결론** — `DeliveryRepository.findByIdForUpdate`·`findByOrderIdForUpdate`를 추가하고 D-03·D-04·D-05, I-11·I-12·I-13이 이것으로 시작한다. 잠금 대상이 배송 행 하나뿐이라 데드락이 생기지 않는다. 잠금 타임아웃은 두지 않았다(PostgreSQL 방언이 밀리초 타임아웃 힌트를 지원하지 않는다). API 계약과 에러 코드는 바뀌지 않았다. 실제 PostgreSQL 회귀 테스트 5개(`DeliveryConcurrencyIntegrationTest`)로 재배정↔도착, 출발↔취소, 취소↔출발, 삭제↔출발, 다른 배송 독립을 확인했다. DELIVERY-004의 결정이다.

## 22. 일반 USER 배송 전체 조회 유지 (2026-10-19)

**왜** — 이슈 #12가 일반 USER의 타 업체 배송 조회와 수령인 개인정보 노출을 지적했다. 해결 방향으로 사용자·업체 관계나 주문자로 조회 범위를 좁히는 방안을 검토했다.

**어떻게** — 먼저 주문자 컬럼(`orderer_id`)과 업체 관리자 조회를 비교했다. 코드를 읽어 보니 I-09에는 요청업체 관리자(`receiverManagerId`)만 있고 공급업체 관리자가 없어, 요청업체 기준으로만 제한하면 공급업체가 자기 물건의 배송을 추적하지 못한다. 한편 발제 원본을 다시 확인하니 배송 관리 권한이 "조회 및 검색은 모든 로그인 사용자가 가능합니다(담당자는 본인 배송만)"이다. 사용자가 발제 원본을 최우선으로 정했다.

**결론** — 조회 범위와 응답 필드를 바꾸지 않는다. 코드 변경이 없고 DELIVERY-006·SA 6.8·SA 9장을 이 결정에 맞춰 기록만 고쳤다. 개인정보 노출은 알려진 위험으로 남긴다. 줄이는 방법은 일반 USER 응답의 마스킹이며, 자기 거래 배송을 구분하려면 I-09에 공급업체 관리자 ID를 추가하는 order 계약 변경이 필요하다. 팀이 발제 해석을 정하면 진행한다.

## 23. 종료된 생성 시도의 지연된 I-09 차단: 종료 표시와 orderId 잠금 (2026-10-19)

**왜** — 이슈 #14. I-13이 대상 배송이 없으면 흔적 없이 204로 끝나서, 주문이 타임아웃 뒤 보상(I-13)을 마친 다음 지연된 I-09가 도착하면 이미 종료된 시도의 활성 배송이 생겼다. 실제 PostgreSQL에서 재현했다(허브 경로 조회를 붙잡고 I-13을 먼저 끝낸 뒤 재개하면 활성 배송 1건이 남았다).

**어떻게** — 시도 토큰과 종료 표시를 비교했다. order는 시도마다 `orderId`를 새로 만들어 I-09·I-13에 쓰므로 `orderId`가 이미 시도 식별자라 토큰 필드가 필요 없다. 그래서 종료 표시(`p_delivery_creation_closures`, order_id 기본키)를 택했다. 표만으로는 "I-09가 표를 확인한 뒤 저장하기 전에 I-13이 표와 삭제를 끝내는" 순서가 남는다. 이를 막으려고 I-09의 저장과 I-13이 같은 orderId의 PostgreSQL advisory lock(`pg_advisory_xact_lock`)을 잡는다. 행 잠금은 쓸 수 없다. 잠글 행이 아직 없기 때문이다. I-09는 원격 호출을 잠금 밖에서 하고, 저장 직전 짧은 트랜잭션에서만 잠금·기존 배송 재확인·표 확인·저장을 한다. 원격 호출 전에도 한 번 확인해 불필요한 호출을 줄인다.

**결론** — I-13은 대상 유무와 상관없이 표를 남기고(멱등), I-09는 표가 있으면 409 `DELIVERY_CREATION_CLOSED`로 거절한다. 표는 만료하지 않는다. 이 변경 이전에 논리 삭제된 배송은 V2가 표를 채운다(안 채우면 같은 orderId의 I-09가 order_id 유니크 위반 500이 된다). 결과 확인은 I-17(`ACTIVE`·`CLOSED`·`NONE`)로 제공한다. 번호는 user의 I-16과 겹치지 않게 I-17로 했다. order의 RECOVERY_REQUIRED 자동 종료·운영 복구 API는 범위 밖이다. 테스트 4개(`DeliveryCreationClosureIntegrationTest`)로 지연 생성 거절, 저장 직전 삭제의 직렬화, 삭제 뒤 재생성 거절과 멱등, I-17 상태와 HTTP 409를 확인했다. DELIVERY-002의 결정이다.

## 24. 부하 테스트 방법과 테스트 전용 배송 생성 API (2026-10-15)

**왜** — 동시성을 잡은 뒤 트래픽이 많을 때의 병목을 코드로 분석했다(학습 노트 `2026-10-15-delivery-high-traffic-bottlenecks.md`). 가장 큰 후보는 배송 생성(I-09) 안의 원격 호출과 담당 건수 쿼리였다. 고치기 전에 k6로 지금 상태를 재고, 하나씩 바꾸며 다시 재는 흐름으로 가기로 했다. 그런데 배송 생성은 내부 API라 Gateway가 막고, 주문(O-01)을 거치면 order·company 시간이 섞여 배송만 바꾼 효과가 묻힌다. 그래서 주문을 거치지 않고 배송에 직접 부하를 줄 테스트용 컨트롤러를 두기로 했다.

**어떻게** — 세 방법을 비교했다.
- delivery-service 포트를 열고 `/internal/deliveries`를 직접 부르기: 코드 변경이 없다. 하지만 Gateway를 거치지 않아 실제 경로(인증 필터, 라우팅)와 다르고, 사용자 헤더를 손으로 넣어야 한다.
- 주문 API로만 재기: 실제 경로지만 배송 쪽 변화를 따로 볼 수 없다. 전체 경로 측정용(`TARGET=order`)으로는 남긴다.
- 외부 경로에 테스트 전용 컨트롤러: Gateway·인증까지 실제와 같고 배송 생성만 잰다. 대신 내부 전용 처리를 외부로 여는 것이라 운영에서 켜지면 안 된다.

세 번째를 고르고 세 겹으로 막았다. `@ConditionalOnProperty(delivery.load-test.enabled=true)`가 아니면 빈이 없다. `@RequireRole(MASTER)`이고 `@Hidden`으로 Swagger에 싣지 않는다. 처리는 `DeliveryCreationService.create`를 그대로 불러 I-09와 같다.

켜는 방법은 팀 설정 규칙("새 환경 변수는 `.env.example`과 `docker-compose.yml`에 함께 추가")과 다르게 갔다. `docker-compose.yml`에 넣으면 모든 팀원의 기본 실행에 변수가 생기고 팀 리뷰 대상이 된다. 대신 덧붙임 파일 `docker-compose.loadtest.yml`을 두고 `COMPOSE_FILE`로 합칠 때만 켠다. `config-repo/delivery-service.yml`의 기본값은 `false`다.

**결론**
- 측정: `load-test/k6/delivery-create.js`(`TARGET=delivery|order`), `delivery-read.js`. 데이터는 시드(`delivery-e2e.sql`)와 과거 배송 대량 데이터(`delivery-load.sql`, 기본 30만 건). 매 측정은 `--reset`한 같은 DB 상태에서 시작한다.
- 테스트 API: `LoadTestDeliveryController`(`POST /api/v1/deliveries/load-test`), 꺼짐·켜짐 조건 테스트 3개.
- 절차·실험 계획·결과 표: [`../load-test.md`](../load-test.md). 실험 순서는 담당 건수 부분 인덱스 → user 호출 캐시 → user 장애 재현과 타임아웃 → 목록 인덱스다. 한 번에 하나만 바꾼다.

## 25. 로컬 부하 측정 방식: 예열을 통제하고 데이터만 비운다 (2026-10-19)

**왜** — 장애 격리 실험(S1)을 세 번 돌렸는데 모두 장애 전부터 실패해 쓸 수 없었다. 첫 번째는 실험 설정이 빠졌고(TS-027), 두 번째와 세 번째는 기동 직후 부하로 커넥션 풀이 바닥났다(TS-028).

**어떻게** — 속도를 5 → 10 → 20으로 올리며 각 1분씩 쟀고, 실행 스크립트가 부하 중 컨테이너 CPU, 커넥션 풀, 스레드, GC를 남기게 했다. 같은 초당 10+10에서 재기동 후 첫 실행은 delivery CPU 227%·생성 p95 2,151ms, 바로 다음 실행은 20%·1,433ms였다. GC는 누적 0.6초, 힙 사용 30% 이하였다. 힙 부족(`-Xmx384m`) 가설은 기각하고 JIT 예열로 판단했다. 매 측정마다 `--reset`으로 컨테이너를 다시 만든 것이 예열을 없앴다.

**결론**
- 측정 전 같은 속도로 1분씩 2번 이상 예열한다. 측정 사이에는 `load-test/reset-delivery-data.sh`로 배송·구간 테이블만 비운다. `--reset`은 처음 한 번만 쓴다.
- 본 측정 속도는 초당 생성 10 + 조회 10. 20+20도 버텼으므로 여유가 있고, 장애 때 스레드 고갈(10 × 60초 = 600 > 200)을 재현하기에 충분하다.
- 분산 추적은 실험 내내 100%로 고정한다. 바꾸려면 컨테이너를 다시 만들어야 해 예열이 사라진다. zipkin CPU는 5% 수준이었다.
- 근거 수치는 [fault-isolation.md](../fault-isolation.md) 8절 예비 측정, 방법론은 학습 노트 `2026-10-19-local-load-test-methodology.md`.

## 26. user 장애 격리: 타임아웃·재시도·벌크헤드·서킷 브레이커를 한 번에 (2026-10-19)

**왜** — S1(초당 생성 10·조회 10, user 30초 정지)에서 user를 부르지 않는 조회 p95가 21ms → 4,307ms가 됐다. delivery 스레드 189/200이 user 응답을 기다렸고 커넥션 대기는 0이었다. user를 되살린 뒤에도 묶였던 요청이 한꺼번에 풀려 2분 넘게 회복하지 못했다(fault-isolation.md 8절).

**어떻게** — 실험 문서는 한 번에 하나씩 넣고 매번 재는 순서였다. 사용자가 "read 20초·재시도 3번은 측정 없이도 명백히 과하다"며 한 번에 넣고 재기로 했다. 대신 각 장치가 얼마나 작동했는지는 Resilience4j 메트릭(벌크헤드 거절 수, 서킷 상태, 거절 호출 수)으로 구분한다. 타임아웃만으로 부족한 이유는 계산으로 설명한다(초당 10 × 최대 2초 = 스레드 20개, 재시도·조회가 겹치면 더 많다).
- 타임아웃: user·hub 모두 connect 1초, read 2초(`config-repo/delivery-service.yml`). 평소 내부 조회는 수십 ms다.
- 재시도: `ConnectFailureRetryer`. 연결 실패(거부, 연결 타임아웃)만 1번 더 시도한다. 읽기 타임아웃은 상대가 느린 것이라 다시 보내면 스레드만 더 묶인다. 공통 `IdempotentRetryConfig`는 바꾸지 않고 delivery 클라이언트만 바꿨다.
- 벌크헤드(user, 동시 10, 대기 0): 평소 동시 호출은 초당 20 × 0.03초 < 1이다(리틀의 법칙). user가 멈춰도 user를 기다리는 스레드가 10개를 넘지 않는다.
- 서킷 브레이커(user): 최근 20번 중 실패 50% 또는 1초 넘는 호출 50%면 열고, 10초 뒤 3번 시험한다. 4xx 거절은 실패로 세지 않는다(`UserServiceFailures`).
- 실패 처리: 담당자 후보(I-14)와 재배정 대상(I-01, D-04)은 대신할 값이 없어 503으로 빨리 실패한다. 수령인 Slack ID(I-01, 배송 생성)는 Slack ID 없이 배송을 만든다(사용자 결정, 부분 저하). 서킷·벌크헤드 거절은 그대로 두면 500이라 fallback에서 503으로 바꾼다.

**결론**
- 코드: `UserClientAdapter`(애너테이션, fallback), `UserServiceFailures`, `ConnectFailureRetryer`·`ConnectFailureRetryConfig`, `UserClient`·`HubClient`·`CompanyClient`의 재시도 설정, delivery `build.gradle`(resilience4j-spring-boot3 2.3.0, AOP).
- 테스트: `ConnectFailureRetryerTest`, `UserServiceFailuresTest`, `UserClientResilienceIntegrationTest`(AOP로 fallback·서킷이 실제로 걸리는지).
- 측정: 같은 조건의 S1을 `DURATION=6m`으로 다시 잰다. 결과는 fault-isolation.md 8절.
- order → delivery 타임아웃 예산(재시도 증폭, S2)은 order 설정이라 이번 범위가 아니다.
- 추가(같은 날): 부하를 초당 80요청(생성 40·조회 40)으로 올리자 장애가 없는데도 생성 19%가 503이었다. 시작 직후 순간 지연(p95 3~4초)에 서킷이 열려 약 10초간 거절했다. 최근 20건 창은 초당 80번 호출에서 0.25초 분량이라 순간 지연에 너무 민감했다. 판단 창을 최근 10초(TIME_BASED, 최소 20건)로, 느린 호출 기준을 1.5초·80%로 바꿨다.
- 검증(같은 날 13시): 예열 후 같은 초당 80요청에서 장애 없이 생성 503이 19% → 0.4%(시작 10초 안 10건, 벌크헤드 거절로 판단)가 됐고 서킷은 한 번도 열리지 않았다. 같은 부하에 user 30초 정지를 넣어도 조회 p95 7.7ms·성공 100%, 복구 약 20초였다. 개선 전과 같은 초당 20요청 조건에서는 조회 p95 4,307ms → 15ms, 바쁜 스레드 189 → 1, 복구 120초+ → 약 20초(fault-isolation.md 8절).

## 남은 일

| 할 일 | 누구와 |
|---|---|
| I-14 구현 (`hub_id`+CHECK, I-01 `hubId`는 10/15 저장소에서 확인됨) | user 담당 |
| I-03 내부 허브 경로 조회 | hub 담당 |
| I-09 필드명, 404 전달, I-11·12·13 보상 순서 확인 | order 담당 |
| 시연용 허브별 배송담당자 계정 | user 담당 |
| ~~SA 공통 에러 코드를 common에 둘지~~ → 16: 배송 코드는 `DeliveryErrorCode` | 팀 |
