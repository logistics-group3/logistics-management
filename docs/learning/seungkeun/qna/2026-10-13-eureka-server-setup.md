---
title: Eureka 서버와 클라이언트는 어떻게 설정하나
date: 2026-10-13
updated: 2026-10-13
asker: Junhyun An
level: 기초
tags: [spring-cloud, eureka, service-discovery, msa]
---

# Eureka 서버와 클라이언트는 어떻게 설정하나

## 질문
유레카 서버 설정은 어떻게 하는가.

## 짧은 답
- **서버**: 의존성 1개 + 어노테이션 1개 + 설정 2줄이면 된다. `spring-cloud-starter-netflix-eureka-server`, `@EnableEurekaServer`, 그리고 "서버 자신은 등록하지 않음" 설정이다.
- **클라이언트(각 서비스)**: `spring-cloud-starter-netflix-eureka-client` 의존성만 있으면 기동할 때 자동으로 등록된다. 서버 주소(`defaultZone`)만 알려 주면 된다.
- 등록된 이름(`spring.application.name`)으로 Gateway(`lb://user-service`)와 Feign(`@FeignClient(name = "user-service")`)이 실제 인스턴스를 찾는다.

## 자세히

### 왜 필요한가
MSA에서는 서비스 인스턴스의 IP와 포트가 계속 바뀐다(컨테이너 재시작, 수평 확장). 호출하는 쪽이 주소를 하드코딩할 수 없으니, 각 서비스가 "나 여기 있다"고 등록하고 호출하는 쪽은 이름으로 조회한다. 이것이 서비스 디스커버리이고, Eureka는 그 전화번호부 역할을 한다.

### 서버 설정
```groovy
// eureka-server/build.gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
}
```
```java
@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication { ... }
```
```yaml
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false   # 서버 자신을 레지스트리에 등록하지 않는다
    fetch-registry: false         # 다른 Eureka 서버에서 목록을 받아오지 않는다 (단독 서버)
```
- 두 값을 끄지 않으면 서버가 자기 자신을 클라이언트로 등록하려다 접속 실패 로그를 계속 남긴다.
- 운영에서는 Eureka 서버를 여러 대 두고 서로 등록하게(peer) 해서 고가용성을 확보한다. 이 프로젝트는 단독 서버다.

### 클라이언트 설정
```yaml
spring:
  application:
    name: user-service            # 등록 이름. 대문자 USER-SERVICE로 표시된다
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka
  instance:
    instance-id: ${spring.application.name}:${random.value}   # 같은 서비스의 여러 인스턴스 구분
    prefer-ip-address: true       # 컨테이너 환경에서 호스트명 대신 IP로 등록
```
- `@EnableDiscoveryClient`는 지금 버전에서 붙이지 않아도 된다. 의존성만 있으면 자동 설정된다.

### 동작 방식 (기본값)
| 동작 | 주기 | 의미 |
|---|---|---|
| 등록(register) | 기동 시 | 이름, IP, 포트, 상태를 서버에 등록 |
| 하트비트(renew) | 30초 | "아직 살아 있다" 신호 |
| 만료(eviction) | 90초간 하트비트 없음 | 레지스트리에서 제거 |
| 목록 갱신(fetch) | 30초 | 클라이언트가 전체 목록을 받아 로컬에 캐시 |

- 그래서 서비스를 내려도 최대 1~2분은 목록에 남아 있을 수 있다. 호출하는 쪽은 재시도나 타임아웃으로 대비한다.
- **자기 보존 모드(self-preservation)**: 짧은 시간에 하트비트가 대량으로 끊기면, Eureka는 네트워크 장애로 판단하고 인스턴스를 지우지 않는다. 대시보드의 빨간 경고 문구가 이것이다. 로컬에서 서비스를 자주 껐다 켜면 보인다.

## 이 프로젝트에서는
- 서버: `../../../../eureka-server`(포트 8761). 대시보드는 http://localhost:8761
- 클라이언트: gateway와 서비스 5개. compose에서는 `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`, `EUREKA_INSTANCE_PREFERIPADDRESS` 환경 변수로 설정을 덮어쓴다.
- 등록 확인: `curl -s -H 'Accept: application/json' localhost:8761/eureka/apps`

## 면접 포인트
- 클라이언트 사이드 디스커버리(Eureka + LoadBalancer)와 서버 사이드 디스커버리(쿠버네티스 Service, AWS ALB)의 차이: 누가 인스턴스를 고르는가. 쿠버네티스 환경에서는 Eureka 없이 DNS 기반 Service를 주로 쓴다.
- 인스턴스가 죽었는데 목록에 남아 있는 시간 동안의 요청은 어떻게 처리하나: 하트비트와 만료 주기 때문에 생기는 지연을 이해하고, 재시도, 타임아웃, 서킷 브레이커로 대응한다.
- 자기 보존 모드가 왜 있는가: 네트워크 분리 상황에서 정상 인스턴스까지 지우는 것을 막는다(가용성 우선, CAP의 AP).

## 참고
- https://docs.spring.io/spring-cloud-netflix/reference/spring-cloud-netflix.html
- 실행 확인: 2026-10-13 compose 환경에서 서비스 6개 UP 등록
