---
title: common과 common-core를 왜 나눴는지 쉽게 이해하기
date: 2026-10-15
updated: 2026-10-15
asker: Junhyun An
level: 기초
tags: [gradle, multi-module, spring-mvc, webflux, gateway, msa]
---

# common과 common-core를 왜 나눴는지 쉽게 이해하기

## 질문
팀 노트 "common과 common-core를 왜 나눴고, 합치는 것과 나누는 것 중 무엇이 나은가"(질문자 kej-2356)를 더 쉽게 설명해 달라.

## 짧은 답
- 서비스 5개와 Gateway는 서로 다른 엔진으로 돈다. 서비스는 Spring MVC, Gateway는 WebFlux다.
- common은 MVC 엔진용 부품(MVC, JPA, Feign)이 든 상자라서 Gateway가 가져다 쓰면 엔진이 충돌한다.
- 그래도 "에러 코드, 에러 응답 모양, 사용자 헤더 이름" 같은 약속은 양쪽이 똑같아야 한다.
- 그래서 엔진과 상관없는 약속만 common-core로 빼서 양쪽이 같이 쓴다. 서비스는 common을 통해 common-core를 자동으로 받으므로 달라진 것이 없다.

## 자세히

### 1. 비유: 가스 주방과 인덕션 주방
| 실제 | 비유 |
|---|---|
| 서비스 5개(Spring MVC, Tomcat) | 가스레인지 주방 |
| Gateway(WebFlux, Netty) | 인덕션 주방 |
| common(MVC, JPA, Feign) | 가스 주방 설비 세트(가스레인지, 가스 배관, 주문 전화기) |
| common-core(에러 코드, 응답 형식, 헤더 이름) | 메뉴판·플레이팅 규격서. 종이라서 어느 주방에나 붙일 수 있다 |

인덕션 주방(Gateway)에 가스 설비 세트(common)를 통째로 들이면 두 가지 문제가 생긴다.
- 가스레인지와 인덕션이 함께 있으면 건물 관리인(Spring Boot)이 "여기는 가스 주방"이라고 판단한다. 인덕션 기기(Spring Cloud Gateway)는 제대로 돌지 않는다.
- 가스 배관(JPA)은 가스 공급처(DB 주소)를 요구한다. Gateway에는 DB가 없으므로 개업(기동)부터 실패한다.

그렇다고 규격서까지 주방마다 따로 쓰면 같은 메뉴가 주방마다 다른 접시에 나온다. 규격서만 한 장 떼어 두 주방이 같이 보는 것이 common-core다.

### 2. 실제로 무엇이 충돌하나
Spring Boot는 실행할 때 클래스패스(실행에 실려 온 라이브러리 목록)를 보고 앱 종류를 정한다.
- MVC(`DispatcherServlet`)가 있으면 WebFlux가 함께 있어도 SERVLET 앱, 즉 MVC 앱으로 판단한다.
- Spring Cloud Gateway의 WebFlux 판은 리액티브 앱에서만 동작하므로, MVC가 섞이면 경고를 띄우거나 제대로 뜨지 않는다.
- JPA가 있으면 DataSource 자동 설정이 켜지고, DB 주소가 없으면 기동이 실패한다.

### 3. 그런데 왜 공유가 꼭 필요한가
클라이언트(프론트엔드)는 Gateway와 서비스를 하나의 API 서버로 본다. 같은 `/api/v1/orders` 요청이라도 에러를 만드는 곳이 다를 수 있다.
- 토큰이 없으면 Gateway에서 막히고, Gateway가 401 응답을 만든다.
- Gateway를 통과한 뒤 주문이 없으면 order-service가 404 응답을 만든다.

두 응답의 JSON 모양이 다르면 프론트는 "Gateway에서 온 에러"와 "서비스에서 온 에러"를 따로 처리해야 한다. 이 프로젝트의 에러 응답은 어디서 나든 아래 모양으로 통일한다.

```json
{
  "status": 401,
  "code": "UNAUTHORIZED",
  "message": "인증이 필요합니다.",
  "path": "/api/v1/orders",
  "timestamp": "2026-10-15T10:00:00"
}
```

헤더 이름도 같다. Gateway가 `X-User-Id`를 넣는데 서비스가 `X-UserId`를 읽으면 사용자 정보가 사라진다. 분리 전에는 Gateway가 이런 상수와 형식을 따로 정의하고 있어서, 한쪽만 고치면 어긋날 위험이 있었다.

### 4. 지금 구조
```
              common-core  (에러 코드, 에러 응답 형식, 사용자 헤더 이름)
               ▲                    ▲
     api로 포함 │                    │ implementation
            common             gateway-service (WebFlux)
     (MVC, JPA, Feign)
               ▲
   user / hub / company / order / delivery
```

- Gateway는 common-core만 가져간다. 엔진 부품이 없으므로 충돌하지 않는다.
- 서비스는 common만 의존한다. common이 common-core를 `api`로 포함하므로 서비스 코드에서 `ErrorResponse`, `UserHeaders`를 그대로 쓸 수 있다. 패키지 이름도 그대로라서 import를 고칠 일이 없다.

### 5. 하나로 합치면 안 되나
합치는 방법은 셋인데 모두 손해가 크다.

| 방법 | 쉽게 말하면 | 문제 |
|---|---|---|
| Gateway에 같은 코드를 다시 복사 | 규격서를 주방마다 따로 쓴다 | 분리 전으로 돌아간다. 한쪽만 고치면 어긋난다 |
| Gateway가 common을 쓰고 MVC·JPA를 `exclude` | 가스 설비 세트를 들이고 가스레인지만 빼낸다 | common에 부품이 하나 늘 때마다 Gateway에서 또 빼야 한다. 잊으면 Gateway가 깨진다 |
| common의 웹 의존을 `compileOnly`로 바꾼다 | 세트에서 가스레인지를 빼고 각 주방이 직접 산다 | 서비스 5개가 starter를 각자 선언해야 한다. 규칙이 줄지 않고 위치만 옮겨진다 |

모듈이 하나 줄어드는 이득보다 위 비용이 크므로 분리를 유지한다.

### 6. 헷갈리기 쉬운 점: implementation이어도 실려 간다
"common이 MVC를 `implementation`으로 선언했으니 Gateway에는 안 넘어가지 않나?"라는 의문이 들 수 있다. Gradle에는 두 단계가 있다.

| 단계 | 의미 | `api` | `implementation` |
|---|---|---|---|
| 컴파일 | 내 코드에서 그 클래스를 직접 쓸 수 있는가 | 보인다 | 안 보인다 |
| 런타임 | 실행할 때 그 jar가 함께 실려 오는가 | 실려 온다 | 실려 온다 |

`implementation`은 "포장해서 같이 보내지만 상자 겉면 목록에는 안 적는다"에 가깝다. 내 코드에서 직접 쓸 수는 없지만 실행할 때는 상자 안에 들어 있다. Spring Boot는 실행할 때 상자 안을 보고 판단하므로, `implementation`이어도 MVC가 Gateway에 들어오면 충돌한다.

### 7. 앞으로 지킬 규칙
새 클래스를 어디에 둘지 헷갈리면 질문 하나로 정한다.

> Gateway도 알아야 하는 약속이고, MVC나 WebFlux 전용 타입을 쓰지 않는가?

- 예: common-core. 에러 코드, 에러 응답 형식, 헤더 이름.
- 아니오: common. `GlobalExceptionHandler`(MVC 전용), JPA 기반 엔티티, Feign 설정.

common-core에는 `spring-web`(MVC와 WebFlux가 함께 쓰는 기반, `HttpStatus` 등)과 `jackson-annotations`만 둔다. 여기에 MVC나 JPA가 들어가면 나눈 이유가 사라진다.

## 이 프로젝트에서는
- 결정 기록: `docs/decisions/0006-common-core-module.md`
- common-core 의존성: `common-core/build.gradle`(`spring-web`, `jackson-annotations`만)
- common이 common-core를 노출: `common/build.gradle`의 `api project(':common-core')`
- Gateway 의존: `gateway-service/build.gradle`의 `implementation project(':common-core')`
- Gateway 사용처: `gateway-service/src/main/java/com/sparta/logistics/gateway/security/UserHeaderSanitizingFilter.java`(`UserHeaders`), `gateway-service/src/main/java/com/sparta/logistics/gateway/support/ErrorResponseWriter.java`(`ErrorResponse`)
- 공유 계약 위치: `common-core/src/main/java/com/sparta/logistics/common/auth/UserHeaders.java`, `common-core/src/main/java/com/sparta/logistics/common/exception/ErrorResponse.java`

## 면접 포인트
- Gateway와 서비스는 공통 코드를 어떻게 공유하나: 실행 엔진(MVC, WebFlux)과 무관한 약속(에러 코드, 응답 형식, 헤더 이름)만 얇은 모듈로 떼어 공유한다. 엔진에 묶인 코드는 섞지 않는다.
- `api`와 `implementation`의 차이: `api`는 소비자의 컴파일 시점에도 보이고, `implementation`은 보이지 않는다. 둘 다 런타임에는 함께 실려 간다.
- MVC와 WebFlux가 클래스패스에 함께 있으면 Spring Boot는 무엇을 고르나: MVC(SERVLET)를 고른다. 리액티브로 쓰려면 MVC를 빼거나 `spring.main.web-application-type=reactive`를 지정한다.

## 참고
- Gradle Java Library 플러그인(api와 implementation): https://docs.gradle.org/current/userguide/java_library_plugin.html
- Spring Boot 웹 애플리케이션 종류 판단(`SpringApplication`의 Web Environment 절): https://docs.spring.io/spring-boot/reference/features/spring-application.html
- Spring Cloud Gateway 문서: https://docs.spring.io/spring-cloud-gateway/reference/
