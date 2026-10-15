# 40. Slack 실제 연결과 컨테이너 설정 반영

- 날짜: 2026-10-15
- 브랜치: `feat/order-create`
- 코드 기준: `7a3f93f feat(order): 커밋 후 비동기 Slack 알림 구현`
- 변경 범위: 로컬 config-server·order-service 실행 환경과 기록. 코드·DB 스키마 변경은 없다.

## 적용한 것

사용자가 `디스이스스파르타`에 앱을 만들고 `.env`의 `SLACK_WEBHOOK_URL`을 설정했다고 알렸다. Compose가 해석한 값의 존재와 HTTPS Slack Webhook 형식을 확인했으며 URL 자체는 출력하지 않았다.
앞서 빌드한 두 이미지를 다음 순서로 적용했다.

```sh
docker compose up -d --no-deps --no-build --wait --wait-timeout 90 config-server
docker compose up -d --no-deps --no-build --wait --wait-timeout 90 order-service
```

`config-repo`는 config-server 이미지 안에 포함되므로 config-server를 먼저 갱신했다. order는 기동하면서 새 설정을 읽는다.
`.env`의 변경은 컨테이너 생성 시 전달되므로 단순 restart가 아니라 up으로 새 컨테이너를 만들었다. `--no-deps`로 의존 DB와 다른 서비스의 재생성을 막았다.

## 검증 결과

| 확인 항목 | 결과 |
|---|---|
| 실제 반영 이미지 | config-server·order 모두 앞서 준비한 이미지 ID와 일치 |
| 환경 변수 | 주문 컨테이너의 Webhook 값이 Compose가 해석한 `.env` 값과 일치 |
| Config Server | `slack.webhook-url`의 환경 변수 자리표시자, 연결 1초·읽기 2초 설정 제공 확인 |
| 기동 상태 | config-server·order-service actuator `UP` |
| Gateway | 주문 OpenAPI `200`, O-01 POST 경로 존재, 미인증 주문 요청 `401 UNAUTHORIZED` |
| 교체 범위 | config-server·order-service의 컨테이너만 교체됨 |
| 기존 서비스·DB | 나머지 7개 컨테이너의 ID·시작 시각, PostgreSQL 볼륨 연결 유지 |
| 비밀값 | `.env` Git 무시 확인, 작업 전후 파일 해시 동일, 새 주문 서비스 로그 최근 250줄에 Webhook 실제 값 없음 |
| 문서 검사 | docs-check·error-codes-check·git diff --check·변경 문서 8개 로컬 링크 검사 통과, 변경 파일에 실제 Webhook 값 없음 |

## 실제 Slack 테스트

사용자가 아래 메시지 1건의 발송을 명시적으로 승인했다.

> [연동 테스트] 주문 알림 연결 확인입니다. 실제 주문이 아닙니다.

실행 중인 **order-service 컨테이너에서** 등록된 Webhook으로 JSON을 한 번 POST했다. URL은 명령 인자나 로그에 노출하지 않고 curl의 표준 입력 설정으로 전달했다. 재시도와 리다이렉트는 사용하지 않았다.
결과는 **HTTP 200**, 응답 본문 **`ok`**, curl 종료 코드 **0**이다. Slack의 발송 수락을 확인했으며 채널 UI에서 메시지를 별도로 조회하지는 않았다.

이번 테스트는 Webhook의 실제 연결·발송 검증이다. 실제 주문을 만들지 않았으므로 Controller → 회사·배송 실제 API → DB 커밋 → 이벤트 → Slack 전체 흐름을 검증한 것은 아니다.
커밋 경계·비동기·알림 실패 처리는 [스텝 39](step-39-order-slack-after-commit.md)의 주문 테스트 108개로 확인한 상태다. 이번에는 코드가 바뀌지 않아 그 결과를 재사용하고 실행 환경을 검증했다.

## 직접 해보기

1. `docker compose ps`에서 config-server와 order-service가 실행 중인지 확인한다.
2. `docker compose exec -T order-service curl -fsS http://localhost:8084/actuator/health`로 `UP`을 확인한다.
3. Webhook 값을 바꾸면 order-service를 `up -d --no-deps --no-build --wait`로 다시 만든다. Config Server 설정 파일도 바뀌었다면 이미지를 다시 빌드하고 config-server부터 반영한다.
4. 실제 메시지를 다시 보낼 때는 테스트 채널·내용·발송 횟수를 먼저 정한다. URL이나 `.env` 전체를 화면·로그·문서에 출력하지 않는다.

## 남은 검증

company I-07·I-08, delivery I-09·I-13의 실제 구현과 테스트 데이터가 준비되면 Gateway를 통한 정상 주문 생성과 실패 보상, 생성 이벤트의 실제 Slack 알림을 함께 확인한다.
이번 실행 기록은 후속 요청에서 `af0ba34 docs(order): Slack 실제 연동과 설정 검증 기록`으로 커밋했다. 푸시는 수행하지 않았다.
