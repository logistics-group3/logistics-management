# 결정 기록 (ADR)

되돌리기 어려운 결정과 그 이유를 남긴다. 작성 방법은 `.agents/skills/adr/SKILL.md`를 따른다.

0001~0007은 junhyun 브랜치에서 가져온 구현 결정 기록이다. 원문의 상태는 그 브랜치에서의 채택을 뜻하며, 이번 통합 PR에 대한 팀 승인 증거를 뜻하지 않는다. 팀에 공유할 통합 판단은 0008과 선별 검토 문서에 기록한다.

| 번호 | 제목 | 상태 | 날짜 |
|---|---|---|---|
| 0008 | [공통 인프라 브랜치의 선별 통합](0008-base-setting-integration.md) | 제안 (통합 브랜치 구현) | 2026-10-14 |
| 0007 | [DB 스키마를 Flyway 마이그레이션으로 관리](0007-flyway-schema-migration.md) | 채택 | 2026-10-14 |
| 0006 | [웹 무관 공통 계약을 common-core 모듈로 분리](0006-common-core-module.md) | 채택 | 2026-10-14 |
| 0005 | [Gateway 인증을 Spring Security(WebFlux)로 전환](0005-gateway-spring-security.md) | 채택 | 2026-10-13 |
| 0004 | [API 문서(springdoc)와 QueryDSL 라이브러리 선택](0004-springdoc-querydsl.md) | 채택 | 2026-10-13 |
| 0003 | [인증 관련 라이브러리와 구성](0003-auth-libraries.md) | 일부 대체됨 (0005) | 2026-10-13 |
| 0002 | [에이전트 중립 작업 규칙 구조](0002-agent-neutral-guidelines.md) | 채택 | 2026-10-13 |
| 0001 | [Spring Boot 3.5.16 수동 Gradle 멀티 모듈 구성](0001-boot-3-5-manual-gradle-multimodule.md) | 채택 | 2026-10-13 |
