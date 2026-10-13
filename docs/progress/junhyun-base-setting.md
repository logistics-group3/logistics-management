# junhyun/base-setting

## 지금 하는 일
공통 인프라 스텝 1 완료, 리뷰 대기 (다음: config-server)

## 진행 상태
공통 인프라 범위: [공통 인프라 개발 범위.md](공통%20인프라%20개발%20범위.md). 10/13 오후 구현, 10/14 오전 리뷰 후 한 명의 결과물을 채택한다. 이 브랜치를 main처럼 쓴다.

**사전 작업 (완료)**
- [x] Gradle 멀티 모듈 뼈대 (Boot 3.5.16, Cloud 2025.0.3, Gradle 8.14.3, toolchain 17)
- [x] 작업 규칙(AGENTS.md, docs/agents, .agents/skills), 기록 체계, 플러그인, 학습 패널

**공통 인프라 스텝** (스텝마다 커밋하고, 사용자 리뷰와 질문을 받은 뒤 다음으로 넘어간다)
- [x] 1. Docker 인프라: compose, Dockerfile 템플릿, init.sql, .env.example. `docker compose up` 후 서비스 6개가 Eureka에 UP으로 등록됨 확인 (완료 기준 1)
- [ ] 2. config-server (native, 8888)
- [ ] 3. common 응답·예외
- [ ] 4. common 엔티티·사용자 컨텍스트
- [ ] 5. user 인증 (U-01, U-02, I-01, 샘플 API)
- [ ] 6. gateway 보안 필터
- [ ] 7. 통합 검증 스크립트 (완료 기준 6개)
- [ ] 8. 시간이 남으면: springdoc, QueryDSL
- 면접 포인트: DB per service를 인스턴스 하나에서 스키마·계정으로 나눈 이유와 한계, compose의 depends_on과 healthcheck 차이

## 다음 단계
1. config-server 모듈 생성(Spring Cloud Config, native 백엔드, `config-repo/`)
2. 서비스 공통 설정(eureka, jpa)과 서비스별 설정을 config-repo로 옮기고 `spring.config.import` 적용
3. compose에 config-server를 추가하고 기동 순서 반영

## 직접 해보기
스텝 1(Docker 인프라)을 혼자 재현하는 순서다.

1. DB 초기화 SQL 작성: `infra/postgres/init.sql`
   - 스키마 5개(`user_schema` 등), 서비스별 계정(`user_app` 등)
   - `GRANT USAGE, CREATE ON SCHEMA ... TO ...`와 `ALTER ROLE ... SET search_path`
   - postgres 이미지는 `/docker-entrypoint-initdb.d/`의 SQL을 **볼륨이 비어 있을 때 한 번만** 실행한다. 수정했다면 `docker compose down -v`로 볼륨을 지우고 다시 띄운다.
2. 환경 변수 파일: `cp .env.example .env`. `.env`는 `.gitignore`에 포함돼 있다.
3. Dockerfile 템플릿: `eclipse-temurin:17-jre`에 `ARG MODULE`로 모듈 jar를 복사한다. `.dockerignore`로 jar만 빌드 컨텍스트에 보낸다.
4. 실행
   ```bash
   ./gradlew bootJar
   docker compose up -d --build
   docker compose ps          # postgres가 healthy인지 확인
   ```
5. 확인
   - http://localhost:8761 대시보드에 서비스 6개가 UP으로 보이면 성공
   - CLI: `curl -s -H 'Accept: application/json' localhost:8761/eureka/apps`
   - 실패하면 `docker compose logs -f user-service`로 확인한다.
6. 정리: `docker compose down`(데이터 유지), `docker compose down -v`(DB 볼륨까지 삭제)

## 메모
- 컨테이너는 `EUREKA_INSTANCE_PREFERIPADDRESS=true`로 IP 등록한다. 호스트명(컨테이너 ID)으로 등록하면 다른 컨테이너가 찾지 못할 수 있다.
- 로컬 `bootRun`과 compose를 동시에 쓰면 포트가 충돌한다.
- ADR: 0001(수동 Gradle 구성), 0002(에이전트 중립 규칙)
- study-mode: 사용자 플러그인의 시스템 프롬프트 수정은 기본 보안 정책이 막는다. 학습 모드 규칙은 AGENTS.md로 대신한다.
