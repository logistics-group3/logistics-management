# 스텝 18. 내부 API는 DTO 그대로 반환

- 날짜: 2026-10-14
- 결정 기록: [ADR 0010](../../decisions/0010-internal-api-unwrapped-response.md)
- 결과: gateway 테스트 24개, user-service 테스트 17개 통과. 새 테스트 `UserServiceClientTest.readsUnwrappedBody`

## 바꾼 것
| 파일 | 내용 |
|---|---|
| `user-service/.../InternalUserController` | `ApiResponse<InternalUserResult>` → `InternalUserResult` |
| `gateway-service/.../UserServiceClient` | `ParameterizedTypeReference<ApiResponse<UserSnapshot>>` → `bodyToMono(UserSnapshot.class)` |
| `UserServiceClientTest` | 200 응답 본문을 읽는 테스트 추가. 쓰지 않는 필드(hubId, slackId)는 무시 |
| `UserAuthIntegrationTest.internalUserContract` | `$.data.*` → `$.*`, `$.data`가 없음을 확인 |
| `docs/agents/coding-conventions.md`, `document-map.md` | 외부/내부 응답 규칙 |

## 리뷰 포인트
1. **성공과 실패의 형식이 다른 이유**: 성공 응답은 호출한 서비스만 읽는 계약이라 단순하게 둔다. 실패 응답은 `FeignErrorDecoder`가 하위 서비스의 `code`를 그대로 클라이언트에 전달하므로, 외부 API와 같은 형식이어야 한다.
2. **이전에 테스트가 없던 경로**: Gateway 테스트는 `UserLookup`을 목으로 바꾸거나 404·타임아웃만 확인했다. 그래서 응답 형식을 바꿔도 깨지는 테스트가 없었다. 형식을 바꾸는 김에 200 본문 해석 테스트를 추가했다.
3. **모르는 필드 무시**: Gateway의 `UserSnapshot`에는 I-01 응답 중 4개 필드만 있다. Spring Boot의 기본 ObjectMapper는 모르는 필드를 무시하므로 user-service가 필드를 추가해도 Gateway가 깨지지 않는다.

## 팀원에게 알릴 것
- 내부 API를 만들 때 `ApiResponse`로 감싸지 않는다. FeignClient 반환 타입도 DTO로 쓴다.
