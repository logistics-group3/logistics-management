---
title: Zipkin을 켜두면 서비스가 계속 요청을 보내는가
date: 2026-10-16
updated: 2026-10-16
asker: jihaneol
level: 중급
tags: [zipkin, micrometer-tracing, observability, spring-boot]
---

# Zipkin을 켜두면 서비스가 계속 요청을 보내는가

## 질문
zipkin 켜두면 계속 요청 보냄?

## 짧은 답
주기적으로 폴링하지 않는다. Zipkin 서버는 받기만 하는 수집기이고, 전송은 각 서비스가 한다. 서비스는 요청을 처리하면서 span이 생겼을 때만 그 span을 모아(batch) 비동기로 `POST /api/v2/spans`를 보낸다. 트래픽이 없으면 보낼 span이 없어 요청도 없다. 다만 헬스체크처럼 사람이 보내지 않는 요청도 span을 만들 수 있고, 샘플링 비율을 1.0으로 올리면 이런 요청까지 전부 전송된다.

## 자세히

### 누가 누구에게 보내나
- Zipkin 서버(`openzipkin/zipkin`, 9411)는 수동적인 수신자다. 서비스에 먼저 요청하지 않는다.
- 각 서비스의 Micrometer Tracing(Brave 또는 OpenTelemetry 브리지)이 span을 만들고, Zipkin reporter가 span을 Zipkin 서버로 push한다.

### 언제 span이 생기나
- 들어온 HTTP 요청(서버 span), 나가는 HTTP 호출(Feign, RestClient 등 클라이언트 span)처럼 Observation이 걸린 작업이 실행될 때 생긴다.
- 샘플링에서 빠진 trace는 span을 기록하지 않으므로 전송하지 않는다. Spring Boot 3.x의 기본값은 `management.tracing.sampling.probability=0.1`(10%)이다. 학습용으로 1.0을 많이 쓰는데, 그러면 모든 요청이 전송 대상이 된다.

### 어떻게 보내나
- reporter는 span을 메모리 큐에 쌓고 별도 스레드가 일정 주기(기본 약 1초)마다 묶어서 보낸다. 요청마다 Zipkin 호출이 하나씩 붙는 구조가 아니다.
- 전송은 비동기라 비즈니스 요청의 응답 시간에 직접 더해지지 않는다.
- 큐가 비어 있으면 보내지 않는다.

### 사람이 안 써도 생기는 span
- Docker healthcheck나 모니터링이 `/actuator/health`를 주기적으로 호출하면 그것도 서버 span이 된다. 샘플링 1.0이면 그대로 Zipkin에 쌓인다.
- Eureka heartbeat(기본 30초)나 레지스트리 조회가 추적 대상 HTTP 클라이언트를 쓰면 span이 생길 수 있다. 계측 여부는 클라이언트 구성에 따라 다르므로 Zipkin UI에서 실제로 보이는지 확인한다.
- 이런 잡음이 거슬리면 actuator 경로를 Observation에서 빼는 `ObservationPredicate` 빈을 등록하거나 샘플링 비율을 낮춘다.

### Zipkin 서버가 꺼져 있으면
- reporter가 전송에 실패하면 경고 로그를 남기고 해당 span을 버린다. 큐 크기에 상한이 있어 메모리가 계속 늘지 않고, 비즈니스 요청도 실패하지 않는다.
- 로그가 시끄러우면 추적을 끄는 설정(`management.tracing.enabled=false` 계열)을 쓴다. Boot 버전마다 속성 이름이 조금씩 다르므로 도입할 때 3.5 레퍼런스에서 정확한 이름을 확인한다.

## 이 프로젝트에서는
- 2026-10-16 기준 아직 도입하지 않았다. SA 1.3(고도화)과 1.4(선택 스택), 배포 구성표에 `zipkin (선택) :9411`로만 있다(`docs/SA/sparta-logistics-sa.md`). ADR 0013도 다음 단계로 Micrometer Tracing + Zipkin을 적었다.
- 도입하면 `docker-compose.yml`의 healthcheck가 주기적으로 actuator를 부르므로, 샘플링 비율과 actuator 제외 여부를 같이 정해야 Zipkin UI에 헬스체크 span만 가득 차는 일을 막을 수 있다.

## 면접 포인트
- "분산 추적을 켜면 성능 부담이 없나요?" 전송은 비동기 배치이고, 샘플링 비율로 수집량을 조절한다. 운영에서는 비율을 낮추거나 tail sampling을 쓴다.
- "Zipkin이 죽으면 서비스도 영향을 받나요?" 받지 않는다. reporter가 span을 버리고 경고만 남긴다. 추적 인프라는 비즈니스 흐름의 필수 의존이 아니어야 한다.

## 참고
- Spring Boot 레퍼런스 "Tracing" 절(`management.tracing.sampling.probability`, `management.zipkin.tracing.endpoint`)
- openzipkin/zipkin-reporter-java `AsyncReporter`(큐, 주기 전송, 실패 시 drop)
- 이 노트는 저장소에 Zipkin 설정이 없는 상태에서 문서 기준으로 작성했다. 실제 실행으로 확인하지 않았다.
