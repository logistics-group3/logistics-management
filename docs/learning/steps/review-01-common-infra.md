# 리뷰 1. 공통 인프라 교차 검토 (2026-10-14)

- 방법: 세 검토자가 서로의 결과를 모른 채 독립적으로 검토했다.
  - 메인 에이전트: 떠 있는 compose에 실제 요청을 보내 경계 상황 확인
  - Claude 서브 에이전트: 정적 검토와 `./gradlew build`, `test` 실행
  - Codex: 정적 검토와 의존성 바이트코드 확인
- 기준: `docs/progress/공통 인프라 개발 범위.md`, SA 3.2, 3.4, 3.5, 5.1~5.2, 6.1~6.4, 8.1~8.5, 10.3
- 결론: 범위 항목은 모두 있고, blocker와 인증 우회는 없다. 다만 오류·타임아웃 처리에서 major 5건이 나왔다.

## major
| # | 내용 | 지적 | 확인 |
|---|---|---|---|
| 1 | Gateway의 I-01 WebClient에 타임아웃이 없다. user-service가 멈추면 캐시가 비어 있는 사용자의 요청이 무한 대기한다. 라우트의 60초 설정은 이 WebClient에 적용되지 않는다 | 셋 다 | 코드 |
| 2 | 공통 예외 처리기가 MVC 표준 4xx를 500으로 바꾼다. 415(Content-Type 오류), `HandlerMethodValidationException`(파라미터 `@Min` 등) 등 | Claude, Codex | `text/plain` 가입 요청이 500으로 재현됨 |
| 3 | 동시 가입 시 409 대신 500. UUID 생성 + `save()`는 INSERT를 커밋 때 실행하므로, 유니크 위반이 try/catch 밖에서 난다 | Claude, Codex | 코드 |
| 4 | 같은 사용자의 동시 요청이 캐시 Future를 공유하는데, 한 클라이언트가 연결을 끊으면 `Mono.fromFuture` 기본 동작이 Future를 취소해 다른 요청이 503이 된다 | Claude, Codex | Codex가 Reactor 3.7.19에서 확인 |
| 5 | 인증 밖의 Gateway 오류가 SA 6.1 형식이 아니다. 라우트 없음 404, 인스턴스 없음 503, 라우트 타임아웃 504(SA는 503) | 셋 다 | `/api/v1/nope` 기본 형식으로 재현됨 |

## minor
| # | 내용 | 지적 |
|---|---|---|
| 6 | `affiliationType`에 숫자 `1`을 보내면 HUB로 가입된다(Jackson 숫자 Enum 허용). 재현됨 | Codex |
| 7 | SA 6.1 페이지 규칙(기본 10건, `createdAt,DESC`, size 10/30/50 외에는 10으로 보정)을 common이 제공하지 않는다 | 셋 다 |
| 8 | SA 3.5 Feign `ErrorDecoder`(하위 서비스 실패를 503 또는 도메인 코드로 변환)가 없다 | Claude, Codex |
| 9 | `@EnableFeignClients`가 메인 클래스에 있어, FeignClient가 생기면 `@WebMvcTest`가 깨진다 | 메인, Claude |
| 10 | JWT `exp`가 없는 토큰을 막지 않고, 만료 허용 오차가 기본 60초다(서명 키가 있어야 악용 가능) | Codex, Claude |
| 11 | 빈 checkout에서 `docker compose up`만으로는 뜨지 않는다(먼저 `bootJar` 필요). 완료 기준 1의 문구와 다르다 | Claude, Codex |
| 12 | e2e: `--up`이 실패해도 계속 진행하고, Eureka의 UP 상태를 보지 않으며, curl 타임아웃이 없다 | Codex |
| 13 | postgres, config-server, eureka 포트가 모든 주소에 공개돼 있고, 예시 비밀번호가 알려져 있다 | Codex, Claude |
| 14 | `p_users`의 CHECK(배송담당자면 hub_id 필수)와 DEFAULT가 DB에 없다 | Claude, Codex |
| 15 | Swagger가 회원가입·로그인에도 Bearer 인증을 요구하는 것처럼 표시된다 | Codex |
| 16 | config import가 `optional:`이라, 로컬에서 config-server 없이 띄우면 포트 8080 기본값으로 떠서 헷갈리게 실패한다 | Claude |
| 17 | `@LoadBalanced WebClient.Builder` 빈이 Boot 기본 빌더를 대체하고, `baseUrl`로 공유 빌더를 변경한다 | Claude |

## 테스트 공백
- DB 통합 테스트 없음: 논리 삭제 뒤 로그인 실패, I-01 404, username 재사용 거부, Audit 저장값, 동시 가입(Testcontainers 필요)
- Gateway 경계 회귀 테스트 없음: 경로 우회, 대소문자 헤더, 필수 claim 누락, 실제 지연·연결 실패
- 캐시: 동시 요청 공유와 구독 취소

## nit
- `promote-master.sh`가 username을 SQL에 그대로 넣는다(로컬 도구)
- 없는 사용자는 BCrypt 비교를 하지 않아 응답 시간으로 계정 존재를 추측할 수 있다
- `UserHeaders`가 gateway와 common에 중복돼 있다
- Dockerfile이 root로 실행된다
- 정렬이 없으면 `PageResponse.sort`가 빈 문자열이다

## 문제없음으로 확인된 것
- 범위 1절 전체와 3절(springdoc, QueryDSL)이 있고, 포트, 라우트, 타임아웃(Feign 5초/20초, Gateway 60초)이 SA와 일치한다
- 헤더 위조: Security보다 먼저, 모든 요청에서 대소문자 무관하게 제거한다. 다시 넣는 값은 JWT가 아니라 DB(I-01) 값이다
- 경로 우회: `..`, `%2f`, `;` 시도가 모두 400(Spring Security StrictServerWebExchangeFirewall), `//internal` 404, 소문자 `x-user-role` 위조 403, `alg:none` 토큰 401 (메인 에이전트 실측)
- U-01, U-02, I-01의 상태 코드, 문구, 필드명, JWT claim, SA 8.5 정규식이 SA와 같다
- BaseEntity Audit 6개, AuditorAware, 응답·에러 형식, `@CurrentUser`, `@RequireRole`, Feign 헤더 전달, init.sql 스키마·계정
- 빌드와 테스트 통과(Claude 실행), 테스트 기록 32개 실패 0(Codex 확인)
