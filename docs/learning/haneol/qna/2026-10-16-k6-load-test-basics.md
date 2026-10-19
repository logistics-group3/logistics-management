---
title: k6는 무엇을 설치해야 하고, 부하 테스트 결과(p95, checks, dropped_iterations)는 어떻게 읽나
date: 2026-10-16
updated: 2026-10-16
asker: jihaneol
level: 기초
tags: [k6, load-test, percentile, docker-compose]
---

# k6는 무엇을 설치해야 하고, 부하 테스트 결과(p95, checks, dropped_iterations)는 어떻게 읽나

## 질문
k6를 쓰려면 뭘 깔아야 하나(JS인데)? 결과가 무슨 뜻인지 설명해 달라. `COMPOSE_FILE`에 파일 세 개를 쓰면 한 번에 다 올리는 것인가?

## 짧은 답
- k6는 Go로 만든 실행 파일이고 안에 자체 JS 엔진이 있다. Node·npm은 필요 없다. 맥은 `brew install k6`.
- 응답 시간은 평균이 아니라 p95·p99로 읽는다. `checks`는 기능이 맞게 응답했는지, `dropped_iterations`는 정한 속도대로 보냈는지를 알려 준다.
- `COMPOSE_FILE=a.yml:b.yml:c.yml`은 세 스택이 아니라 앞 파일을 기준으로 뒤 파일을 덮어쓴 설정 한 벌이다.

## 자세히

### k6 스크립트
- `import http from 'k6/http'` 같은 `k6/...` 모듈은 내장이다. npm 패키지와 Node API(`fs`, `require`)는 쓸 수 없다. 그래서 `lib/delivery.js`의 UUID도 직접 만든다.
- Docker로도 돌릴 수 있다(`grafana/k6`). 컨테이너 안에서 `localhost`는 자기 자신이라 `BASE_URL=http://host.docker.internal:8080`이 필요하다.

### 결과 읽기
| 지표 | 뜻 |
|---|---|
| `http_req_duration` p95 | 요청을 빠른 순으로 줄 세웠을 때 95번째. 95%가 이 안에 끝났다 |
| p99 | 가장 느린 1%를 뺀 나머지의 상한 |
| `checks` | 스크립트가 확인한 조건(예: 201이 왔나)의 통과율 |
| `http_req_failed` | 실패(4xx·5xx, 타임아웃) 비율 |
| `dropped_iterations` | VU가 모자라 정한 속도로 보내지 못한 요청 수. 0보다 크면 서버가 밀렸다는 신호이고, 그 결과는 비교에 쓰지 않는다 |

평균을 쓰지 않는 이유: 99건이 10ms, 1건이 10초면 평균은 약 110ms로 괜찮아 보인다. 하지만 누군가는 10초를 기다렸다.

arrival-rate 실행기(`constant-arrival-rate`, `ramping-arrival-rate`)는 서버가 느려져도 초당 요청 수를 유지한다(열린 모델). VU 수로 부하를 주면 서버가 느릴수록 요청도 줄어 병목이 가려진다.

### COMPOSE_FILE
`:`로 이은 파일을 순서대로 합친다. 뒤 파일은 앞 파일의 서비스 설정에 덮어쓰거나 더한다. 합친 결과는 `docker compose config`로 확인한다. `export`는 그 터미널에서만 유효하다.

## 이 프로젝트에서는
- 1분 시험 실행(2026-10-16, 맥, 장애 없음, 생성·조회 각 초당 20건): 2,402건, checks 100%, 실패 0%, dropped 없음. 배송 생성(부하 테스트 API, user 호출 2번 포함) p95 31ms·p99 61ms, 배송 목록 조회 p95 14ms·p99 33ms. 결과 파일은 `load-test/results/fault-smoke-20261016-214301.json`(저장소에 올리지 않음).
- 부하 테스트 API는 주문을 거치지 않고 Gateway → delivery로 배송 생성만 잰다(결정 로그 24). 주문 경로 전체는 `TARGET=order`.
- 덧붙임 파일: `docker-compose.loadtest.yml`(부하 테스트 API 켜기), `docker-compose.fault.yml`(Gateway 권한 캐시 10분, Tomcat 스레드 지표). 절차는 `delivery-service/docs/fault-isolation.md`.

## 면접 포인트
- "성능 지표로 평균 대신 p95를 쓰는 이유는?" → 평균은 느린 요청을 가린다. 사용자 체감과 SLO는 꼬리 지연(p95·p99)으로 본다.
- "부하 테스트에서 열린 모델과 닫힌 모델의 차이는?" → 닫힌 모델(VU 고정)은 서버가 느려지면 보내는 양도 줄어 병목을 숨긴다. 실제 트래픽은 서버 속도와 무관하게 들어오므로 도착률(arrival-rate)을 고정한다.

## 참고
- k6 실행기 문서: https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/
