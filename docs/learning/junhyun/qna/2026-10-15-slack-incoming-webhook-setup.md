---
title: 주문 알림용 Slack은 새 채널이 필요한가, Webhook은 어떻게 연결하는가
date: 2026-10-15
updated: 2026-10-15
asker: Junhyun An
level: 기초
tags: [slack, webhook, configuration, docker]
---

# 주문 알림용 Slack은 새 채널이 필요한가, Webhook은 어떻게 연결하는가

## 질문

> 커밋하고 slack 세팅부터 하자!! 새 채널을 파야하나?

후속 질문: "내 소유 워크스페이스여야 되는거지??" 사용자는 Slack 설정은 직접 진행하겠다고 선택했다.

## 짧은 답

새 채널은 필수가 아니다. 기존 채널도 Incoming Webhook의 발송 대상으로 선택할 수 있다.
개발 테스트 알림이 쌓이므로 `#logistics-order-dev` 같은 전용 채널을 추천한다. 채널 이름은 아직 제안이다.
채널을 준비한 뒤 Slack 앱의 Incoming Webhooks를 활성화하고 해당 채널에 연결한다.
발급된 URL은 비밀값이므로 `.env`에만 저장하고 Git·문서·채팅에 남기지 않는다.

## 자세히

| 구성 | 역할 |
|---|---|
| 워크스페이스 | 프로젝트 팀이 사용하는 Slack 공간 |
| 채널 | 주문 알림을 받을 대화방 |
| Slack 앱 | 메시지를 보내는 연동의 주체 |
| Incoming Webhook URL | 앱이 선택된 채널에 메시지를 보낼 수 있는 비밀 주소 |

Webhook URL은 특정 채널에 연결된다. 주문 요청마다 채널을 지정하는 구조가 아니다.
비공개 채널에 연결하려면 설정하는 사용자가 그 채널에 먼저 참여해야 한다. 워크스페이스 정책에 따라 앱 설치에 관리자 승인이 필요할 수 있다.

### 본인 소유 워크스페이스가 필요한가

소유자일 필요는 없다. Slack은 기본적으로 일반 멤버도 앱을 설치할 수 있지만, 워크스페이스에서 앱 승인 정책을 켜면 소유자나 앱 관리 권한자의 승인이 필요하다.
채널 생성과 앱 설치는 별개의 권한이다. 팀원들과 알림을 함께 볼 목적이면 팀 워크스페이스를 사용하고, 설치가 제한되어 있으면 승인 절차를 따른다.
개인 개발 테스트만 빠르게 해 볼 목적이면 본인이 관리하는 워크스페이스가 편하다. 아래 후속 확인에서 팀 워크스페이스의 설치 제한을 확인했다.

### 설정 순서

1. 프로젝트 워크스페이스에서 사용할 채널을 정한다. 기존 채널을 쓰거나 개발 알림용 채널을 만든다.
2. [Slack 앱 설정](https://api.slack.com/apps)에 해당 워크스페이스 계정으로 로그인한다. 새 앱을 만들거나 사용할 기존 앱을 선택한다. 새 앱 이름의 제안은 `Logistics Order Dev`다.
3. 앱의 `Incoming Webhooks`에서 `Activate Incoming Webhooks`를 켠다.
4. `Add New Webhook to Workspace`에서 대상 채널을 선택하고 설치·발송 권한을 확인한다. 권한이 허용되면 URL이 발급된다.
5. 로컬 `.env`의 `SLACK_WEBHOOK_URL`에 URL을 저장한다. 앱 토큰·Client Secret을 여기에 넣는 것이 아니다.
6. config-server와 order-service의 새 이미지·환경 변수를 반영한다. 프로젝트는 설정 자동 갱신을 사용하지 않으므로 기동 시 다시 읽게 해야 한다.
7. 대상 채널 테스트 발송 요청 후 한 번의 테스트 메시지로 연결을 확인한다. 실제 주문 이벤트 검증은 company·delivery 실제 API와 데이터가 준비된 뒤 따로 확인한다.

## 이 프로젝트에서는

- 기능은 `7a3f93f feat(order): 커밋 후 비동기 Slack 알림 구현`에 커밋했다. 주문 테스트 108개 통과 결과와 전체 빌드 성공은 [스텝 39](../steps/step-39-order-slack-after-commit.md)에 있다.
- `.env`는 Git 무시 대상이며 초기 확인 당시 `SLACK_WEBHOOK_URL`은 비어 있었다. 이후 사용자가 URL을 설정했고 아래 실제 연결 단계에서 반영을 확인했다. 실제 값은 출력하지 않았다.
- `docker compose build config-server order-service`로 두 이미지를 준비한 뒤, 아래 실제 연결 단계에서 대상 컨테이너를 교체하고 테스트 메시지 1건을 전송했다.
- 처음 확인한 Codex 브라우저는 로그인 전이었다. 이후 사용자가 직접 설정하겠다고 선택했고, 후속 요청에서 Chrome의 권한 확인을 다시 요청했다. 아래 결과는 Chrome에서 확인한 상태다.
- 이미지 적용 명령은 스텝 39의 `직접 해보기`에 있다. `.env` 변경은 `restart`만으로 반영되지 않는다.

### 2026-10-15 Chrome에서 확인한 설치 제한

사용자가 선택한 대상은 `2026 내일배움캠프`다. 로그인된 앱 관리 화면에서 `Create New App → Blank app → Continue`를 열고 워크스페이스를 선택했다. 이름 입력·앱 생성·설치·Webhook 발급은 진행하지 않았다.

- 선택 직후 앱 설치 권한이 없으며 관리자나 소유자가 설치해야 한다는 안내가 표시됐다. 앱 자체는 만들 수 있다는 문구도 있었다.
- 이후 화면이 갱신되어 `2026 내일배움캠프 has reached its app limit` 안내가 표시됐다. 최종 스크린샷에서 앱 설치 한도 초과를 확인했다.
- 현재 상태로 신규 Webhook 앱 설치를 완료할 수 없다. 관리자가 설치 권한과 앱 한도를 함께 확인해야 한다. 이미 설치된 앱의 재사용 가능 여부와 정확한 요금제·설치 수는 조사하지 않았다.
- 개발 테스트를 먼저 진행하려면 사용자가 관리하는 테스트 워크스페이스를 선택할 수 있다. 기존 팀 앱의 삭제·교체는 하지 않는다.

사용자가 보낸 `#incoming_webhooks_programmatic` 절은 앱 배포 시 OAuth 과정에서 Webhook URL을 발급하는 설명이다. 이번처럼 한 워크스페이스·채널을 연결할 때는 앱 설정 화면에서 수동 발급하는 앞부분의 절차로 충분하다.

### 팀원까지 함께 쓰려면 어느 워크스페이스가 좋은가

후속 질문: "그럼 어디에다가 하는 게 낫지? 내 개인 워크스페이스에 하고 팀원들 초대해야하나?"

팀원도 알림을 확인할 목적이면 사용자가 프로젝트 전용 워크스페이스를 새로 만들고 팀원을 초대하는 방식을 추천했다. 워크스페이스를 만든 사용자는 Primary Owner가 된다.
기존 개인 공간에 일반 멤버를 초대하면 그 공간의 다른 공개 채널도 찾아보고 참여할 수 있다. 개인 작업과 팀 알림을 함께 두기보다 프로젝트용 공간으로 관리하는 편이 낫다.

제안 구성은 워크스페이스 `Sparta Logistics`, 채널 `#logistics-order-dev`, 앱 `Logistics Order Dev`다. 이름과 생성은 아직 확정하지 않았다.
처음에는 사용자 혼자 채널과 Webhook을 연결해 확인하고, 팀원 초대는 연결 검증 후 진행해도 된다. 단순 개인 테스트라면 기존 개인 워크스페이스를 사용하고 초대를 생략해도 된다.
추가 워크스페이스에 참여해야 하는 부담은 있으므로 새 공간은 프로젝트 알림용으로 사용한다. 기존 개인 공간이 비어 있고 팀용으로 사용해도 괜찮다면 재사용할 수 있다.

### 새 워크스페이스 준비 결과

사용자가 `디스이스스파르타`를 만들었다고 알렸다. Chrome의 앱 생성 양식에서 새 워크스페이스가 목록에 표시되고 선택되는 것을 확인했다.
선택 후 스크린샷에도 기존 워크스페이스에서 보였던 설치 권한·앱 한도 경고가 없었다. 이는 현재 생성 양식의 관찰이며, 실제 앱 설치·Webhook 발급이 완료됐다는 뜻은 아니다.

사용자가 직접 설정하도록 Blank app 양식에서 워크스페이스만 선택해 두었다. 다음은 알림 채널 생성, 앱 이름 입력·생성, Incoming Webhooks 활성화, 대상 채널에 Webhook 연결이다. 기존 `Sparta Logistics`는 제안 이름이었고 실제 워크스페이스 이름은 `디스이스스파르타`다.

### 앱 생성 중 권한 스코프가 비어 보이는 경우

후속 질문: "권한 스코프 설정하고 싶은데 뭐가 아무것도 안뜨는ㄴ데?"

확인한 화면은 새 앱 생성 2/4단계의 `What it can do in Slack?`였다. `Bot scopes (0)` 아래는 이미 선택한 스코프 목록이므로 처음에는 `No bot scopes yet`가 표시된다.
상단 `Search to add new scopes`에 `incoming-webhook`을 입력하니 검색 결과가 나타났다. 해당 결과를 선택한 뒤 `Added incoming-webhook`, `Bot scopes (1)`과 `Post messages to specific channels in Slack` 설명을 확인했다.

현재 주문 구현은 발급된 Webhook URL로 JSON을 보내므로 `incoming-webhook` 하나를 사용한다. User scopes는 0개로 두며 채널 기록·사용자 정보 읽기 권한은 추가하지 않는다.
이 작업은 생성 양식에서 스코프를 선택한 단계다. 실제 앱 생성·워크스페이스 설치·Webhook URL 발급은 아직 완료하지 않았고, 사용자가 Continue로 다음 단계를 진행한다.

### 실제 환경 반영과 발송 확인

이후 사용자가 앱 생성과 `.env` 설정 완료를 알렸다. Webhook 형식을 값 노출 없이 검사하고 config-server·order-service에 새 이미지·환경 변수를 반영했다.
주문 컨테이너의 값이 `.env`의 Compose 해석 값과 같고 두 서비스 health는 UP이다. Gateway OpenAPI 200·미인증 401, 다른 7개 컨테이너·PostgreSQL 볼륨·`.env` 해시 유지를 확인했다.

사용자가 승인한 메시지 `[연동 테스트] 주문 알림 연결 확인입니다. 실제 주문이 아닙니다.`를 주문 컨테이너에서 1건 전송했다. Slack은 HTTP 200과 본문 `ok`를 반환했다.
실제 주문 데이터는 만들지 않았다. 팀 서비스 실제 API·데이터를 포함한 주문 이벤트 전체 검증과 Webhook 연결 검증은 구분한다. 자세한 근거는 [스텝 40](../steps/step-40-slack-live-connection.md)에 있다.

## 면접 포인트

- Webhook URL이 비밀값인 이유는 무엇인가? URL을 가진 주체가 연결된 채널에 메시지를 보낼 수 있기 때문이다.
- Webhook 발급과 알림 구현은 어떻게 다른가? 코드는 주문 이벤트와 HTTP 전송을 담당하고, Webhook은 실제 Slack 앱·채널에 대한 발송 권한과 목적지를 제공한다.

## 참고

- [Slack 공식 Incoming Webhooks 가이드](https://docs.slack.dev/messaging/sending-messages-using-incoming-webhooks/)
- [incoming-webhook 스코프](https://docs.slack.dev/reference/scopes/incoming-webhook/)
- [Slack 앱 설치 권한](https://slack.com/help/articles/202035138-Add-apps-to-your-Slack-workspace)
- [워크스페이스 앱 승인 정책](https://slack.com/help/articles/222386767-Manage-app-approval-for-your-workspace)
- [워크스페이스 생성과 소유자 역할](https://slack.com/help/articles/206845317-Create-a-Slack-workspace)
- [일반 멤버의 공개 채널 접근](https://slack.com/help/articles/205239967-Join-a-channel)
- [설정 보안 가이드](../../../guides/config-security.md)
- [주문 알림 구현 스텝](../steps/step-39-order-slack-after-commit.md)
