# 결정 기록 (ADR)

되돌리기 어려운 결정과 그 이유를 남긴다. 작성 방법은 `.agents/skills/adr/SKILL.md`를 따른다.

0001~0007은 junhyun 브랜치에서 가져온 구현 결정 기록이다. 원문의 상태는 그 브랜치에서의 채택을 뜻하며, 이번 통합 PR에 대한 팀 승인 증거를 뜻하지 않는다. 통합 판단은 0008에 있다. 팀 검토로 바뀐 항목은 0008의 "후속 결정" 절과 0009·0010이 기준이다.

| 번호 | 제목 | 상태 | 날짜 |
|---|---|---|---|
| 0020 | [로컬 Docker 네트워크를 앞단과 데이터 두 개로 나눈다](0020-docker-network-split.md) | 채택 | 2026-10-21 |
| 0019 | [주문 생성 운영자 복구와 배송 조건부 종료](0019-order-creation-operator-recovery.md) | 로컬 구현 채택·팀 리뷰 대기 | 2026-10-20 |
| 0019 | [Redis 캐시를 기본 꺼짐으로 넣고 측정으로 채택을 정한다](0019-redis-cache-measured-adoption.md) | 제안 | 2026-10-19 |
| 0018 | [메트릭 수집: Prometheus + Grafana 도입](0018-metrics-prometheus-grafana.md) | 제안 | 2026-10-16 |
| 0017 | [분산 추적: Micrometer Tracing + Zipkin 도입](0017-distributed-tracing-micrometer-zipkin.md) | 제안 | 2026-10-16 |
| 0016 | [주문 커밋 후 배송담당자 이메일로 Slack DM 발송](0016-delivery-manager-slack-dm.md) | 제안 | 2026-10-15 |
| 0015 | [실제 Gateway와 연결하는 물류 운영 테스트 UI](0015-logistics-test-ui.md) | 제안 | 2026-10-15 |
| 0014 | [SA 프로젝트 구조에 common-core 모듈 반영](0014-common-core-in-sa-structure.md) | 제안 | 2026-10-15 |
| 0013 | [서비스 간 통신: 동기 OpenFeign](0013-sync-openfeign-communication.md) | 제안 (최승근 ADR-04) | 2026-10-14 |
| 0012 | [데이터 분리: 서비스별 스키마, FK 없음, UUID 논리 참조](0012-schema-per-service-uuid-reference.md) | 제안 (최승근 ADR-03) | 2026-10-14 |
| 0011 | [서비스 경계: 비즈니스 서비스 5개와 인프라 서비스 2개](0011-service-boundaries.md) | 제안 (최승근 ADR-01) | 2026-10-14 |
| 0010 | [내부 API는 DTO를 감싸지 않고 반환](0010-internal-api-unwrapped-response.md) | 채택 | 2026-10-14 |
| 0009 | [common 공통 빈을 조건부 자동 설정으로 등록](0009-common-conditional-auto-configuration.md) | 채택 | 2026-10-14 |
| 0008 | [공통 인프라 브랜치의 선별 통합](0008-base-setting-integration.md) | 제안 (일부는 후속 결정으로 바뀜) | 2026-10-14 |
| 0007 | [DB 스키마를 Flyway 마이그레이션으로 관리](0007-flyway-schema-migration.md) | 채택 | 2026-10-14 |
| 0006 | [웹 무관 공통 계약을 common-core 모듈로 분리](0006-common-core-module.md) | 채택 | 2026-10-14 |
| 0005 | [Gateway 인증을 Spring Security(WebFlux)로 전환](0005-gateway-spring-security.md) | 채택 | 2026-10-13 |
| 0004 | [API 문서(springdoc)와 QueryDSL 라이브러리 선택](0004-springdoc-querydsl.md) | 채택 | 2026-10-13 |
| 0003 | [인증 관련 라이브러리와 구성](0003-auth-libraries.md) | 일부 대체됨 (0005) | 2026-10-13 |
| 0002 | [에이전트 중립 작업 규칙 구조](0002-agent-neutral-guidelines.md) | 채택 | 2026-10-13 |
| 0001 | [Spring Boot 3.5.16 수동 Gradle 멀티 모듈 구성](0001-boot-3-5-manual-gradle-multimodule.md) | 채택 | 2026-10-13 |

## 서비스별 결정
한 서비스 안의 결정은 그 서비스 폴더에 둔다. 번호는 서비스마다 따로 매긴다.

| 서비스 | 결정 기록 |
|---|---|
| hub | [docs/services/hub/decisions](../services/hub/decisions/README.md) |
| company | [docs/services/company/decisions](../services/company/decisions/README.md) |
| order | [docs/services/order/decisions](../services/order/decisions/README.md) |
| delivery | [docs/services/delivery/decisions](../services/delivery/decisions/README.md) |

## 팀원 ADR 이관
최승근의 Notion ADR 15개를 옮겼다(팀 결정: "ADR 문서 자체는 넣는데 각 서비스에 위치시키기"). 팀 승인 기록이 없어 모두 "제안"이다.
- 공통 결정 ADR-01·03·04는 0011~0013으로 옮겼다.
- 기존 결정과 겹치는 ADR-02·05·15는 0001·0005·0009 끝에 "팀원 ADR 보완" 절로 붙였다.
- 서비스 결정 ADR-06~14는 위 서비스별 결정 기록으로 옮겼다. SA v2.0과 다른 결정(재고, 배송 2구간 등)은 서비스 담당자가 채택 여부를 정한다.
