# 07. CHANGELOG — 문서 변경 이력

> 최신이 위. 형식: `- [영역/서비스] 내용 (관련 파일) — 작성자`

## 2026-10-13
- [common] QueryDSL APT 추가 → `QBaseEntity` 생성, 서비스 컴파일 오류(`cannot find symbol QBaseEntity`) 해결 (backend/common/build.gradle, 02-architecture §2) — 한얼

## 2026-10-12
- [README] 프로젝트 소개 · 아키텍처 · 인증/주문 흐름 · 도메인 개요 · 기술 스택 · 문서 목차 · 팀으로 재구성 (README.md) — 한얼
- [가이드] 역할별 가이드 `doc/guide/11~15` 추가: 서비스 개발 순서, config 보안·무중단 설정 변경(설계), Git 규칙(제안), 리뷰 체크리스트, 문제 해결 (doc/README, 02-architecture §1·§6, config/README) — 한얼
- [ADR] ADR-0006 설정 반영은 `/actuator/refresh` + 스크립트, Bus 미도입 (제안) (06-adr) — 한얼
- [규칙] `.claude/rules/` 코드 컨벤션 9개(common·controller·service·domain·repository·feign·erd·config·test) + `.claude/settings.json` Stop 훅, `.gitignore`, `.env.example`, `.github/workflows/docs-guard.yml` 추가 — 한얼
- [구조] 모듈을 `backend/`로 이동, config-server 추가(설정 `config/`), DB를 서비스별 컨테이너로 분리하고 DDL을 `doc/sql/`로 이동 (02-architecture, 03-erd, ADR-0002·0003) — 한얼
- [전체] 공통 인프라 초기 구성: Gradle 멀티 모듈, common, eureka, gateway, user-service 인증, 빈 도메인 모듈 4개, docker-compose (02-architecture, 03-erd, 04-api) — 한얼
- [ADR] ADR-0001 ~ 0005 추가 (06-adr) — 한얼
