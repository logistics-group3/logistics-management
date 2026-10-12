# 06. ADR — 설계 결정 기록

> 최신이 위. Notion SA와 다르게 구현했거나, SA에 없는 내용을 정한 경우 기록한다.

## 템플릿
```
## ADR-NNNN 제목 (YYYY-MM-DD, 작성자)
- 상황:
- 결정:
- 이유:
- 영향:
```

---

## ADR-0006 설정 반영은 `/actuator/refresh` + 스크립트, Spring Cloud Bus 미도입 (2026-10-12, 한얼) — 제안
- 상황: 서비스는 기동 시에만 config-server에서 설정을 받는다. 배포 중 설정만 바꿀 때 서비스를 재시작하지 않는 방법이 필요
- 결정: 각 인스턴스의 `POST /actuator/refresh`를 `script/config-refresh.sh {service}`로 일괄 호출한다. 재시작이 필요한 설정(포트, DB, JWT 키)은 롤링 재시작. Spring Cloud Bus는 쓰지 않는다
- 이유: Bus는 Kafka/RabbitMQ가 추가로 필요하다(컨테이너·학습 비용). 인스턴스 수가 적어 스크립트로 충분하다. refresh는 서비스 포트가 호스트에 공개되지 않아 compose 내부망에서만 호출된다
- 영향: 서비스 고유 설정은 JavaBean `@ConfigurationProperties`로 받아야 refresh된다. 상세·구현 단계: `doc/guide/12-config-ops.md`. 팀 리뷰(수)에서 확정 필요

## ADR-0005 내부 API 응답은 래퍼 없이 DTO 그대로 (2026-10-12)
- 상황: SA 6.1 공통 응답 형식(`{status, message, data}`)이 내부 API(`/internal/**`)에도 적용되는지 명시되지 않음
- 결정: 외부 API만 `ApiResponse` 래퍼, 내부 API는 DTO를 그대로 반환. 에러는 내부·외부 모두 `ErrorResponse`
- 이유: Feign 클라이언트가 매번 `data`를 꺼낼 필요가 없고, `FeignErrorDecoder`는 에러 형식만 알면 된다
- 영향: 팀 리뷰(수)에서 확정 필요

## ADR-0004 역할 확인은 서비스의 `@RequireRole`, Gateway는 인증·재확인만 (2026-10-12)
- 상황: MASTER 전용 경로를 Gateway 경로 표로 막을지, 서비스에서 막을지
- 결정: SA 8.3 #7대로 각 서비스 컨트롤러에 `@RequireRole(UserRole.MASTER)`. Gateway는 JWT 검증 + role·탈퇴 재확인 + 헤더 주입만
- 이유: API를 추가하는 사람이 권한도 같은 파일에서 정한다. Gateway 경로 표를 서비스 담당자 5명이 함께 고치는 충돌을 피함
- 영향: 역할 확인 누락은 코드 리뷰에서 확인 (`.claude/rules/controller.md`)

## ADR-0003 config-server 추가 — 설정은 루트 `config/` (2026-10-12)
- 상황: Notion 구성에는 config-server가 없음. JPA·Feign 타임아웃·Eureka 설정을 서비스마다 복사하면 바꿀 때 5곳을 고쳐야 함
- 결정: Spring Cloud Config Server(native, :8888) 추가. 공통·환경별·서비스별 설정을 루트 `config/`에 두고, 서비스 `application.yml`에는 이름·프로파일·Config 주소만. 프로파일 `local` / `docker`
- 이유: 공통 인프라 원칙("나중에 바꾸면 전부 고쳐야 하는 것")을 한 폴더로 관리. compose는 볼륨 마운트라 설정 수정 시 재빌드 불필요
- 영향: 모든 서비스가 config-server 기동 후에 뜬다 (compose `depends_on: service_healthy`). IDE 실행 시에도 config-server를 먼저 띄워야 한다

## ADR-0002 Database per Service + DDL은 `doc/sql/` (2026-10-12)
- 상황: Notion SA 3.4는 PostgreSQL 1개 + 서비스별 스키마·계정
- 결정: 서비스마다 PostgreSQL 컨테이너 1개(`{service}-db`, 호스트 포트 5433~5437). DDL 원본은 `doc/sql/{NN}-{service}.sql`, 각 DB 컨테이너가 자기 파일만 실행
- 이유: 서비스 간 DB 독립을 물리적으로 보장(다른 DB 접속 정보 자체가 없음), 서비스 담당자가 자기 DDL 파일만 수정
- 영향: 컨테이너 수 증가(로컬 리소스). 도메인 DB는 `--profile domain`일 때만 기동. SA 3.4의 스키마 방식과 다르므로 SA 문서 갱신 필요

## ADR-0001 권한 재확인 캐시 60초 (2026-10-12)
- 상황: SA 8.2·9장은 30초 예시, Notion「공통 인프라 개발 범위」는 60초
- 결정: 최신 문서인 공통 인프라 범위를 따라 60초, `gateway.auth.role-cache-ttl`로 조정 가능
- 이유: 튜터 피드백(SA 9-6) 결과에 따라 값만 바꾸면 된다
- 영향: 권한 변경·탈퇴가 최대 60초 늦게 반영
