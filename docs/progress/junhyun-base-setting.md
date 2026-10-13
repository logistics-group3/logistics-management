# junhyun/base-setting

## 지금 하는 일
공통 인프라 스텝 8: springdoc, QueryDSL (시간이 남는 범위)

## 진행 상태
공통 인프라 범위: [공통 인프라 개발 범위.md](공통%20인프라%20개발%20범위.md). 10/13 오후 구현, 10/14 오전 리뷰 후 한 명의 결과물을 채택한다. 이 브랜치를 main처럼 쓴다.

**사전 작업 (완료)**
- [x] Gradle 멀티 모듈 뼈대 (Boot 3.5.16, Cloud 2025.0.3, Gradle 8.14.3, toolchain 17)
- [x] 작업 규칙(AGENTS.md, docs/agents, .agents/skills), 기록 체계, 플러그인, 학습 패널

**공통 인프라 스텝** (스텝마다 커밋하고 바로 다음 스텝으로 간다. 전체 리뷰는 마지막에 한다. 스텝 설명은 docs/learning/steps/)
- [x] 1. Docker 인프라: compose, Dockerfile 템플릿, init.sql, .env.example. `docker compose up` 후 서비스 6개가 Eureka에 UP으로 등록됨 확인 (완료 기준 1)
- [x] 2. config-server (native, 8888): 서비스 6개가 config에서 설정을 받아 기동, Gateway 라우팅으로 user-service 도달 확인
- [x] 3. common 응답·예외: ApiResponse, PageResponse, ErrorCode(인터페이스)+CommonErrorCode, GlobalExceptionHandler. 테스트 5개 통과
- [x] 4. common 엔티티·사용자 컨텍스트: BaseEntity, AuditorAware, @CurrentUser, @RequireRole, FeignHeaderInterceptor. common 테스트 13개 통과
- [x] 5. user 인증 (U-01, U-02, I-01, 샘플 API): Gateway 경유 가입·로그인 확인(완료 기준 2), 테스트 7개 통과. 위조 헤더가 아직 통과함을 재현
- [x] 6. gateway 보안 필터: 완료 기준 3~6 curl 확인, gateway 테스트 9개 통과
- [x] 7. 통합 검증 스크립트: 빈 DB에서 `script/e2e-check.sh --up` PASS 11/0, 64초 (완료 기준 1~6 전체)
- [ ] 8. 시간이 남으면: springdoc, QueryDSL
- 면접 포인트: DB per service를 인스턴스 하나에서 스키마·계정으로 나눈 이유와 한계, compose의 depends_on과 healthcheck 차이, Config Server 값과 환경 변수의 우선순위

## 다음 단계
1. springdoc: 서비스별 문서 + Gateway Swagger UI에서 서비스 선택
2. QueryDSL 공통 설정
3. 전체 리뷰 (사용자)

## 직접 해보기
공통 인프라 전체를 처음부터 띄우고 검증하는 순서다. 스텝별 상세는 `docs/learning/steps/`에 있다.

1. `cp .env.example .env`
2. `./gradlew bootJar` 후 `docker compose up -d --build`
3. http://localhost:8761 에서 서비스 6개 UP 확인
4. 가입: `curl -X POST localhost:8080/api/v1/auth/signup -H 'Content-Type: application/json' -d '{"username":"hong01","password":"Sparta!2026","name":"홍길동","slackId":"U01","affiliationType":"COMPANY","affiliationName":"부산수산도매"}'`
5. 로그인: 같은 방식으로 `/api/v1/auth/login`에 username, password를 보내고 `data.accessToken`을 복사한다
6. 호출: `curl localhost:8080/api/v1/users/sample/me -H "Authorization: Bearer <토큰>"`
7. MASTER 확인: `script/promote-master.sh <username>` 후 다시 로그인하고 `/api/v1/users/sample/admin` 호출
8. 한 번에 확인: `script/e2e-check.sh`

## 메모
- 컨테이너는 `EUREKA_PREFER_IP=true`(config-repo의 `prefer-ip-address` 자리표시자)로 IP 등록한다. 호스트명(컨테이너 ID)으로 등록하면 다른 컨테이너가 찾지 못할 수 있다.
- 로컬 `bootRun`과 compose를 동시에 쓰면 포트가 충돌한다.
- 서비스가 막 뜬 직후 Gateway 호출이 503이면, Gateway의 Eureka 목록 캐시(30초 주기)가 아직 갱신되지 않은 것이다. 잠시 뒤 다시 호출한다.
- ADR: 0001(수동 Gradle 구성), 0002(에이전트 중립 규칙)
- study-mode: 사용자 플러그인의 시스템 프롬프트 수정은 기본 보안 정책이 막는다. 학습 모드 규칙은 AGENTS.md로 대신한다.
