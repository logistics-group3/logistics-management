# 구현 스텝 기록

공통 인프라를 스텝 단위로 구현하면서 남긴 설명이다. 스텝마다 만든 것, 리뷰 포인트, 검증 결과를 적는다. 전체 리뷰와 블로그 정리의 재료로 쓴다. 질문과 답은 `../qna/`에 따로 둔다. 스텝 13부터는 팀 의견을 공통 인프라 통합 브랜치에 반영한 기록이다. 커밋은 제목으로 찾는다(`git log --grep`).

| 스텝 | 문서 | 커밋 제목 |
|---|---|---|
| 33 | [문서·AI 지침 범위 최종 확인](step-33-final-check.md) | docs(infra): 문서·AI 지침 범위 최종 확인과 PR 본문 갱신 |
| 32 | [문서 동기화 검사](step-32-docs-check.md) | chore(ci): 문서 동기화 검사와 PR 검사 워크플로 추가 |
| 31 | [로컬 실행 스크립트](step-31-run-script.md) | feat(infra): 로컬 실행 스크립트 run.sh 추가 |
| 30 | [Git 작업 가이드와 PR 템플릿](step-30-git-workflow.md) | docs(guides): 팀 Git 흐름과 STAR PR 템플릿 정리 |
| 29 | [팀 가이드와 문제 해결 기록](step-29-team-guides.md) | docs(guides): 구현·로컬 실행·설정 보안 가이드와 문제 해결 기록 추가 |
| 28 | [공용 스킬 재구성과 도구 연결](step-28-skills.md) | feat(agents): 공용 스킬 재구성과 Claude Code 연결 스크립트 추가 |
| 27 | [레이어별 코드 규칙과 리뷰 체크리스트](step-27-layer-rules.md) | docs(agents): 레이어별 코드 규칙과 리뷰 체크리스트 추가 |
| 26 | [팀원 ADR 이관](step-26-adr-migration.md) | docs(decisions): 팀원 ADR을 공통·서비스별 결정 기록으로 이관 |
| 25 | [모듈별 지침](step-25-module-guidelines.md) | docs(agents): 모듈별 AGENTS.md와 CLAUDE.md 추가 |
| 24 | [서비스 협업 창구](step-24-service-collaboration.md) | docs(services): 서비스별 협업 창구 추가 |
| 23 | [문서 지도와 작성 가이드](step-23-docs-map-writing-guide.md) | docs: 문서 지도·작성 가이드·서비스 협업 창구 규칙 추가 |
| 22 | [학습 노트를 사람별 폴더로](step-22-learning-notes-per-person.md) | docs(learning): 학습 노트를 사람별 폴더로 이동 |
| 21 | [새로 시작 안내와 전체 검증](step-21-final-verification.md) | docs(infra): 통합 후 DB 새로 시작 안내와 전체 검증 기록 |
| 20 | [에러 코드 규칙·통합 목록](step-20-error-code-rules.md) | docs(common): 에러 코드 이름 규칙·통합 목록과 중복 검사 추가 |
| 19 | [MASTER 계정 자동 생성](step-19-master-seed.md) | feat(user): 기동 시 MASTER 계정 자동 생성 |
| 18 | [내부 API DTO 반환](step-18-internal-api-unwrapped.md) | refactor(user): 내부 API를 DTO 그대로 반환 |
| 17 | [정렬 허용 목록·QueryDSL 도우미](step-17-sort-allow-list-querydsl.md) | feat(common): 정렬 허용 목록과 QueryDSL 검색 도우미 추가 |
| 16 | [common 조건부 자동 설정](step-16-common-auto-configuration.md) | refactor(common): 공통 빈을 조건부 자동 설정으로 등록 |
| 15 | [소유자 검증 헬퍼](step-15-owner-check-helpers.md) | feat(common): 본인·MASTER 소유자 검증 헬퍼 추가 |
| 14 | [Eureka 로컬 튜닝](step-14-eureka-local-tuning.md) | chore(eureka): 로컬 개발용 등록 갱신·만료 주기 단축 |
| 13 | [실행 환경 보강](step-13-runtime-env.md) | chore(build): 줄바꿈·인코딩·데몬 JDK 고정과 비밀값 무시 패턴 추가 |
| 12 | [Flyway로 DB 스키마 관리](step-12-flyway.md) | feat: Flyway로 DB 스키마 관리 |
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
