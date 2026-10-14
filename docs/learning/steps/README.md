# 구현 스텝 기록

공통 인프라를 스텝 단위로 구현하면서 남긴 설명이다. 스텝마다 만든 것, 리뷰 포인트, 검증 결과를 적는다. 전체 리뷰와 블로그 정리의 재료로 쓴다. 질문과 답은 `../qna/`에 따로 둔다. 커밋은 제목으로 찾는다(`git log --grep`).

| 스텝 | 문서 | 커밋 제목 |
|---|---|---|
| 11 | [직접 구현·하드코딩 정리](step-11-review-2-fixes.md) | refactor: 공통 예외 처리기 상속, app.jar, common-core 분리, 생성자 주입 통일 (커밋 4개) |
| 리뷰 2 | [직접 구현·하드코딩 검사](review-02-reinvention-check.md) | docs: 직접 구현·하드코딩 검사와 Flyway·JaCoCo 노트 추가 |
| 10-6 | [Testcontainers DB 통합 테스트](step-10-6-integration-tests.md) | test: Testcontainers 기반 user-service 통합 테스트 추가 |
| 10-5 | [인프라와 검증 스크립트 보강](step-10-5-infra.md) | fix: compose 단독 기동, 관리 포트 로컬 바인딩, e2e 보강 |
| 10-4 | [Gateway 타임아웃·취소·오류 형식·JWT 검증 보강](step-10-4-gateway.md) | fix: Gateway 타임아웃, 캐시 취소, 오류 형식, JWT 필수 claim 보강 |
| 10-3 | [user-service와 설정 클라이언트 보강](step-10-3-user-service.md) | fix: 동시 가입 409, 로그인 타이밍, config fail-fast |
| 10-2 | [Feign 공통 오류 처리와 재시도](step-10-2-common-feign.md) | feat: Feign 공통 오류 변환과 재시도 설정 추가 |
| 10-1 | [common 예외·입력 규칙 보강](step-10-1-common-errors-input.md) | fix: common 예외 처리와 입력 규칙 보강 |
| 리뷰 1 | [공통 인프라 교차 검토](review-01-common-infra.md) | docs: 공통 인프라 교차 검토 결과 추가 |
| 9 | [Gateway를 Spring Security로 전환](step-09-gateway-spring-security.md) | refactor: Gateway 인증을 Spring Security WebFlux로 전환 |
| 8 | [springdoc, QueryDSL](step-08-springdoc-querydsl.md) | feat: springdoc 문서 통합과 QueryDSL 공통 설정 추가 |
| 7 | [통합 검증 스크립트](step-07-e2e-check.md) | feat: 공통 인프라 완료 기준 자동 검증 스크립트 추가 |
| 6 | [gateway 보안 필터](step-06-gateway-security.md) | feat: gateway 인증 필터와 권한 재확인 캐시 구현 |
| 5 | [user 인증](step-05-user-auth.md) | feat: user-service 회원가입·로그인·내부 사용자 조회 구현 |
| 4 | [common 엔티티·사용자 컨텍스트](step-04-common-entity-user-context.md) | feat: common 엔티티 Audit과 사용자 컨텍스트 추가 |
| 3 | [common 응답·예외](step-03-common-response-exception.md) | feat: common 공통 응답과 전역 예외 처리 추가 |
| 2 | [config-server로 설정 중앙화](step-02-config-server.md) | feat: config-server로 서비스 설정 중앙화 |
| 1 | [Docker 기반 로컬 인프라](step-01-docker-infra.md) | feat: Docker 기반 로컬 인프라 구성 |
