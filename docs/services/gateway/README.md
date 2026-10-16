# gateway 협업 창구

외부 요청의 단일 진입점인 gateway-service의 계약과 현황이다. 이 서비스 코드를 고칠 때 지킬 규칙은 [`gateway-service/AGENTS.md`](../../../gateway-service/AGENTS.md)에 있다. API의 요청·응답 필드 원본은 [SA](../../SA/sparta-logistics-sa.md) 6장이다.

## 1. 서비스 정보

| 항목 | 값 |
|---|---|
| 담당자 | 공통 인프라 |
| 포트 | 8080 (외부 공개는 이 포트 하나) |
| 스키마·DB 계정 | 없음 |
| 책임 | 외부 요청 단일 진입점, JWT 검증, 권한 재확인(60초 캐시), `X-User-*` 정리·주입, 라우팅, `/internal/**` 차단 |
| 분산 추적 | 외부 요청마다 trace를 시작하고 하위 서비스·권한 재확인(I-01) 호출에 `traceparent`를 싣는다. `spring.reactor.context-propagation: auto`로 WebFlux 로그에도 traceId를 남긴다([ADR 0017](../../decisions/0017-distributed-tracing-micrometer-zipkin.md)) |

## 2. 제공 API (계약)

| ID | Method·URL | 권한·호출자 | 상태 | SA와 다른 점·비고 |
|---|---|---|---|---|
| 라우팅 | `/api/v1/auth/**`, `/api/v1/users/**` → user 등 (SA 3.2) | 외부 | 구현 | `config-repo/gateway-service.yml` |
| 헤더 주입 | `X-User-Id`, `X-Username`, `X-User-Role`, `X-Delivery-Manager` | 하위 서비스 전체 | 구현 | 클라이언트가 보낸 같은 이름 헤더는 먼저 지운다 |
| 차단 | `/internal/**` | 외부 | 구현 | 404 `NOT_FOUND` |
| Swagger | `/swagger-ui.html` | 외부 | 구현 | 서비스별 문서 선택 |

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-01 `GET /internal/users/{userId}` | user | 권한·탈퇴 재확인(60초 캐시) | 하지 않음(3초 제한) |

## 4. 구현 현황

- [x] 완료 기준 6개(E2E PASS 18/0)
- [x] 권한 재확인 60초 캐시와 만료 후 재조회

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

아직 기록이 없다.
