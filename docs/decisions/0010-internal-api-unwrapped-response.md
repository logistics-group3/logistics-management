# 0010. 내부 API는 DTO를 감싸지 않고 반환

- 상태: 채택
- 날짜: 2026-10-14
- 결정자: 팀 (Notion "main 통합 방안 — 팀 합의 대조·추가 검토" 3장)

## 맥락
- SA 6.3은 내부 API(I-01 등)의 응답 필드만 정하고, 외부 API처럼 `{status, message, data}`로 감쌀지는 정하지 않았다.
- 브랜치마다 달랐다. haneol(ADR-0005)과 KMH는 DTO 그대로, KEJ와 통합안은 `ApiResponse`로 감쌌다.
- 서비스 개발이 시작되면 모든 FeignClient가 이 형식에 맞춰 작성되므로 먼저 하나로 정해야 했다.
- 팀 의견: "내부 API는 DTO 그대로 반환(서비스 계약이라 단순히). 문제가 생기면 그때 오류를 감싸는 걸로 리팩터링".

## 결정
- `/internal/**` 컨트롤러는 DTO를 그대로 반환한다. FeignClient 메서드의 반환 타입도 DTO다. 예: `HubResult getHub(@PathVariable UUID hubId)`
- 실패 응답은 외부 API와 같은 SA 6.1 에러 형식(`status, code, message, path, timestamp`)을 유지한다. 호출하는 쪽 `FeignErrorDecoder`가 `code`를 읽어 같은 코드로 전달한다.
- 외부 API(`/api/v1/**`)는 지금처럼 `ApiResponse`로 감싼다.

## 검토한 대안
- 내부 API도 `ApiResponse`로 감싸기: 응답 형식이 하나로 통일되지만, 호출하는 쪽마다 `ParameterizedTypeReference<ApiResponse<T>>`나 `.data()` 꺼내기가 반복된다. 내부 호출에서 `status`, `message`는 쓰지 않는다.

## 결과
- Gateway `UserServiceClient`는 `UserSnapshot`을 바로 읽는다. 200 본문 해석을 확인하는 테스트(`UserServiceClientTest.readsUnwrappedBody`)를 새로 추가했다. 이전에는 이 경로를 확인하는 테스트가 없었다.
- user-service 통합 테스트가 I-01 응답에 `data` 감싸기가 없음을 확인한다.
- 나중에 공통 메타데이터(추적 ID 등)를 내부 응답에 실어야 하면 이 결정을 다시 검토한다. 그때는 응답 헤더를 먼저 고려한다.
