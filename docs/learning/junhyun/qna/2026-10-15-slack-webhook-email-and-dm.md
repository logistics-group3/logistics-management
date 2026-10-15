---
title: Slack Webhook을 배송담당자 이메일에 연결할 수 있는가
date: 2026-10-15
updated: 2026-10-15
asker: Junhyun An
level: 중급
tags: [slack, webhook, oauth, notification, delivery]
---

# Slack Webhook을 배송담당자 이메일에 연결할 수 있는가

## 질문

> 슬랙 웹훅도 배송담당자 이메일로 연결할 수 있나?

## 짧은 답

Incoming Webhook은 설치할 때 선택한 채널로 보내므로 이메일을 넣어 수신자를 바꿀 수 없다.
같은 워크스페이스에 등록된 이메일로 Slack 사용자를 찾고 그 사용자에게 DM을 보내는 방식은 별도 Web API 연동으로 가능하다.
현재 프로젝트는 Slack 멤버 ID를 저장하고 고정 채널 Webhook을 사용한다. 이메일 조회·개인 DM 발송은 아직 구현하지 않았다.
이메일은 Slack 사용자를 찾는 입력이며, 실제 이메일함으로 메일을 보내는 기능과는 다르다.

## 자세히

| 방식 | 받는 곳 | 필요한 정보·권한 |
|---|---|---|
| Incoming Webhook | 앱 설치 때 선택한 채널 | Webhook URL, `incoming-webhook` |
| 이메일로 사용자를 찾아 DM | 그 Slack 사용자와 앱의 DM | Bot OAuth Token, 이메일 또는 Slack 멤버 ID, 선택한 API의 스코프 |
| 이메일 알림 | 사용자의 메일함 | 별도 메일 발송 서버·서비스 |

Webhook URL은 발송 목적지와 권한을 함께 가진 비밀값이다. 이메일 주소로 대체하거나 payload에 이메일을 추가해 채널을 바꾸는 방식이 아니다.
Slack 공식 문서는 Webhook 요청으로 기본 채널·username·아이콘을 덮어쓸 수 없다고 설명한다.

명시적으로 DM을 열어 보내는 구현 흐름은 다음과 같다.

1. 서버에서 `users.lookupByEmail`로 해당 워크스페이스의 등록 이메일을 조회한다. `users:read.email` 스코프가 필요하다.
2. 결과의 Slack 멤버 ID로 `conversations.open`을 호출해 앱과 사용자의 DM을 연다. 1:1 DM에는 `im:write`가 필요하다.
3. 반환된 DM 채널 ID에 `chat.postMessage`로 메시지를 보낸다. `chat:write`가 필요하다.

이미 멤버 ID를 알고 있다면 1번 조회를 생략할 수 있다. 따라서 현재 회원가입의 `slackId`를 이용하면 이메일 조회 권한 없이 시작할 수 있다.
Bot Token은 서버 설정에 보관한다. 프런트엔드 환경 변수·브라우저 코드에 넣지 않는다. 스코프를 추가하면 Slack 앱의 설치 권한도 그에 맞게 갱신해야 한다.
대상 이메일은 같은 워크스페이스에 등록된 활성 사용자를 가리켜야 한다. 임의의 이메일 주소로 Slack 외부 사람에게 보낼 수 있다는 의미가 아니다.

## 이 프로젝트에서는

- `user-service/.../presentation/dto/SignupRequest.java`는 `slackId`를 받는다. 이메일 필드는 없다. 이메일을 slackId에 입력해도 자동으로 Slack 멤버 ID로 변환되지 않는다.
- `order-service/.../infrastructure/notification/SlackOrderNotificationSender.java`는 설정된 Webhook URL 한 곳으로 보낸다. 현재 payload는 일반 텍스트이고 입력을 이스케이프한다. 담당자 멘션이나 DM 수신자 선택은 하지 않는다.
- `OrderCreatedEvent`에는 업체·상품·수량·요청사항이 있고 배송 구간 담당자·수신 이메일은 없다. 개인 알림을 추가하려면 어떤 구간 담당자에게 언제 보낼지와 담당자 조회 계약을 먼저 정한다.
- 주문 생성 완료 알림이라면 기존 커밋 후 비동기 경계를 유지한다. 배송 생성 직후에 보냈다가 이후 주문 트랜잭션이 실패하는 경우를 구분해야 한다.
- 현재 UI 테스트 서버의 Slack Webhook은 비활성화되어 있다. 이번 작업은 질문 답변과 UI 연결이며 실제 Slack 메시지 전송·스코프 변경은 하지 않았다.

현재 범위에서는 채널 알림과 저장된 Slack 멤버 ID를 유지하는 구성이 간단하다. 개인 DM이 필요해지면 봇 발송 어댑터를 추가하고, 이메일 입력을 원하면 이메일→ID 조회·검증을 더한다. 사용자 API·저장 필드 변경은 user 담당자와 합의한다.

## 면접 포인트

- Webhook과 Web API의 차이는 무엇인가? Webhook은 사전에 설정된 발송 경로가 중심이고, Web API는 토큰과 스코프로 조회·수신자 선택·발송을 조합한다.
- 알림 대상의 이메일과 Slack ID 중 무엇을 저장할 것인가? 이메일은 사용자에게 익숙하지만 조회·권한이 필요하다. 실제 Slack 발송에는 해당 워크스페이스의 멤버 ID를 사용하고, 워크스페이스가 다르면 별도 매핑이 필요하다.
- 주문 알림 실패가 주문 실패인가? 주문 커밋 이후의 알림 실패는 별도로 처리하며 이미 완료된 주문을 알림 장애로 취소하지 않는다.

## 참고

2026-10-15에 공식 문서와 현재 저장소 코드를 확인했다.

- [Slack Incoming Webhooks](https://docs.slack.dev/messaging/sending-messages-using-incoming-webhooks/)
- [users.lookupByEmail](https://docs.slack.dev/reference/methods/users.lookupByEmail/)
- [conversations.open](https://docs.slack.dev/reference/methods/conversations.open/)
- [chat.postMessage](https://docs.slack.dev/reference/methods/chat.postMessage/)
- [기존 Webhook 설정 노트](2026-10-15-slack-incoming-webhook-setup.md)
- [커밋 후 비동기 알림](2026-10-15-order-notification-after-commit-async.md)
