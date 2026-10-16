---
title: Gateway 메트릭의 비공개 별도 관리 포트란 무엇인가
date: 2026-10-16
updated: 2026-10-16
asker: Junhyun An
level: 기초
tags: [gateway, actuator, prometheus, docker, security, port]
---

# Gateway 메트릭의 비공개 별도 관리 포트란 무엇인가

## 질문

> Gateway가 JWT 없이는 모든 경로를 막고 8080이 외부 공개된 걸 확인했으니, Gateway 메트릭은 비공개 별도 관리 포트로 내보내도록 할게요. 이어서 실행 스크립트와 문서 동기화 규칙을 확인하겠습니다.

다른 ai가 하는 작업인데 이게 무슨소리야? 비공개 별도 관리포트?

## 짧은 답

일반 사용자가 API를 호출하는 입구와 모니터링 도구가 서버 상태를 읽는 입구를 나누겠다는 뜻이다.
예를 들어 사용자 요청은 8080으로 받고, 메트릭은 별도 관리 포트 9080에서 제공한다. 9080은 설명용 예시다.
비공개란 포트 번호를 숨긴다는 뜻이 아니라, 외부에서는 접속할 수 없도록 네트워크 접근을 제한한다는 뜻이다.
포트 분리와 접근 제한은 각각 설정해야 한다. JWT 없이 메트릭을 읽도록 하는 보안 규칙도 별도로 확인해야 한다.

## 자세히

### 포트와 메트릭

포트는 같은 서버에서 통신을 받을 입구를 구별하는 번호다. 하나의 Spring Boot 애플리케이션도 사용자 API용 포트와 관리용 포트를 함께 사용할 수 있다.
메트릭은 요청 수, 응답 시간, 오류 수, JVM 메모리 사용량처럼 서버 동작을 수치로 나타낸 정보다.
Prometheus는 이런 수치를 주기적으로 읽어 저장하는 모니터링 도구다.

| 구분 | 사용자 API용 포트 | 관리용 포트 |
|---|---|---|
| 번호 예시 | 8080 | 9080 |
| 읽는 대상 | 로그인, 주문, 배송 등 API | `/actuator/prometheus` 등 운영 정보 |
| 접속 주체 | 프런트엔드, API 사용자 | 내부 모니터링 도구 |
| 접근 제어 | 공개 API 예외와 JWT 인증 | 내부 네트워크 제한과 관리 엔드포인트 보안 규칙 |

Spring Boot 3.5.16은 `management.server.port`로 Actuator 관리 포트를 분리할 수 있다. 다음은 원리 설명용 설정이다.

```yaml
server:
  port: 8080
management:
  server:
    port: 9080
```

이 설정은 포트만 분리한다. 비공개 네트워크 구성이나 JWT 검사 예외까지 자동으로 완성하지 않는다.

### 왜 나누나

현재 JWT 검사 대상인 메트릭 경로에 Prometheus가 토큰 없이 접근하면 인증 단계에서 거절될 수 있다.
그 경로를 외부 공개 포트에서 무인증으로 허용하면, 외부에서도 운영 정보를 읽을 수 있게 된다.
인용한 작업은 내부 수집 경로를 따로 만들어 모니터링 접근과 사용자 API 접근을 분리하려는 의도로 해석할 수 있다.

### Docker에서 비공개로 만드는 의미

기본 bridge 네트워크 구성에서는 같은 Docker 네트워크의 컨테이너끼리 내부 포트로 통신할 수 있다.
따라서 Gateway 관리 포트를 호스트의 `ports:`에 공개하지 않고, 같은 네트워크의 Prometheus가 `gateway-service:9080`으로 읽는 구성이 가능하다.
여기서 내부 접근 범위는 같은 네트워크에 연결된 컨테이너들이며, Prometheus 한 대만 자동으로 허용되는 것은 아니다.

Docker 없이 같은 컴퓨터에서 실행한다면 관리 서버 주소를 `127.0.0.1`로 제한하는 방법도 있다.
컨테이너 안의 `127.0.0.1`은 그 컨테이너 자신을 가리키므로, 별도 Prometheus 컨테이너가 읽는 구성에 이 설정을 그대로 적용하면 연결할 수 없다.

## 이 프로젝트에서는

2026-10-16 질문 확인 시점의 작업 트리를 읽어 확인했다. 다른 에이전트가 모니터링 기능을 구현 중이므로 아래 내용은 완료된 배포 상태를 뜻하지 않는다.

- [`config-repo/gateway-service.yml`](../../../../config-repo/gateway-service.yml)의 `auth.whitelist`에는 회원가입·로그인·health·Swagger 경로가 있다. 따라서 "JWT 없이는 모든 경로를 막는다"보다 "공개 경로를 제외한 요청에 JWT를 요구한다"가 정확하다.
- [`SecurityConfig.java`](../../../../gateway-service/src/main/java/com/sparta/logistics/gateway/security/SecurityConfig.java)는 화이트리스트를 제외한 요청에 인증을 요구한다. 확인 시점의 화이트리스트에는 `/actuator/prometheus`가 없었다.
- [`docker-compose.yml`](../../../../docker-compose.yml)의 Gateway 포트 매핑은 `${GATEWAY_PORT:-8080}:8080`이었다. 루프백 주소로 제한하지 않은 호스트 포트 공개 설정이다. 실제 인터넷에서 접속 가능한지는 방화벽·공유기·배포 환경을 별도로 확인해야 한다.
- "실행 스크립트와 문서 동기화 규칙을 확인하겠다"는 말은 포트 변경으로 상태 확인 주소 등이 달라지는지 살피고, [`script/docs-check.sh`](../../../../script/docs-check.sh)와 [`docs/doc-map.tsv`](../../../doc-map.tsv)에 맞춰 관련 설명도 갱신하겠다는 뜻으로 해석할 수 있다.
- 이 질문에서는 코드·실행 설정을 변경하지 않았다. 관리 포트의 실제 기동, Prometheus 수집 성공, 외부 접근 차단은 검증하지 않았다.

학습 로드맵의 "API Gateway, 라우팅, 인증 위임"과 "헬스 체크, Actuator"에 연결되는 내용이다.

## 면접 포인트

- 관리 포트를 나누는 이유는 무엇인가? 사용자 API와 운영 정보의 접근 경계를 분리하기 위해서다.
- 포트 번호만 바꾸면 비공개가 되는가? 아니다. 수신 주소, Docker 포트 공개, 방화벽 등으로 접근 범위를 제한하고 필요한 인증 규칙을 적용해야 한다.

## 참고

- [Spring Boot 3.5.16: Monitoring and Management Over HTTP](https://docs.spring.io/spring-boot/3.5/reference/actuator/monitoring.html)
- [Docker: Port publishing and mapping](https://docs.docker.com/engine/network/port-publishing/)
- 확인 범위: 현재 브랜치, Gateway 보안 코드·화이트리스트, Compose 포트 매핑, 문서 검사 스크립트를 읽었다. 실행 환경 검증은 하지 않았다.
