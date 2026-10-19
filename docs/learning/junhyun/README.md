# 학습 노트

작업 중 나온 질문과 답을 날짜별로 남긴다. 블로그 초안과 복습 자료로 쓴다. 작성 방법은 `.agents/skills/learning-note/SKILL.md`, 운영 원칙은 `docs/agents/learning-guide.md`를 따른다.

- 학습 범위: [roadmap.md](roadmap.md)
- 구현 스텝 기록: [steps/](steps/README.md)

| 날짜 | 제목 | 난이도 | 태그 | 질문자 |
|---|---|---|---|---|
| 2026-10-19 | [이 프로젝트는 DDoS에 대비돼 있나, 그리고 k6로 DDoS 테스트를 할 수 있나](qna/2026-10-19-ddos-readiness-and-k6.md) | 고급 | security, ddos, rate-limiting, k6, gateway, resilience, portfolio | Junhyun An |
| 2026-10-16 | [허브 간 이동 도착 때 실제 거리·소요시간을 왜 필수로 받나, 그리고 배송 로직 검토](qna/2026-10-16-delivery-actual-distance-review.md) | 중급 | delivery, domain-model, validation, concurrency, optimistic-lock, privacy, msa | Junhyun An |
| 2026-10-16 | [가속 시간과 AI 돌발 사건을 넣어 물류 시스템을 시뮬레이션 게임으로 만들면 어떤가](qna/2026-10-16-logistics-simulation-game.md) | 고급 | simulation, virtual-time, logistics, ai, replay, portfolio | Junhyun An |
| 2026-10-16 | [리소스 부족이나 장애가 생기면 Slack 알림을 무엇으로 보내나](qna/2026-10-16-slack-monitoring-alerts.md) | 중급 | prometheus, grafana, alerting, slack, monitoring | Junhyun An |
| 2026-10-16 | [물류 MSA의 보안을 고도화하려면 무엇부터 해야 하나](qna/2026-10-16-security-hardening-priorities.md) | 고급 | security, authorization, jwt, rate-limiting, msa, audit | Junhyun An |
| 2026-10-16 | [물류 서비스를 취업 포트폴리오로 고도화할 때 무엇부터 해야 하나](qna/2026-10-16-backend-portfolio-hardening.md) | 고급 | portfolio, distributed-systems, recovery, idempotency, outbox, performance | Junhyun An |
| 2026-10-16 | [Gateway 메트릭의 비공개 별도 관리 포트란 무엇인가](qna/2026-10-16-gateway-private-management-port.md) | 기초 | gateway, actuator, prometheus, docker, security, port | Junhyun An |
| 2026-10-16 | [Zipkin Find a trace 화면은 어떻게 보나](qna/2026-10-16-zipkin-find-a-trace.md) | 기초 | zipkin, distributed-tracing, eureka, observability | Junhyun An |
| 2026-10-16 | [Zipkin Dependencies 화면에서 서비스 사이를 흐르는 점의 개수는 무엇을 뜻하나](qna/2026-10-16-zipkin-dependencies-particles.md) | 중급 | zipkin, distributed-tracing, dependencies, vizceral, observability | Junhyun An |
| 2026-10-16 | [여러 서비스를 거치는 요청 흐름을 한 번에 보려면](qna/2026-10-16-distributed-tracing-local.md) | 중급 | distributed-tracing, micrometer-tracing, zipkin, traceparent, mdc, msa | Junhyun An |
| 2026-10-16 | [Swagger로 전체 시나리오를 테스트할 때 서버 로그와 테스트 데이터를 어떻게 준비하나](qna/2026-10-16-swagger-test-server-logs.md) | 중급 | swagger, logging, access-log, docker-compose, test-data, gateway | Junhyun An |
| 2026-10-15 | [Slack Webhook을 배송담당자 이메일에 연결할 수 있는가](qna/2026-10-15-slack-webhook-email-and-dm.md) | 중급 | slack, webhook, oauth, notification, delivery | Junhyun An |
| 2026-10-15 | [GitHub PR의 중대 결함과 리뷰 포인트를 검사해 자동 승인하려면](qna/2026-10-15-github-ai-review-auto-approval.md) | 중급 | github-actions, code-review, codex, coderabbit, ci | Junhyun An |
| 2026-10-15 | [서비스별 에러 코드는 어디에 두고, 하위 서비스의 에러는 클라이언트에 어떻게 전달하나](qna/2026-10-15-downstream-error-propagation.md) | 중급 | exception, error-code, feign, msa, saga | Junhyun An |
| 2026-10-15 | [common과 common-core를 왜 나눴는지 쉽게 이해하기](qna/2026-10-15-common-core-split-easy.md) | 기초 | gradle, multi-module, spring-mvc, webflux, gateway | Junhyun An |
| 2026-10-15 | [주문 알림용 Slack 채널과 Webhook 설정](qna/2026-10-15-slack-incoming-webhook-setup.md) | 기초 | slack, webhook, configuration, docker | Junhyun An |
| 2026-10-15 | [주문 Slack 알림은 왜 트랜잭션 커밋 후 비동기로 보내는가](qna/2026-10-15-order-notification-after-commit-async.md) | 중급 | spring, transaction, event, async, slack | Junhyun An |
| 2026-10-15 | [주문 도메인은 어떤 기능부터 구현하는 것이 좋은가](qna/2026-10-15-order-implementation-order.md) | 중급 | order, domain, jpa, saga, testing | Junhyun An |
| 2026-10-14 | [JaCoCo로 테스트 커버리지 재기 - 원리, 리포트 읽는 법, 함정](qna/2026-10-14-jacoco-coverage.md) | 중급 | jacoco, coverage, testing | Junhyun An |
| 2026-10-14 | [Flyway와 JaCoCo는 무엇이고 이 프로젝트에 왜 필요한가](qna/2026-10-14-flyway-and-jacoco.md) | 중급 | flyway, migration, jacoco, coverage | Junhyun An |
| 2026-10-14 | [서비스 8개가 Gradle 빌드를 한 번만 공유하는 Docker 멀티스테이지 빌드와 BuildKit 캐시](qna/2026-10-14-docker-multistage-buildkit-cache.md) | 중급 | docker, buildkit, gradle | Junhyun An |
| 2026-10-14 | [save()로는 동시 가입이 500, saveAndFlush()로는 409인 이유와 테스트 검증법](qna/2026-10-14-jpa-flush-timing-and-concurrency-test.md) | 고급 | jpa, transaction, concurrency, testing | Junhyun An |
| 2026-10-14 | [-parameters 컴파일 옵션이 없으면 왜 Spring이 @RequestParam 이름을 못 읽나](qna/2026-10-14-java-parameters-compiler-flag.md) | 중급 | java, spring, gradle | Junhyun An |
| 2026-10-13 | [MSA에서 Spring Security는 Gateway와 user-service 중 어디에 두나](qna/2026-10-13-spring-security-placement-in-msa.md) | 중급 | spring-security, gateway, jwt, msa | Junhyun An |
| 2026-10-13 | [모듈마다 Spring 프로젝트를 두면 작업 환경이 무거워지지 않나](qna/2026-10-13-multimodule-workspace-weight.md) | 기초 | gradle, multi-module, msa, docker | Junhyun An |
| 2026-10-13 | [Eureka 서버와 클라이언트는 어떻게 설정하나](qna/2026-10-13-eureka-server-setup.md) | 기초 | spring-cloud, eureka, service-discovery | Junhyun An |
| 2026-10-13 | [Initializr가 Boot 3.5를 만들지 못할 때와 Gradle toolchain](qna/2026-10-13-gradle-toolchain-and-initializr.md) | 중급 | gradle, java, spring-boot | Junhyun An |
| 2026-10-13 | [Spring Boot 멀티 모듈 프로젝트 초기 세팅: IntelliJ와 CLI](qna/2026-10-13-spring-boot-multimodule-setup.md) | 기초 | spring-boot, gradle, intellij | Junhyun An |
