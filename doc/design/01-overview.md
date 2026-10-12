# 01. 개요 — 범위·일정·담당

> 최종 수정: 2026-10-12 · 기준: Notion「공통 인프라 개발 범위」, 「스파르타물류_SA문서_Master」

## 1. 프로젝트

- 주제: 물류 관리 및 배송 시스템을 위한 MSA (전국 17개 허브, 허브 간 P2P 이동, 업체 배송)
- 목표: 서비스 경계 설계, FeignClient 기반 서비스 간 통신, 서비스별 스키마 분리, 실패 시 보상(롤백), Docker Compose 오케스트레이션

## 2. 진행 방식

| 단계 | 일정 | 내용 |
|---|---|---|
| SA 제출 | 10/13(화) 12:00 | Notion SA 문서 |
| 공통 인프라 각자 구현 | 10/13(화) 오후 | 이 저장소의 범위 (아래 3) |
| 리뷰 후 하나 채택 | 10/14(수) 오전 | 채택본을 기준 브랜치로 |
| 도메인 구현 | 10/14(수) ~ | 서비스 담당자별로 모듈 하나씩 |
| 제출 / 발표 | 10/21(화) 20:00 / 10/22(수) 10:00 | |

## 3. 공통 인프라 범위

> 원칙: 모든 서비스가 같이 쓰고, 나중에 바꾸면 전부 고쳐야 하는 것까지만 넣는다. 도메인 로직은 넣지 않는다.

| 영역 | 범위 | 상태 |
|---|---|---|
| 프로젝트 뼈대 | Gradle 멀티 모듈, Spring Boot 3.3.5 · Cloud 2023.0.3 고정, 서비스 5개 빈 모듈(기동 + Eureka 등록), 패키지 구조 규칙 | 완료 |
| eureka-server | 전체 | 완료 |
| config-server | 루트 `config/` 설정 저장소(native), 프로파일 local / docker | 완료 (Notion 범위 외 추가) |
| gateway-service | 라우팅, JWT 검증, X-User-* 헤더 제거 후 재주입, `/internal/**` 차단, 회원가입·로그인 인증 제외, 권한 재확인(60초 캐시) | 완료 |
| user-service (인증만) | U-01 회원가입, U-02 로그인·JWT 발급, I-01 내부 사용자 조회 | 완료 |
| common 모듈 | BaseEntity(Audit + 논리 삭제), AuditorAware, 공통 응답·페이지 응답, 에러 코드 + 전역 예외 처리, @CurrentUser, Feign 헤더 전달 | 완료 (+ `@RequireRole`, Feign 에러 변환) |
| 인프라 | docker-compose(서비스별 DB 컨테이너 + `doc/sql` DDL + eureka + config + gateway + user), Dockerfile 템플릿, .env.example | 완료 |

### 제외 (수요일부터 서비스 담당자)
- 도메인별 Entity·CRUD·검색, 사용자 관리 API(U-03~U-08)
- 서비스별 FeignClient
- 주문 생성 Saga·보상 트랜잭션
- 허브 초기 데이터(17개 허브, 272개 경로)
- Slack 연동

## 4. 서비스 담당

| 서비스 | 포트 | 담당자 |
|---|---|---|
| user-service | 8081 | |
| hub-service | 8082 | |
| company-service | 8083 | |
| order-service | 8084 | |
| delivery-service | 8085 | |
