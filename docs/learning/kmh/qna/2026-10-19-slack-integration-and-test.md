---
title: Slack 연동 방법과 테스트 절차
date: 2026-10-19
updated: 2026-10-19
asker: kmh
level: 중급
tags: [slack, webhook, slack-web-api, dm, notification, test, http-stub]
---

# Slack 연동 방법과 테스트 절차

## 질문
Slack 연동 방법과 테스트를 수행하는 절차를 알고 싶다.

## 짧은 답
order-service에는 Slack 알림이 두 가지 있다. 둘 다 주문이 DB에 커밋된 뒤 비동기로 보낸다. Slack이 실패해도 주문은 성공으로 남는다.

| | ① 채널 알림 (Incoming Webhook) | ② 배송담당자 DM (Slack Web API) |
|---|---|---|
| 받는 곳 | Webhook을 만들 때 고른 채널 하나 | 그 주문 구간을 맡은 배송담당자 개인 DM |
| 필요한 값 | `SLACK_WEBHOOK_URL` | `SLACK_DM_ENABLED`, `SLACK_BOT_TOKEN`, `SLACK_DM_WORKSPACE_ID`, (선택) `SLACK_DM_ALLOWED_EMAILS` |
| 기본 상태 | URL이 비어 있으면 생략 | `SLACK_DM_ENABLED=false`라 꺼져 있음 |
| 구현 | `SlackOrderNotificationSender` | `DeliveryManagerDmService`, `SlackDmClient` |

연동은 세 단계다. Slack 앱에서 Webhook URL이나 Bot Token을 발급한다. 그 값을 `.env`에 넣는다. order-service 컨테이너를 새로 만든다.

테스트는 두 층으로 나뉜다. 자동 테스트는 진짜 Slack 대신 로컬 HTTP 대역 서버(`HttpStubServer`)로 돈다. 실제 발송 확인은 수신자를 본인으로 제한한 뒤 수동으로 한 번 한다.

## 자세히

### 1. 채널 알림(Webhook) 연동
1. 워크스페이스와 채널을 정한다. 개발 알림이 쌓이므로 전용 채널(예: `#logistics-order-dev`)을 추천한다.
   - 앱 설치는 워크스페이스 정책에 따라 관리자 승인이 필요할 수 있다. `2026 내일배움캠프` 워크스페이스는 2026-10-15에 앱 설치 한도 초과로 막혔던 기록이 있다(`docs/learning/junhyun/qna/2026-10-15-slack-incoming-webhook-setup.md`). 테스트용이면 본인이 관리하는 워크스페이스가 편하다.
2. https://api.slack.com/apps 에서 앱을 만들거나 고른다.
3. `Incoming Webhooks`에서 `Activate Incoming Webhooks`를 켠다.
4. `Add New Webhook to Workspace`로 채널을 고르고 허용한다. `https://hooks.slack.com/services/...` URL이 발급된다.
5. 저장소 루트의 `.env`에 `SLACK_WEBHOOK_URL=<발급 URL>`을 넣는다. 이 URL만 있으면 누구나 그 채널에 글을 쓸 수 있는 비밀값이다. Git, 문서, 채팅, 스크린샷에 남기지 않는다.
6. 반영: `.env` 변경은 `restart`로는 반영되지 않는다. 컨테이너를 새로 만들어야 한다. `script/run.sh order-service` 또는 `docker compose up -d --force-recreate order-service`로 한다.

Webhook URL은 채널 하나에 고정된다. 그래서 수신자를 요청마다 바꿀 수 없다. 사람별 DM에는 ②의 Web API가 필요하다.

### 2. 배송담당자 DM 연동
**Slack 앱 설정**
1. 같은 앱의 `OAuth & Permissions` → `Bot Token Scopes`에 다음을 추가한다.
   - `users:read.email`: 이메일로 Slack 멤버 찾기
   - `im:write`: DM 열기
   - `chat:write`: 메시지 보내기
   - `users:read`: 이메일 권한의 의존 권한으로 설정 화면이 요구했다(2026-10-16 확인)
2. 권한을 바꿨으면 워크스페이스에 앱을 다시 설치한다. 재설치하면 토큰이 바뀔 수 있고, 기존 Webhook이 무효화된 사례도 있었다(`docs/reviews/slack-dm.md`).
3. `Bot User OAuth Token`(`xoxb-`로 시작)을 복사한다. User Token(`xoxp-`)이나 Webhook URL과 다른 값이다.
4. 워크스페이스 ID(`T`로 시작)를 확인한다. 브라우저에서 Slack을 열었을 때 주소 `app.slack.com/client/T.../`의 `T...` 부분이다.

**서버 설정 (`.env`)**
```
SLACK_DM_ENABLED=true
SLACK_BOT_TOKEN=xoxb-...            # 비밀값
SLACK_DM_WORKSPACE_ID=T...
SLACK_DM_ALLOWED_EMAILS=me@example.com   # 테스트 중에는 본인 이메일만
```
- 활성화, 토큰, 워크스페이스 ID가 모두 있어야 발송한다.
- `SLACK_DM_ALLOWED_EMAILS`는 수신자 허용 목록이다. 비워 두면 일반 환경에서는 모두 허용한다. 테스트 중에 다른 사람에게 DM이 가는 사고를 막으려면 본인 이메일만 넣는다. 격리 UI 환경(`script/ui-test.sh`)은 DM을 켜면서 목록을 비우면 실행을 거부한다.
- 반영은 ①과 같다. 토큰이나 활성화 값만 바꿨으면 order-service만 다시 만들면 된다.

**데이터 준비 (화면 또는 Swagger)**
DM은 이메일만 등록한다고 가지 않는다. 그 사람이 실제로 배정된 배송 구간이 있어야 한다.
1. 회원가입할 때 `Slack 계정 이메일`에 Slack 가입 이메일을 넣는다. 기존 사용자는 MASTER가 U-06(사용자 상세의 `Slack DM 알림`)으로 이메일을 설정한다.
2. MASTER가 U-07로 그 사용자를 배송담당자로 지정하고 담당 허브를 고른다.
3. 공급·수령업체가 그 허브에 연결된 상태에서 주문을 만든다. 해당 담당자에게 WAITING이나 MOVING 구간이 배정되면 알림 대상이 된다.

### 3. 처리 흐름
```
주문 생성 트랜잭션 커밋
  └ @TransactionalEventListener(AFTER_COMMIT) → 비동기 실행기(스레드 2개, 큐 100개)
      ├ ① Webhook: POST hooks.slack.com/... → 채널 메시지
      └ ② DM:
          order → delivery I-15 : 현재 구간 담당자 ID 목록
          order → user I-16     : 담당자별 배송담당 여부, slackEmail
          order → Slack         : users.lookupByEmail → conversations.open → chat.postMessage
```
- 커밋 뒤에 보내는 이유: 커밋 전에 보내면, 이후 롤백된 주문의 알림이 나간다. 또 느린 Slack을 기다리는 동안 DB 커넥션과 잠금을 잡고 있게 된다.
- 실패해도 주문은 그대로다. 외부 알림 장애로 이미 커밋한 업무 결과를 바꾸지 않는다.
- Slack POST는 자동 재전송하지 않는다. 응답이 유실됐을 뿐 실제로는 보내졌을 수 있다. POST는 멱등하지 않아서 다시 보내면 중복 메시지가 생긴다. 내부 조회 I-15·I-16은 GET이라 통신 실패 시 최대 3회 시도한다.
- Slack Web API는 실패해도 HTTP 200에 `{"ok": false, "error": "..."}`로 응답한다. 그래서 상태 코드만 보지 않고 `ok` 값을 확인한다.
- 사용자 입력의 `<!channel>` 같은 멘션은 `&lt;`로 이스케이프한다. 주문 요청사항으로 채널 전체 알림을 띄우는 일을 막기 위해서다.
- 로그에는 토큰, 이메일, Webhook URL, Slack 응답 원문을 남기지 않는다. 결과 분류(`SLACK_DM_SENT`, `CONTACT_NOT_CONFIGURED`, `SLACK_DM_NOT_ALLOWED` 등)만 남긴다.
- 비동기 큐는 메모리에 있다. 프로세스가 죽으면 알림이 유실될 수 있다. Outbox 패턴은 후속 범위다.

### 4. 자동 테스트 (진짜 Slack 없이)
| 테스트 | 종류 | 확인하는 것 |
|---|---|---|
| `SlackOrderNotificationSenderTest` | 단위 + HTTP 대역 | Webhook 메시지 형식, 멘션 이스케이프, 실패 시 URL 비노출 |
| `SlackDmClientTest` | 단위 + HTTP 대역 | 이메일 URL 인코딩, Bearer 인증, 조회→열기→발송 순서, `ok=false` 실패 처리, 예외에 토큰·이메일 비노출, `X-User-*` 헤더 비전파 |
| `DeliveryManagerDmServiceTest` | 단위(mock) | 담당자·이메일 중복 제거, 미설정·허용 목록 밖이면 생략, 한 명 실패해도 나머지 계속 |
| `OrderNotificationIntegrationTest`, `OrderDmNotificationIntegrationTest` | 통합(Testcontainers) | 커밋 후 별도 스레드에서 발송, 롤백이면 미발송, 같은 Idempotency-Key 재요청이면 재발송 없음, Slack 실패에도 주문 201 유지 |

**HTTP 대역 서버를 쓰는 이유:** `HttpStubServer`는 JDK 내장 `HttpServer`로 `127.0.0.1`의 임의 포트에 가짜 Slack을 띄운다. `SLACK_DM_API_BASE_URL`(테스트에서는 `api-base-url`)을 이 주소로 바꾸면 클라이언트 코드는 그대로 두고 실제 HTTP 요청과 응답을 주고받을 수 있다. mock 객체와 달리 URL 인코딩, 헤더, JSON 본문처럼 HTTP 계층에서만 드러나는 문제를 잡을 수 있다.

**실행**
```bash
# Slack 관련 단위 테스트 (Docker 불필요)
./gradlew :order-service:test --tests "*Slack*" --tests "*DeliveryManagerDmServiceTest"

# 알림 통합 테스트 (Docker 필요)
./gradlew :order-service:test --tests "*OrderNotificationIntegrationTest" --tests "*OrderDmNotificationIntegrationTest"

# 전체
./gradlew :order-service:test
```
IntelliJ에서는 테스트 클래스 옆 실행 버튼을 누른다. 결과 보고서는 `order-service/build/reports/tests/test/index.html`에 생긴다.

### 5. 실제 Slack으로 수동 확인
1. `.env`에 위 값을 넣는다. `SLACK_DM_ALLOWED_EMAILS`에는 본인 이메일만 둔다.
2. `script/run.sh`(또는 `script/run.sh order-service`)로 띄운다.
3. 본인 이메일로 가입하거나 이메일을 설정한다. 배송담당자로 지정하고, 그 허브의 업체로 주문을 만든다.
4. 확인
   - 주문 API가 201을 반환한다. 이것은 주문 성공이지 알림 성공이 아니다.
   - 로그를 확인한다: `docker compose logs order-service | grep -E "SLACK_DM_|SLACK_NOTIFICATION"`. 성공하면 `SLACK_DM_SENT`가 찍힌다.
   - Slack 앱의 DM 창에 메시지가 온다. Webhook을 설정했으면 채널에도 신규 주문 메시지가 온다.
5. 실패하면 로그의 분류로 원인을 좁힌다.
   - `CONTACT_NOT_CONFIGURED`: 배송담당자가 아니거나 이메일이 없다.
   - `SLACK_DM_NOT_ALLOWED`: 허용 목록 밖의 이메일이다.
   - `USER_NOT_FOUND`: Slack에 그 이메일로 가입한 멤버가 없다.
   - `PERMISSION_DENIED`: 스코프가 빠졌거나 앱을 재설치하지 않았다.
   - `WORKSPACE_MISMATCH`: 워크스페이스 ID가 다르다.
   - 로그가 아예 없으면: 그 사람에게 배정된 구간이 없거나, `.env` 변경 후 컨테이너를 새로 만들지 않았다.
6. 끝나면 테스트 주문을 취소한다. 다른 테스트(E2E)를 돌리기 전에는 `SLACK_DM_ENABLED=false`로 돌려 놓고 컨테이너를 다시 만든다. E2E와 시드 스크립트는 DM이 켜져 있거나 Webhook이 있으면 주문을 만들기 전에 중단한다.

### 6. 비밀값 관리
- `.env`, `.env.ui-test`는 Git에서 제외되어 있다. 값은 서버(order-service 컨테이너)에만 전달하고 프런트에 넣지 않는다.
- 토큰이 노출되면 Slack 앱 설정에서 즉시 폐기하고 새로 발급한다. 이 프로젝트에도 2026-10-16에 토큰을 교체한 기록이 있다(`docs/reviews/slack-dm.md`).

### 더 깊이
- 정본 문서: `docs/guides/slack-dm.md`(설정·실패 표), `docs/decisions/0016-delivery-manager-slack-dm.md`(설계 결정), `docs/services/order/decisions/0003-slack-notification-after-commit.md`(커밋 후 비동기 알림), `docs/guides/config-security.md`(비밀값 전달 경로).
- Slack 공식 문서: [users.lookupByEmail](https://docs.slack.dev/reference/methods/users.lookupByEmail/), [conversations.open](https://docs.slack.dev/reference/methods/conversations.open/), [chat.postMessage](https://docs.slack.dev/reference/methods/chat.postMessage/).

## 면접 포인트
- "알림을 트랜잭션 안에서 보내면 안 되는 이유": 롤백된 주문의 알림이 나갈 수 있다. 또 외부 API 지연이 DB 커넥션과 잠금 점유로 이어진다. `AFTER_COMMIT` 이벤트와 비동기 실행으로 분리했다.
- "알림이 유실되면": 메모리 큐라 프로세스가 종료되면 유실될 수 있다. 보장이 필요하면 Transactional Outbox(이벤트를 같은 트랜잭션으로 테이블에 저장하고 별도 발행)를 쓴다.
- "외부 API 테스트는 어떻게 했나": 로컬 HTTP 대역 서버로 실제 HTTP 요청과 응답을 검증했다. 실제 발송은 수신자 허용 목록으로 범위를 제한한 뒤 한 번만 확인했다.
- 꼬리 질문: Slack POST를 재시도하지 않은 이유. 응답이 유실돼도 이미 전송됐을 수 있고, POST는 멱등하지 않아 중복 메시지가 생긴다.
