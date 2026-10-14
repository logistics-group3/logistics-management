# 학습 로드맵

백엔드 취업에 필요한 역량을 이 프로젝트의 작업 단계와 연결한 표다. 작업하다가 해당 항목이 나오면 에이전트가 짚고, 질문이 나오면 노트로 남긴다. 노트가 생기면 "노트" 열에 링크한다.

## 1. 기초: Java와 Spring 동작 원리
| 주제 | 프로젝트에서 만나는 지점 | 노트 |
|---|---|---|
| 빌드 도구, 의존성 관리, BOM | 멀티 모듈 세팅 | [멀티 모듈](qna/2026-10-13-spring-boot-multimodule-setup.md), [toolchain](qna/2026-10-13-gradle-toolchain-and-initializr.md) |
| IoC/DI, 빈 스캔, 자동 설정 | common 모듈 스캔, starter | [-parameters](qna/2026-10-14-java-parameters-compiler-flag.md) |
| 스프링 MVC 요청 흐름, 필터와 인터셉터 | Controller, 공통 응답, 예외 처리 | |
| Bean Validation | 요청 DTO 검증 (SA 8.5) | |
| JPA 영속성 컨텍스트, 더티 체킹, 지연 로딩 | Entity 설계, BaseEntity | [flush 시점과 동시 가입](qna/2026-10-14-jpa-flush-timing-and-concurrency-test.md) |
| 트랜잭션 전파와 격리 수준, 프록시 | application 서비스 | |

## 2. 설계: 코드 구조와 데이터
| 주제 | 프로젝트에서 만나는 지점 | 노트 |
|---|---|---|
| 계층형 아키텍처, 의존 방향 | 4계층 패키지 (SA 2.4) | |
| 도메인 모델과 상태 전이 | 배송 상태 전이 (SA 2.5) | |
| ERD, 인덱스, 논리 삭제 | 테이블 명세 (SA 4, 5) | |
| N+1, 페치 조인, QueryDSL 동적 쿼리 | 검색 API | |
| REST API 설계, 공통 응답, 페이징 | API 명세 (SA 6) | |
| 테스트 전략 (단위, 슬라이스, 통합) | 도메인 테스트, Testcontainers | [동시성 테스트 검증](qna/2026-10-14-jpa-flush-timing-and-concurrency-test.md) |

## 3. MSA와 분산 시스템
| 주제 | 프로젝트에서 만나는 지점 | 노트 |
|---|---|---|
| 서비스 디스커버리, 클라이언트 사이드 로드밸런싱 | Eureka, LoadBalancer (SA 3.3) | [Eureka 설정](qna/2026-10-13-eureka-server-setup.md) |
| API Gateway, 라우팅, 인증 위임 | gateway-service (SA 8.3) | |
| 서비스 간 동기 통신과 장애 전파 | OpenFeign (SA 2.3) | |
| DB per service, 스키마 분리 | PostgreSQL 스키마 (SA 3.4) | |
| 분산 트랜잭션, Saga, 보상 트랜잭션 | 주문 생성 Saga (SA 7.1) | |
| 멱등성 | 주문 요청 (SA 7.3) | |
| 동시성 제어 (비관적·낙관적 락, 분산 락) | 재고 차감 (SA 7.4) | |
| JWT 인증과 인가 | SA 8.1~8.4 | [Security 배치](qna/2026-10-13-spring-security-placement-in-msa.md) |

## 4. 운영
| 주제 | 프로젝트에서 만나는 지점 | 노트 |
|---|---|---|
| Docker, docker-compose, 이미지 빌드 | 컨테이너 구성 (SA 3.2) | [작업 환경 무게](qna/2026-10-13-multimodule-workspace-weight.md), [멀티스테이지·BuildKit](qna/2026-10-14-docker-multistage-buildkit-cache.md) |
| 설정 분리, 환경 변수, 비밀값 관리 | application.yml, .env | |
| 헬스 체크, Actuator | 서비스 기동 순서 | |
| 장애 대응: 타임아웃, 재시도, 서킷 브레이커 | SA 3.5 | |
| 로깅, 분산 추적 | Zipkin (선택) | |
| 수평 확장과 무상태 설계 | `--scale` (SA 3.3) | |
| CI, 테스트 자동화 | 추후 | |
