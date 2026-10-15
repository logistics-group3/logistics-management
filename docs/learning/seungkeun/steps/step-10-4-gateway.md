# 스텝 10-4. Gateway 타임아웃·취소·오류 형식·JWT 검증 보강 (리뷰 반영)

- 날짜: 2026-10-14
- 근거: [리뷰 1](review-01-common-infra.md) major 1, 4, 5 / minor 10, 17
- 결과: gateway 테스트 23개 통과(이전 11개), 전체 빌드 통과

## 무엇을 고쳤나
| 문제 | 수정 |
|---|---|
| 권한 재확인(I-01) WebClient에 타임아웃이 없어 user-service가 멈추면 무한 대기 | `auth.user-lookup-timeout`(기본 3초). 연결·응답 타임아웃(Netty)과 전체 조회 상한(`Mono.timeout`)을 함께 건다. 초과하면 기존 경로대로 503 |
| 같은 사용자의 동시 요청 중 하나가 끊기면 공유 Future가 취소돼 다른 요청까지 503 | `Mono.fromFuture(supplier, true)`: 구독 취소를 공유 Future에 전달하지 않는다 |
| 라우트 없음 404, 인스턴스 없음 503, 라우트 타임아웃 504가 Gateway 기본 형식 | `GatewayErrorWebExceptionHandler`(order -2): SA 6.1 형식으로 통일, 504는 SA대로 503 SERVICE_UNAVAILABLE |
| 경로 우회 요청을 방화벽이 본문 없는 400으로 거부 | `ServerExchangeRejectedHandler` 빈: SA 형식 400 INVALID_REQUEST |
| exp가 없는 토큰을 통과시킴. 만료 후 60초까지 허용 | 필수 claim(sub, username, role, iat, exp) 검증기, 허용 오차 0 |
| `@LoadBalanced WebClient.Builder` 빈이 Boot 기본 빌더를 대체하고, `baseUrl`로 공유 빌더를 변경 | Boot 빌더를 `clone()`한 뒤 `ReactorLoadBalancerExchangeFilterFunction`을 붙여 이 클라이언트에만 적용 |

## 추가한 회귀 테스트
- 필수 claim 누락(exp 없음, role 없음) → 401, user-service를 부르지 않음
- 만료 1초 지난 토큰 → 401(허용 오차 없음)
- 소문자 `x-user-role`, `x-username` 위조 → 지워지고 실제 값 주입
- `/internal/**` 토큰 없이 → 401이 아니라 404
- 경로 우회 3종(`..`, `..%2f`, `;`) → SA 형식 400 INVALID_REQUEST
- 라우트 없음 → SA 형식 404, 대상 인스턴스 없음(`lb://missing-service`) → SA 형식 503
- I-01 무응답 → 100ms 제한 시간 뒤 TimeoutException
- 동시 요청 중 하나 취소 → 다른 요청은 정상 결과, 조회는 1회

## 리뷰 포인트
1. **타임아웃을 두 겹으로 둔 이유**: Netty의 `responseTimeout`은 요청을 보낸 뒤 응답 헤더까지의 시간이다. `Mono.timeout`은 인스턴스 선택(로드밸런서), 연결, 본문 해석까지 전체를 덮는다. 둘 중 먼저 걸리는 쪽이 실패를 낸다.
2. **경로 우회 테스트에 절대 URI를 쓴 이유**: WebTestClient에 문자열 경로를 주면 URI 템플릿 처리와 인코딩을 거치면서 `..%2f` 같은 공격 문자열이 바뀐다. 실제 공격처럼 원문을 보내려고 `URI.create("http://localhost:" + port + path)`를 썼다.
3. **504를 503으로 바꾸는 이유**: SA 6.1 공통 에러 코드에는 하위 서비스 실패·타임아웃을 503 SERVICE_UNAVAILABLE 하나로 정했다. 클라이언트가 처리할 코드를 하나로 맞춘다.
4. **응답이 이미 나간 경우**: 오류 처리기와 writer는 응답이 커밋된 뒤면 쓰지 않는다. 스트리밍 도중 오류로 응답이 깨지는 것을 막는다.

## 면접 포인트
- 리액티브 코드에서 타임아웃과 취소 전파(cancel) 동작
- JWT 검증에서 "서명 확인"만으로 부족한 이유(필수 claim, 만료 허용 오차)
