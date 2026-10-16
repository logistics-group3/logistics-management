---
title: Zipkin Find a trace 화면은 어떻게 보나
date: 2026-10-16
updated: 2026-10-16
asker: Junhyun An
level: 기초
tags: [zipkin, distributed-tracing, eureka, observability]
---

# Zipkin Find a trace 화면은 어떻게 보나

## 질문
Zipkin의 Find a trace 화면은 어떻게 보나. 결과 목록이 API 요청을 하나씩 보여 주는 것 같지 않다. 조건 없이 조회하면 `order-service: http get`, `order-service: http put`처럼 서비스마다 GET·PUT이 짝지어 나오고 Spans는 모두 1이다.

## 짧은 답
- 목록의 한 줄은 **trace 하나**(외부 요청 하나 또는 배경 작업 하나)다. 조회 기간 안에서 가장 최근 trace를 `limit`개(기본 10개)까지 보여 준다.
- 조건 없이 조회하면 보이는 `http get`·`http put`은 API 요청이 아니다. 각 서비스의 **Eureka 클라이언트가 5초마다 보내는 호출**이다. 서비스 5개가 5초마다 2건씩 만들어서 최근 10개를 5초 만에 다 채운다.
- 실제 API 요청은 모두 Gateway로 들어오므로 **`serviceName=gateway-service`로 거르면** 요청 하나가 한 줄로 나온다.
- 특정 요청 하나를 찾을 때는 로그 줄의 traceId를 오른쪽 위 검색창에 넣는 방법이 가장 빠르다.

## 자세히

### 1. 목록 한 줄 읽기
| 열 | 뜻 |
|---|---|
| Root | trace의 첫 span. `서비스명: span 이름` 형식이다 |
| Start Time | 첫 span이 시작한 시각 |
| Spans | 이 trace에 들어 있는 span 수. 1이면 다른 서비스로 이어지지 않은 단독 호출이다 |
| Duration | trace 전체가 걸린 시간. 막대 길이는 목록 안에서의 상대 비교다 |
| 펼치기(∨) | 이 trace에 참여한 서비스와 서비스별 span 수 |
| SHOW | trace 상세 화면(타임라인)으로 이동 |

정렬(위 화면의 `↓ Duration`)은 서버가 돌려준 결과 안에서만 바꾼다. 서버는 조건에 맞는 trace 중 **최근 것부터 `limit`개**만 준다. 그래서 오래 걸린 요청이 있어도 최근 10개 밖이면 Duration으로 정렬해도 보이지 않는다.

### 2. GET·PUT 짝의 정체: Eureka 클라이언트의 배경 호출
2026-10-16 로컬 Zipkin에서 최근 trace 200개를 첫 span 기준으로 묶어 보니 전부 다음 두 종류였다.

| span | 실제 주소 | 하는 일 |
|---|---|---|
| `http put` | `PUT http://eureka-server:8761/eureka/apps/ORDER-SERVICE/<인스턴스ID>?status=UP...` | heartbeat. "나 아직 살아 있다"를 5초마다 알린다(`eureka.instance.lease-renewal-interval-in-seconds`) |
| `http get` | `GET http://eureka-server:8761/eureka/apps/delta` | 다른 서비스 목록의 바뀐 부분을 5초마다 받아 온다(`eureka.client.registry-fetch-interval-seconds`) |

- Eureka 클라이언트는 내부에서 Spring이 계측한 HTTP 클라이언트를 쓴다. 그래서 이 호출도 CLIENT span으로 기록된다.
- 외부 요청에서 시작한 것이 아니라서 부모가 없다. 그래서 span 하나짜리 trace가 된다.
- eureka-server에는 추적 라이브러리가 없어 받는 쪽 span이 없다. 그래서 Spans가 1이다.
- Eureka 기본 주기는 둘 다 30초다. 이 프로젝트는 새로 뜬 서비스를 빨리 찾도록 `config-repo/application.yml`에서 5초로 줄였다. order-service의 heartbeat 간격을 실제로 재 보니 5.0초였다.
- user·hub·company·order·delivery 5개 서비스가 5초마다 2건씩, 초당 2건을 만든다. 최근 200건이 100초 분량이었다.
- gateway-service의 Eureka 호출은 기록되지 않았다. 그래서 Gateway로 거르면 이 잡음이 빠진다.

### 3. 실제 API 요청만 보기
화면 위 검색 줄의 `+`로 조건을 추가한다. 조건은 `and`로 묶인다.

| 조건 | 쓰는 때 | 예 |
|---|---|---|
| `serviceName` | 그 서비스가 참여한 trace만 | `gateway-service`면 외부 API 요청만 남는다 |
| `remoteServiceName` | 그 서비스를 부른 trace만 | `company-service` |
| `spanName` | span 이름이 같은 것만 | `http post` |
| `tagQuery` | 태그 값으로 거름 | `http.url=/api/v1/orders`, `status=404`, `error` |
| `minDuration` | 이 시간보다 오래 걸린 것만 | `100ms` |

- 오른쪽의 조회 기간(기본 15분)과 limit도 함께 본다. 테스트 직후가 아니면 기간을 늘린다.
- Gateway가 받은 요청의 첫 span 이름은 경로 없이 `http get`, `http post`로만 나온다. Spring Cloud Gateway는 컨트롤러 매핑이 아니라 라우트로 요청을 넘겨서, 경로 템플릿 태그 `uri`가 `UNKNOWN`이 되기 때문이다. 실제 경로는 `http.url` 태그나, 그 아래 하위 서비스 SERVER span의 이름(`http get /api/v1/deliveries`)에서 본다.

### 4. 특정 요청 하나 찾기
1. 서비스 로그 줄의 `[traceId-spanId]`에서 앞 32자(traceId)를 복사한다.
2. Zipkin 오른쪽 위 `Search by trace ID` 칸에 붙여 넣는다.
3. 검색 조건과 무관하게 그 trace의 상세 화면이 바로 열린다.

`script/api-test.sh trace <traceId>`를 쓰면 같은 traceId의 로그 줄과 Zipkin 링크를 함께 볼 수 있다.

### 5. 상세 화면(SHOW) 읽기
- 왼쪽 나무는 span의 부모·자식 관계다. 들여쓰기가 한 단계 깊으면 바로 위 span이 부른 호출이다.
- 가로 막대는 trace 시작부터의 시작 시각과 걸린 시간이다. 부모 막대 안에서 자식 막대가 시작하는 위치를 보면 어디서 시간이 갔는지 알 수 있다.
- span을 누르면 오른쪽에 태그(`http.url`, `status`, `exception`, `error`)와 이벤트 시각이 나온다.
- Gateway trace 하나에는 `security filterchain before`, `authorize exchange`처럼 Spring Security가 만든 span도 함께 들어 있다. HTTP 호출만 보려면 `kind`가 SERVER·CLIENT인 span을 따라간다.

## 이 프로젝트에서는
- Eureka 배경 호출은 계속 쌓이므로 Find a trace에서는 `serviceName=gateway-service`부터 건다.
- 이 잡음을 아예 기록하지 않으려면 MVC 서비스 공통 설정에 Eureka 주소로 가는 HTTP 클라이언트 관측을 끄는 `ObservationPredicate`를 두는 방법이 있다. 서비스 전체에 영향을 주는 공통 영역 변경이라 팀 PR로 다룬다(2026-10-16 기준 적용하지 않음).

  ```java
  // 예시: /eureka/ 로 가는 클라이언트 호출은 관측(span)을 만들지 않는다
  @Bean
  ObservationPredicate skipEurekaClientObservation() {
      return (name, context) -> !(context instanceof ClientRequestObservationContext client
              && client.getCarrier() != null
              && client.getCarrier().getURI().getPath().startsWith("/eureka/"));
  }
  ```

## 면접 포인트
- **trace와 span의 차이**: trace는 요청 하나의 전체 흐름이고 span은 그 안에서 한 서비스가 한 일 하나다. 목록은 trace 단위, 상세 화면은 span 단위로 본다.
- **추적 데이터의 잡음은 어떻게 줄이나**: 헬스 체크, 서비스 디스커버리 heartbeat처럼 사용자 요청과 무관한 호출을 관측 단계에서 제외한다(Spring은 `ObservationPredicate`). 샘플링 비율을 낮추면 저장량은 줄지만 잡음과 실제 요청이 같은 비율로 줄어 해결책이 아니다.

## 참고
- 2026-10-16 로컬 Zipkin `/api/v2/traces` 조회 결과(최근 200건의 첫 span 집계, order-service heartbeat 간격 측정, `serviceName=gateway-service` 조회)
- Zipkin Lens 소스: `zipkin-lens/src/components/DiscoverPage/DiscoverPageContent.tsx`(검색 조건 `tagQuery`가 API의 `annotationQuery`로 바뀐다), `lookback.ts`(조회 기간 선택지)
- 관련 노트: [여러 서비스를 거치는 요청 흐름을 한 번에 보려면](2026-10-16-distributed-tracing-local.md), [Zipkin Dependencies 화면에서 서비스 사이를 흐르는 점의 개수는 무엇을 뜻하나](2026-10-16-zipkin-dependencies-particles.md)
