# 07. CHANGELOG — 문서 변경 이력

> 최신이 위. 형식: `- [영역/서비스] 내용 (관련 파일) — 작성자`

## 2026-10-13
- [user] `jwt.expiration-minutes`를 `JwtProperties`(JavaBean)로 받아 발급마다 읽음 → 재시작 없이 변경. secret은 기존대로 기동 시 1회 (JwtTokenProvider, 02-architecture §4.3) — 한얼
- [가이드] 12-config-ops §3.2를 동적(A)/재시작(C) 판단 기준·분류표로 재작성, springdoc은 재시작 대상으로 분류 — 한얼
- [config-server] 설정 파일 변경 감지 → Eureka로 해당 서비스 인스턴스에 `/actuator/refresh` 자동 호출, refresh 엔드포인트 노출·Feign refresh, `GatewayAuthProperties` JavaBean 전환 (gateway 관리 포트 분리는 배포 전 과제로 문서화) (12-config-ops §3, 02-architecture §1·§6, ADR-0006, config/README) — 한얼
- [common] QueryDSL APT 추가 → `QBaseEntity` 생성, 서비스 컴파일 오류(`cannot find symbol QBaseEntity`) 해결 (backend/common/build.gradle, 02-architecture §2) — 한얼

## 2026-10-12
- [README] 프로젝트 소개 · 아키텍처 · 인증/주문 흐름 · 도메인 개요 · 기술 스택 · 문서 목차 · 팀으로 재구성 (README.md) — 한얼
- [가이드] 역할별 가이드 `doc/guide/11~15` 추가: 서비스 개발 순서, config 보안·무중단 설정 변경(설계), Git 규칙(제안), 리뷰 체크리스트, 문제 해결 (doc/README, 02-architecture §1·§6, config/README) — 한얼
- [ADR] ADR-0006 설정 반영은 `/actuator/refresh` + 스크립트, Bus 미도입 (제안) (06-adr) — 한얼
- [규칙] `.claude/rules/` 코드 컨벤션 9개(common·controller·service·domain·repository·feign·erd·config·test) + `.claude/settings.json` Stop 훅, `.gitignore`, `.env.example`, `.github/workflows/docs-guard.yml` 추가 — 한얼
- [구조] 모듈을 `backend/`로 이동, config-server 추가(설정 `config/`), DB를 서비스별 컨테이너로 분리하고 DDL을 `doc/sql/`로 이동 (02-architecture, 03-erd, ADR-0002·0003) — 한얼
- [전체] 공통 인프라 초기 구성: Gradle 멀티 모듈, common, eureka, gateway, user-service 인증, 빈 도메인 모듈 4개, docker-compose (02-architecture, 03-erd, 04-api) — 한얼
- [ADR] ADR-0001 ~ 0005 추가 (06-adr) — 한얼
