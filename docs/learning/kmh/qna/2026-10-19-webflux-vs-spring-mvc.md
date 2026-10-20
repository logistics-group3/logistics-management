---
title: Spring WebFlux와 Spring MVC는 무엇이 다른가
date: 2026-10-19
updated: 2026-10-19
asker: kmh
level: 중급
tags: [spring-mvc, webflux, reactive, netty, tomcat, gateway]
---

# Spring WebFlux와 Spring MVC는 무엇이 다른가

## 질문
Spring WebFlux와 Spring MVC의 차이는 무엇인가.

## 짧은 답
둘은 요청을 처리하는 스레드 모델이 다르다.
- Spring MVC: 요청 하나에 스레드 하나를 배정한다. DB나 다른 서비스의 응답을 기다리는 동안 그 스레드는 멈춰 있다(블로킹). Servlet 위에서 동작하고 기본 서버는 Tomcat이다.
- Spring WebFlux: 적은 수의 이벤트 루프 스레드가 많은 요청을 번갈아 처리한다. 기다리는 동안 스레드를 놓아주고, 응답이 오면 이어서 처리한다(논블로킹). Reactor(`Mono`, `Flux`) 기반이고 기본 서버는 Netty다.

MVC는 코드가 단순하고 JPA 같은 블로킹 라이브러리를 그대로 쓸 수 있다. WebFlux는 적은 스레드로 동시 연결을 많이 다루지만, 호출 경로 전체가 논블로킹이어야 효과가 있고 코드가 어렵다.

## 자세히

### 스레드 모델
```
MVC (thread-per-request)
요청1 ─ 스레드1: [처리]──[DB 대기 .......]──[응답]
요청2 ─ 스레드2: [처리]──[Feign 대기 ....]──[응답]
요청3 ─ 스레드3: ...        (스레드 풀이 다 차면 다음 요청은 대기)

WebFlux (event loop)
루프 스레드: [요청1 처리][요청2 처리][요청3 처리][요청1 응답 도착→이어서][요청2 ...]
            기다리는 동안 스레드를 쥐고 있지 않는다
```
- Tomcat 기본 스레드 풀은 200개다. 대기 시간이 긴 요청이 몰리면 스레드가 모자라 처리량이 떨어진다.
- Netty는 보통 CPU 코어 수만큼의 이벤트 루프 스레드로 동작한다. 대신 그 스레드에서 블로킹 호출(JDBC, `Thread.sleep`, 동기 HTTP)을 하면 루프 전체가 멈춰 모든 요청이 느려진다.

### 비교
| 항목 | Spring MVC | Spring WebFlux |
|---|---|---|
| 기반 | Servlet API | Reactive Streams, Reactor |
| 기본 서버 | Tomcat | Netty |
| I/O | 블로킹 | 논블로킹 |
| 반환 타입 | `User`, `ResponseEntity<User>` | `Mono<User>`(0~1개), `Flux<User>`(0~N개) |
| 요청 문맥 | `ThreadLocal`에 둘 수 있다(한 요청 = 한 스레드) | 스레드가 바뀌므로 Reactor `Context`, `ServerWebExchange`를 쓴다 |
| 필터 | `Filter`, `HandlerInterceptor` | `WebFilter`, Gateway `GlobalFilter` |
| DB 접근 | JPA, JDBC | R2DBC 등 리액티브 드라이버 |
| HTTP 클라이언트 | OpenFeign, `RestClient` | `WebClient` |
| 디버깅 | 스택 트레이스가 호출 순서대로 나온다 | 연산자 체인이라 스택 트레이스가 읽기 어렵다 |

### 코드 모양
```java
// MVC: 위에서 아래로 실행된다. 기다리는 동안 스레드가 멈춘다
@GetMapping("/users/{id}")
public UserResponse get(@PathVariable UUID id) {
    return userService.get(id);
}

// WebFlux: 실행할 일을 조립해 Mono로 돌려준다. 실제 실행은 구독 시점에 일어난다
@GetMapping("/users/{id}")
public Mono<UserResponse> get(@PathVariable UUID id) {
    return userRepository.findById(id).map(UserResponse::from);
}
```

### 이 프로젝트에서는
- 5개 서비스(user, hub, company, order, delivery)는 MVC다. JPA, OpenFeign을 쓰므로 블로킹 스택이 자연스럽고, 팀이 익숙한 방식으로 비즈니스 로직을 단순하게 유지한다. 공통 기능은 `common` 모듈에 있다.
- gateway는 WebFlux다. 모든 요청이 지나가는 관문이고, 하는 일이 JWT 검증, 헤더 주입, 라우팅이라 대부분 다른 서비스의 응답을 기다리는 시간이다. 이런 I/O 중심 작업은 논블로킹이 유리하다. Spring Cloud Gateway도 WebFlux 기반 버전(`spring-cloud-starter-gateway-server-webflux`)을 쓴다.
- 예: `UserHeaderInjectionFilter`는 `WebFilter`를 구현하고 `Mono<Void> filter(ServerWebExchange, WebFilterChain)`를 반환한다. MVC 서비스의 `RequireRoleInterceptor`(`HandlerInterceptor`)와 역할이 비슷해도 API가 다르다.
- 같은 애플리케이션에 두 스택을 섞으면 Spring Boot가 classpath의 Servlet을 보고 MVC 앱으로 판단한다. 그래서 gateway는 MVC를 포함한 `common`이 아니라 `common-core`만 의존한다(`2026-10-19-common-vs-common-core.md`).

### 언제 무엇을 고르나
- MVC: 일반적인 CRUD, JPA 사용, 팀이 리액티브에 익숙하지 않을 때. 대부분의 업무 서비스가 여기에 해당한다.
- WebFlux: 게이트웨이, 스트리밍(SSE), 외부 API를 많이 동시에 호출하는 집계 서비스처럼 대기 시간이 대부분이고 동시 연결이 많을 때.
- WebFlux가 MVC보다 항상 빠른 것은 아니다. 요청 하나의 처리 시간은 비슷하거나 오히려 느릴 수 있다. 이점은 같은 자원으로 버티는 동시 요청 수다.

### 더 깊이
- Java 21의 가상 스레드는 MVC 코드 모양을 유지하면서 블로킹 대기 비용을 줄인다(`spring.threads.virtual.enabled=true`). 이 프로젝트는 Java 17이라 해당하지 않는다.
- 리액티브 체인 안에서 블로킹 호출이 불가피하면 `Schedulers.boundedElastic()`으로 옮긴다. 이벤트 루프에서 블로킹을 잡는 도구로 BlockHound가 있다.

## 면접 포인트
- "WebFlux가 MVC보다 빠른가": 응답 속도가 아니라 적은 스레드로 많은 동시 요청을 처리하는 확장성이 장점이다. 경로 중 하나라도 블로킹(JDBC 등)이면 이점이 사라진다.
- "Gateway를 왜 WebFlux로 했나": 모든 트래픽이 모이고 대부분 하위 서비스 응답을 기다리는 I/O 작업이라 논블로킹이 맞다. 서비스는 JPA를 쓰므로 MVC로 단순하게 유지했다.
- 꼬리 질문: WebFlux에서 `ThreadLocal`이 왜 안 맞는가. 한 요청이 여러 스레드를 오가므로 Reactor `Context`로 문맥을 전달한다.
