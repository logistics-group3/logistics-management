# logistics-management

물류 관리 MSA 프로젝트. Java 17, Spring Boot 3.5.16, Spring Cloud 2025.0.3, PostgreSQL 16.

## 빠른 실행
```bash
cp .env.example .env
docker compose up -d --build  # 빌드는 Dockerfile 안에서 한다(로컬 Gradle 빌드 불필요)
script/e2e-check.sh           # 공통 인프라 완료 기준 6개 자동 확인 (--up을 붙이면 기동까지 한다)
# 권한 재확인 60초 캐시와 만료 후 재조회까지 보려면 --with-cache-ttl 추가 (약 65초)
```
- Eureka 대시보드: http://localhost:8761 (관리용 포트는 이 PC에서만 열린다)
- API 진입점(Gateway): http://localhost:8080
- 종료: `docker compose down` (DB까지 지우려면 `docker compose down -v`)
- MASTER 계정 만들기: 가입 후 `script/promote-master.sh <username>` 실행, 다시 로그인

## 구성
| 모듈 | 포트 | 역할 |
|---|---|---|
| config-server | 8888 | 설정 제공(`config-repo/`) |
| eureka-server | 8761 | 서비스 디스커버리 |
| gateway-service | 8080 | Spring Security(WebFlux): JWT 검증, 권한 재확인(60초 캐시), 사용자 헤더 주입, `/internal` 차단 |
| user-service | 8081 | 회원가입, 로그인(JWT 발급), 내부 사용자 조회 |
| hub / company / order / delivery-service | 8082~8085 | 서비스별 담당자가 구현 |
| common-core | - | 웹 스택과 무관한 공통 계약: 에러 코드, 에러·성공 응답, 사용자 헤더 이름 (gateway와 서비스가 함께 사용) |
| common | - | MVC 서비스 공통: 예외 처리기, BaseEntity, `@CurrentUser`, `@RequireRole`, Feign 오류·헤더 처리, 페이지 규칙 |

## 문서
- 설계: `docs/SA/`
- 공통 인프라 범위: `docs/progress/공통 인프라 개발 범위.md`
- 구현 스텝별 설명: `docs/learning/steps/`
- 결정 기록: `docs/decisions/`
- 에이전트·작업 규칙: `AGENTS.md`, `docs/agents/`
