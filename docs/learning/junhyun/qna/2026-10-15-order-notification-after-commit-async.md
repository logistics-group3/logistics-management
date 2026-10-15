---
title: 주문 Slack 알림은 왜 트랜잭션 커밋 후 비동기로 보내는가
date: 2026-10-15
updated: 2026-10-15
asker: Junhyun An
level: 중급
tags: [spring, transaction, event, async, slack]
---

# 주문 Slack 알림은 왜 트랜잭션 커밋 후 비동기로 보내는가

## 질문

> 다음은 주문 생성 후 Slack 알림 구현해보자. 트랜잭션 커밋 후 비동기로 보내는 이유도 설명하면서 진행해줘.

## 짧은 답

커밋 후에 보내야 최종 저장에 실패한 주문의 알림이 나가지 않는다.
비동기로 보내야 Slack 응답을 기다리느라 주문 응답이 늦어지지 않는다.
두 장치는 목적이 다르므로 함께 필요하다. 알림 오류는 주문 성공을 바꾸지 않는다.
현재 메모리 이벤트 방식은 프로세스 종료나 전송 실패 때 알림 유실을 허용한다.

## 자세히

`saveAndFlush`는 SQL을 DB로 보내는 단계이고, COMMIT은 트랜잭션을 최종 확정하는 단계다. 예를 들어 지연 UNIQUE 제약은 flush가 성공한 뒤 커밋에서 실패할 수 있다.
이 사이에 Slack을 호출하면 알림은 이미 나갔는데 주문은 없는 상황이 된다. DB 롤백은 외부 HTTP 전송까지 되돌려 주지 않는다.

| 방식 | 발송 시점 | 주문 응답에 미치는 영향 |
|---|---|---|
| 저장 중 직접 호출 / 일반 EventListener | 기본적으로 커밋 전 | Slack 대기 동안 요청·트랜잭션 자원을 잡고 있을 수 있다 |
| Async만 적용 | 작업이 즉시 시작해 커밋 전일 수 있음 | 요청 대기는 줄지만 롤백된 주문을 알릴 수 있다 |
| AFTER_COMMIT만 적용 | 커밋 성공 후 | Slack을 같은 스레드에서 기다리면 응답이 늦다 |
| AFTER_COMMIT + Async | 커밋 성공 후 별도 스레드 | 주문 응답은 Webhook 완료를 기다리지 않는다 |

Spring의 `@Async`는 기본적으로 프록시를 통한 호출을 가로챈다. 같은 클래스의 메서드를 직접 호출하면 프록시를 지나지 않아 비동기가 되지 않는다.
이 구현은 AFTER_COMMIT 리스너가 별도 전송 빈을 부른다. 작업 대기열이 가득 차면 제출 단계에서 실패하므로 리스너도 예외를 처리한다. 전송 빈은 실제 HTTP 실패를 따로 기록한다.

AFTER_COMMIT 이벤트는 활성 트랜잭션 안에서 발행해야 한다. 기본 `fallbackExecution=false`이므로 트랜잭션 밖 이벤트는 처리하지 않는다.
이벤트에는 값만 복사한다. 원래 요청 스레드의 JPA Entity·ThreadLocal·인증 헤더를 다른 스레드로 넘겨 쓰지 않는다.

## 이 프로젝트에서는

- `OrderWriter.save`의 독립 트랜잭션에서 이벤트를 등록하고 `OrderCreatedEventListener`에서 커밋 성공만 받는다.
- `SlackOrderNotificationSender`가 전용 풀(2개 스레드·100개 큐)에서 연결 1초·읽기 2초 제한으로 전송한다.
- 주문은 201을 유지한다. Slack 실패·큐 포화 때문에 배송 삭제 보상을 호출하지 않는다.
- Webhook URL은 비밀값이므로 예외 메시지·스택·응답 본문 대신 주문 ID·HTTP 상태·예외 종류만 기록한다.
- 알림의 업체·상품명과 요청사항을 이스케이프해 사용자 입력이 Slack 멘션으로 해석되는 것을 막는다.
- 상세 파일·검증·실행 절차는 [스텝 39](../steps/step-39-order-slack-after-commit.md)에 있다.

## 면접 포인트

- 커밋 후 비동기면 전달이 보장되는가? 아니다. JVM 메모리 이벤트이며, 프로세스 종료·전송 실패·포화 시 유실될 수 있다. 영속적인 재처리가 필요하면 주문과 같은 트랜잭션에 아웃박스를 저장하고 전달·중복 처리 정책을 추가한다.
- 왜 ThreadPool을 제한하는가? 느린 외부 요청이 몰려도 스레드와 메모리가 무한히 늘어나지 않게 하기 위해서다. 이 부가 알림은 포화 시 실패를 기록하며 주문 응답을 계속 처리한다.

## 참고

- [Spring 6.2 트랜잭션 이벤트](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/event.html)
- [Spring 6.2 비동기 실행과 스레드 풀](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Slack Incoming Webhooks](https://docs.slack.dev/messaging/sending-messages-using-incoming-webhooks/)
- [Slack 텍스트 이스케이프·서식 비활성화](https://docs.slack.dev/messaging/formatting-message-text/)
- [SA 6.7·7.1](../../../SA/sparta-logistics-sa.md)
