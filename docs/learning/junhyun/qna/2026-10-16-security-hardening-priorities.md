---
title: 물류 MSA의 보안을 고도화하려면 무엇부터 해야 하나
date: 2026-10-16
updated: 2026-10-16
asker: Junhyun An
level: 고급
tags: [security, authorization, jwt, rate-limiting, msa, audit]
---

# 물류 MSA의 보안을 고도화하려면 무엇부터 해야 하나

## 질문

이 프로젝트 보안적으로 고도화 하려면 뭐 해봐야해

## 짧은 답

현재 JWT 검증, 위조 사용자 헤더 제거, 권한·탈퇴 재확인, BCrypt는 구현돼 있다.
데이터 접근 범위를 재검토하고 권한 거부 테스트를 보강하는 일이 우선이다.
첫 구현 과제로는 로그인 시도 제한과 보안 감사 로그·지표를 묶는 방식을 추천한다.
내부 서비스 인증과 서버 측 토큰 폐기는 그다음 설계 후보다. 기존 SA를 바꾸는 내용은 팀 확인 뒤 결정한다.

## 자세히

### 1. 데이터 접근 범위와 권한 테스트

인증은 요청자가 누구인지 확인하는 일이고, 인가는 그 사람이 해당 데이터를 다룰 수 있는지 확인하는 일이다.
로그인에 성공한 USER 두 명도 서로 다른 업체·주문·배송에 대한 권한을 가져야 할 수 있다.
객체 ID를 바꿔 다른 사람의 데이터를 다루는 문제를 BOLA라고 부른다. UUID도 접근권한 검사를 대신하지 않는다.

이 프로젝트의 주문 조회는 본인 또는 MASTER로 제한하고, 주문 검색은 소유자 조건을 DB 조회와 건수 계산에 적용한다.
배송 조회는 일반 USER에게 허용하고 배송담당자에게 본인 담당 범위를 적용한다. 이는 SA 8.4에 명시된 현재 정책이다.
배송 응답에는 배송지·수령인 이름·Slack ID 필드가 있으므로, 실제 운영 시 일반 USER의 조회 범위와 필드별 공개 범위를 먼저 논의할 만하다.

권한 매트릭스를 기준으로 타인 주문 조회·수정·취소, 다른 업체 상품 수정, 타인 배송 구간 상태 변경을 거부하는 테스트를 만든다.
목록 내용뿐 아니라 검색 조건을 바꿨을 때 전체 건수에 다른 사용자의 데이터가 섞이지 않는지도 확인한다.
기존 권한 테스트는 이미 있으므로 빠진 경계와 Gateway를 거치는 통합 경로를 보강한다.

### 2. 로그인 시도 제한과 보안 관측

조회한 인증 코드와 Gateway 설정에서는 로그인 횟수 제한을 찾지 못했다.
Gateway에는 신뢰할 수 있는 접속 IP를 기준으로 요청 제한을 두고, user-service에는 계정별 실패 누적과 짧은 재시도 제한을 두는 방식을 제안한다.
여러 인스턴스가 제한 횟수를 공유해야 하면 Redis의 원자 연산과 TTL을 쓸 수 있다. 신규 인프라 채택은 별도 결정이다.

초과 응답은 429로 표현하고 재시도 가능 시점을 안내한다. 임계치는 실제 트래픽에 맞춰 정한다.
IP만 제한하면 분산된 공격에 약하고, 계정을 무조건 오래 잠그면 타인의 계정을 고의로 잠그는 문제가 생긴다.
프록시가 붙으면 클라이언트가 임의로 보낸 X-Forwarded-For를 그대로 신뢰하지 않도록 경계를 정한다.

로그인 실패, 권한 거부, 관리자 권한 변경을 보안 이벤트로 기록한다.
감사 로그에는 시각·주체·행위·대상·결과·traceId를 남기되 비밀번호·JWT·Webhook URL은 남기지 않는다.
Grafana에는 실패율·차단 건수를 표시하고 의미 있는 증가에 알림을 건다. 사용자 ID·토큰을 메트릭 라벨로 넣지 않는다.

### 3. 로그아웃과 탈취 토큰 대응

현재 토큰 유효시간은 60분이며, 프런트 로그아웃은 메모리의 토큰과 화면 상태를 지운다.
별도의 서버 측 세션·토큰 폐기 기능은 조회 범위에서 찾지 못했다. 복사된 유효 토큰은 클라이언트 상태 삭제만으로 무효화되지 않는다.
즉시 폐기가 필요하면 세션 상태 또는 토큰 식별자 기반 폐기 목록을 검사하는 설계를 검토한다. TTL 단축만으로 즉시 폐기를 보장하지는 못한다.

현재 권한·탈퇴 확인의 60초 캐시는 팀이 허용한 반영 지연이다. 즉시 차단을 요구하면 여러 Gateway의 캐시 처리도 함께 바꿔야 한다.
Refresh Token은 SA에서 제외돼 있다. 추가한다면 저장·회전·재사용 감지까지 별도 범위로 논의한다.
검증 서비스가 늘어날 때에는 비대칭 서명과 키 교체, 발급자·대상 서비스 검증을 심화 주제로 삼을 수 있다.

### 4. 내부 호출도 인증

기본 Compose는 업무 서비스 포트를 호스트에 공개하지 않고, Gateway는 외부의 /internal 요청을 차단한다.
서비스들은 전달된 X-User-* 헤더의 형식을 검사하고 신뢰한다. 이 헤더만으로 호출자의 서비스 신원을 증명하지는 않는다.
내부망의 비인가 프로세스나 침해된 서비스까지 위협으로 다룬다면 mTLS 또는 대상·권한을 제한한 서비스 토큰을 검토한다.

사용자 신원과 호출 서비스 신원은 따로 확인해야 한다. 서비스 인증이 통과해도 데이터 소유권 검사는 유지한다.
먼저 order → delivery 한 구간에서 호출자별 허용 작업을 정하고, 무인증 호출·다른 대상용 토큰·권한 없는 작업이 거부되는지 검증하는 편이 작게 시작하기 좋다.
현재 SA의 내부 신뢰 모델을 변경하므로 구현 전에 팀 확인과 ADR이 필요하다.

### 5. 배포 설정과 보안 검사

외부 배포에는 HTTPS, 관리자 화면 접근 제어, 비밀값 주입·교체 절차를 준비한다.
현재 기본 Compose의 DB·Config Server·Eureka·Zipkin 관리 포트는 127.0.0.1 바인딩이며, 이 설정이 모든 실행 방식의 격리를 보장하지는 않는다.
Gateway 관리 포트 분리와 Prometheus·Grafana 연동은 별도 작업에서 진행 중이므로 최종 외부 차단 상태는 실행 검증으로 확인한다.

저장소에서 확인한 PR workflow는 문서와 에러 코드 검사다. GitHub 계정 측 보안 기능의 활성화 상태는 확인하지 않았다.
비밀값 검사, 의존성·이미지 취약점 검사, 권한 회귀 테스트를 CI 후보로 추가할 수 있다.
도구 예시는 Gitleaks와 Trivy이며, 검사 통과를 전체 보안의 증명으로 해석하지 않는다.

## 이 프로젝트에서는

| 확인 대상 | 근거 |
|---|---|
| JWT 검증과 필수 claim 검사 | [Gateway SecurityConfig](../../../../gateway-service/src/main/java/com/sparta/logistics/gateway/security/SecurityConfig.java) |
| 외부 헤더 제거·내부 경로 차단 | [UserHeaderSanitizingFilter](../../../../gateway-service/src/main/java/com/sparta/logistics/gateway/security/UserHeaderSanitizingFilter.java) |
| BCrypt와 로그인 실패 응답 통일 | [AuthService](../../../../user-service/src/main/java/com/sparta/logistics/user/application/AuthService.java) |
| 주문 소유자 검사 | [OrderQueryService](../../../../order-service/src/main/java/com/sparta/logistics/order/application/OrderQueryService.java) |
| 배송 조회 정책과 개인정보 필드 | [DeliveryQueryService](../../../../delivery-service/src/main/java/com/sparta/logistics/delivery/application/DeliveryQueryService.java), [DeliverySummaryResult](../../../../delivery-service/src/main/java/com/sparta/logistics/delivery/application/dto/DeliverySummaryResult.java) |
| 클라이언트 로그아웃 | [context.tsx](../../../../frontend/src/app/context.tsx) |
| 팀 보안 계약 | [SA 8장](../../../SA/sparta-logistics-sa.md), [ADR 0005](../../../decisions/0005-gateway-spring-security.md) |

2026-10-16 현재 작업 트리의 코드·설정을 읽어 정리했다. 기존 테스트의 정의는 읽었으나 이번 상담에서 빌드·테스트·공격 재현은 실행하지 않았다.
학습 로드맵의 JWT 인증과 인가, 설정·비밀값 관리, 운영 관측과 이어지는 주제다.
후속 질문인 리소스 부족·장애의 Slack 알림 구성은 [별도 노트](2026-10-16-slack-monitoring-alerts.md)에 정리했다.

## 면접 포인트

- JWT가 유효해도 타인의 주문을 수정할 수 없어야 하는 이유는 무엇인가? 인증과 객체 단위 인가의 책임이 다르기 때문이다.
- 로그아웃과 토큰 만료는 어떻게 다른가? 클라이언트 상태 삭제, 서버 측 폐기, 자연 만료는 서로 다른 동작이다.
- 내부망에서 사용자 헤더를 신뢰하는 구조에는 어떤 전제가 필요한가? 접근 경로를 통제해야 하며 내부 침해까지 다루려면 호출자 인증과 인가가 추가로 필요하다.

## 참고

- [OWASP BOLA](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/)
- [OWASP Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP Microservices Security](https://cheatsheetseries.owasp.org/cheatsheets/Microservices_Security_Cheat_Sheet.html)
- [Spring Cloud Gateway RequestRateLimiter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/requestratelimiter-factory.html)
- [JWT Best Current Practices, RFC 8725](https://www.rfc-editor.org/rfc/rfc8725.html)
- [Gitleaks](https://github.com/gitleaks/gitleaks), [Trivy](https://trivy.dev/docs/latest/guide/)
