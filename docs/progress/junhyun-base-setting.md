# junhyun/base-setting

## 지금 하는 일
공통 인프라 스텝 6: gateway 보안 필터

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
- [ ] 6. gateway 보안 필터
- [ ] 7. 통합 검증 스크립트 (완료 기준 6개)
- [ ] 8. 시간이 남으면: springdoc, QueryDSL
- 면접 포인트: DB per service를 인스턴스 하나에서 스키마·계정으로 나눈 이유와 한계, compose의 depends_on과 healthcheck 차이, Config Server 값과 환경 변수의 우선순위

## 다음 단계
1. gateway: 헤더 제거, `/internal/**` 차단, 화이트리스트, JWT 검증, I-01 재확인(Caffeine 60초), 헤더 주입, 공통 에러 JSON
2. WebTestClient 필터 테스트, curl로 완료 기준 3~6 확인

## 직접 해보기
스텝 2(config-server)를 혼자 재현하는 순서다.

1. 모듈 추가
   - `settings.gradle`에 `include 'config-server'`
   - `config-server/build.gradle`: `spring-cloud-config-server`, `spring-boot-starter-actuator`
   - 메인 클래스에 `@EnableConfigServer`
2. 서버 설정 (`config-server/src/main/resources/application.yml`)
   - `server.port: 8888`, `spring.profiles.active: native`
   - `spring.cloud.config.server.native.search-locations: classpath:/config-repo`
   - `build.gradle`의 `processResources`로 루트 `config-repo/`를 jar 안 `config-repo/`에 넣는다.
3. 설정 저장소 (`config-repo/`)
   - `application.yml`: 모든 서비스 공통(eureka, jpa, feign 타임아웃)
   - `{서비스명}.yml`: 서비스 전용(포트, datasource)
   - 환경마다 다른 값은 `${DB_HOST:localhost}`처럼 자리표시자로 둔다.
4. 클라이언트 (각 서비스)
   - 의존성 `spring-cloud-starter-config`
   - `application.yml`에는 이름과 `spring.config.import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}`만 남긴다.
5. 확인
   - `curl localhost:8888/user-service/default`: 공통 설정과 서비스 설정이 함께 내려오면 성공
   - 서비스 로그에 `Fetching config from server at`과 `Tomcat started on port 8081`이 보이면 성공
   - IntelliJ에서 실행할 때는 config-server를 먼저 띄운다.

## 메모
- 컨테이너는 `EUREKA_PREFER_IP=true`(config-repo의 `prefer-ip-address` 자리표시자)로 IP 등록한다. 호스트명(컨테이너 ID)으로 등록하면 다른 컨테이너가 찾지 못할 수 있다.
- 로컬 `bootRun`과 compose를 동시에 쓰면 포트가 충돌한다.
- 서비스가 막 뜬 직후 Gateway 호출이 503이면, Gateway의 Eureka 목록 캐시(30초 주기)가 아직 갱신되지 않은 것이다. 잠시 뒤 다시 호출한다.
- ADR: 0001(수동 Gradle 구성), 0002(에이전트 중립 규칙)
- study-mode: 사용자 플러그인의 시스템 프롬프트 수정은 기본 보안 정책이 막는다. 학습 모드 규칙은 AGENTS.md로 대신한다.
