---
title: 리소스 부족이나 장애가 생기면 Slack 알림을 무엇으로 보내나
date: 2026-10-16
updated: 2026-10-16
asker: Junhyun An
level: 중급
tags: [prometheus, grafana, alerting, slack, monitoring]
---

# 리소스 부족이나 장애가 생기면 Slack 알림을 무엇으로 보내나

## 질문

그그그 슬랙으로 알림오는거! 리소스 부족이나 뭐 문제생기면 그거 뭘로하지

## 짧은 답

장애 감지와 통지를 맡는 기능을 Alerting이라고 한다.
이 프로젝트에는 Prometheus → Grafana Alerting → Slack 구성을 추천한다.
Prometheus는 수치를 수집하고 Grafana가 조건을 평가해 Slack Contact point로 보낸다.
Prometheus가 알림 규칙을 평가하고 별도 Alertmanager가 알림을 묶어 보내는 구성도 가능하다.

## 자세히

### 구성 선택

| 구성 | 수집 | 조건 판단 | 묶음·반복 제어와 발송 | 이 프로젝트의 선택 기준 |
|---|---|---|---|---|
| Grafana Alerting | Prometheus | Grafana | Grafana의 내장 Alertmanager와 Slack Contact point | 지금 추가 중인 Grafana에서 규칙과 알림을 함께 관리하기 좋다 |
| 별도 Alertmanager | Prometheus | Prometheus alert rules | 별도 Alertmanager | Prometheus 쪽에 규칙을 두고 알림 운영을 Grafana와 분리하고 싶을 때 선택한다 |

Grafana는 Slack API 토큰 또는 Incoming Webhook URL을 지원한다. 고정된 운영 알림 채널 하나라면 Webhook으로 시작하기 쉽다.
추천 경로는 지표 수집 → 알림 조건·지속 시간 설정 → Slack 수신처 설정 → 알림과 복구 통지 확인이다.
관리 UI의 세부 메뉴명은 Grafana 버전에 따라 달라질 수 있다.

### 처음 만들 알림

다음 값은 초기 실험 예시다. 실제 부하와 정상 상태를 관찰해 조정한다.

| 상황 | 초기 조건 예시 | 추가 고려 |
|---|---|---|
| 서비스 수집 실패 | 수집 대상의 up=0이 1분 지속 | 수집 실패와 실제 업무 장애를 구분한다. Eureka에서 대상 자체가 사라지면 No Data·기대 서비스 누락도 검사한다 |
| JVM 메모리 압박 | 최대 힙 대비 사용량 85% 초과가 5분 지속 | GC 후에도 계속 높은지 본다. JVM 힙과 컨테이너 전체 메모리는 별도 지표다 |
| 서버 오류 증가 | 최근 5분간 5xx 비율 5% 초과 | 최소 요청 수 조건을 함께 둬 요청 한두 건으로 알림이 생기지 않게 한다 |
| 응답 지연 | p95 응답 시간이 목표치를 5분간 초과 | 서비스·API별 목표치와 트래픽을 기준으로 정한다 |
| 호스트·컨테이너 자원 부족 | CPU·메모리·디스크 임계치 지속 초과 | 필요한 exporter와 메모리 제한·측정 대상을 먼저 확인한다 |

애플리케이션·JVM 지표는 Spring Actuator와 Micrometer로 수집한다.
컨테이너 자원 지표는 cAdvisor, Linux 호스트 지표는 Node Exporter로 보강할 수 있다.
macOS의 Docker 환경에서는 컨테이너·Linux VM·Mac 호스트 중 무엇을 측정하는지 먼저 구분한다.

### 알림을 운영할 때

- 주문 알림과 운영 알림은 수신처를 분리한다. 기존 주문용 SLACK_WEBHOOK_URL의 목적은 유지하고 운영용 비밀값을 별도로 둔다.
- 같은 장애는 서비스·원인별로 묶고 반복 간격을 둔다. 정상으로 돌아오면 복구 알림도 보낸다.
- 메시지에는 서비스, 문제, 현재 값, 발생 시각, 대시보드·대응 절차 링크를 넣는다.
- Webhook URL을 Git·로그·메트릭 라벨에 넣지 않는다.
- 규칙과 수신처 설정은 파일 프로비저닝 등 재현 가능한 형태로 관리한다. 현재 Compose는 Grafana 저장 볼륨을 두지 않아 UI 변경만으로 영구 보존을 기대하면 안 된다.
- 전체 호스트 장애까지 다루려면 별도 위치의 외부 감시가 필요하다. 같은 호스트의 수집기와 알림기가 함께 멈추면 그 구성만으로는 통지를 보장하지 못한다.

보안 고도화와 연결하려면 로그인 실패 급증·권한 거부 급증·관리자 권한 변경도 별도 보안 이벤트와 지표로 관측할 수 있다.

## 이 프로젝트에서는

2026-10-16 현재 작업 트리에는 [Compose](../../../../docker-compose.yml)의 Prometheus·Grafana 서비스와 [Prometheus 수집 설정](../../../../infra/prometheus/prometheus.yml)이 추가돼 있다.
Grafana 프로비저닝은 데이터소스와 대시보드 구성이 확인됐고, 조회 시점에는 저장소에 Slack Contact point나 alert rule 설정은 없었다.
Prometheus는 Eureka에서 서비스 목록을 받아 15초마다 메트릭을 수집하도록 작성돼 있다. 기동·수집 성공을 이번 상담에서 검증하지는 않았다.

추천하는 다음 구현 범위는 서비스 수집 실패와 JVM 메모리 알림 두 개, 운영용 Slack 수신처, 복구 알림이다.
검증 시에는 전용 환경에서 조건을 만들어 알림·복구·반복 제어를 각각 확인한다. 실제 Slack 전송은 별도 실행 작업이며 이번 상담에서 보내지 않았다.
이 노트는 구성 제안이다. 신규 도구 채택이나 팀 운영 정책을 확정한 ADR은 아니다.

## 면접 포인트

- Prometheus와 Alertmanager는 어떻게 다른가? 전자는 지표 수집과 규칙 평가, 후자는 알림의 그룹화·중복 억제·라우팅·발송을 맡는다.
- 메모리 사용률을 한 번 넘었다고 바로 알리지 않는 이유는 무엇인가? 일시적인 부하와 지속적인 문제를 구분해 불필요한 알림을 줄이기 위해서다.
- 서비스 목록에서 대상이 사라지면 up=0만으로 충분한가? 수집 대상 자체가 없어질 수 있으므로 데이터 부재와 기대 대상 수를 함께 다뤄야 한다.

## 참고

- [Grafana Alerting의 Slack 연동](https://grafana.com/docs/grafana/latest/alerting/configure-notifications/manage-contact-points/integrations/configure-slack/)
- [Prometheus Alerting 개요](https://prometheus.io/docs/alerting/latest/overview/)
- [Prometheus와 cAdvisor로 컨테이너 지표 수집](https://prometheus.io/docs/guides/cadvisor/)
- [Prometheus Node Exporter로 호스트 지표 수집](https://prometheus.io/docs/guides/node-exporter/)
