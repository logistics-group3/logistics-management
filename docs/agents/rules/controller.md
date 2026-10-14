---
paths:
  - "*-service/src/main/java/**/presentation/**"
---
# presentation 규칙

적용 경로: `*-service/src/main/java/**/presentation/**`

## URL
- 외부 API는 `/api/v1/{복수 리소스}`다. Gateway 라우트(`config-repo/gateway-service.yml`)에 있는 접두어만 쓴다.
- 내부 API는 `/internal/{복수 리소스}`이고 `Internal{X}Controller`로 클래스를 나눈다. Gateway가 외부 접근을 막는다.
- 경로 변수는 `{리소스Id}`(UUID)다. CRUD로 표현되지 않는 동작은 하위 리소스 동사로 쓴다: `POST /api/v1/orders/{orderId}/cancel`.

## 응답
| 종류 | 반환 타입 | 만드는 방법 |
|---|---|---|
| 외부 단건 | `ApiResponse<XResponse>` | `ApiResponse.ok(XResponse.from(result))` |
| 외부 생성 | 같음 + `@ResponseStatus(HttpStatus.CREATED)` | `ApiResponse.created("허브가 생성되었습니다.", response)` |
| 외부 목록 | `ApiResponse<PageResponse<XResponse>>` | `ApiResponse.ok(PageResponse.from(page, XResponse::from))` |
| 내부 | `XResponse` 그대로(감싸지 않는다) | ADR 0010 |

Entity와 `Result`를 그대로 응답하지 않는다. 항상 `XResponse.from(result)`로 바꾼다.

## 권한과 사용자
- 로그인 사용자는 `@CurrentUser UserContext user`로 받아 application에 넘긴다.
- MASTER 전용 API에는 `@RequireRole(Role.MASTER)`를 붙인다. Gateway는 인증만 하고 역할은 막지 않으므로 빠뜨리면 USER도 호출할 수 있다.
- 소유자 검증(본인 주문 등)은 application에서 한다.
- 내부 컨트롤러에는 `@RequireRole`을 붙이지 않는다. 호출자는 다른 서비스다.

## Request·Response DTO (`presentation/dto`)
- `record`로 만든다. 이름은 `Create{X}Request`, `Update{X}Request`, `{X}SearchRequest`, `{X}Response`다.
- Bean Validation은 Request에 둔다: `@NotBlank`, `@NotNull`, `@Size(max = 컬럼 길이)`, `@Positive`, `@Pattern(message = "한국어 안내")`. 규칙은 SA 8.5를 따른다.
- `Request.toCommand()`로 Command를 만들고, `Response.from(result)` 정적 팩토리로 응답을 만든다.
- JSON 이름이 `isXxx`여야 하는 boolean은 `@JsonProperty("isXxx")`를 붙이거나 record 컴포넌트 이름을 `isXxx`로 둔다.

## 메서드
```java
@Operation(summary = "H-02 허브 단건 조회")
@GetMapping("/{hubId}")
public ApiResponse<HubResponse> get(@PathVariable UUID hubId) {
    return ApiResponse.ok(HubResponse.from(hubQueryService.getHub(hubId)));
}

@Operation(summary = "H-01 허브 검색")
@GetMapping
public ApiResponse<PageResponse<HubResponse>> search(@ModelAttribute HubSearchRequest request, Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(hubQueryService.search(request.toCondition(), pageable), HubResponse::from));
}
```
- 컨트롤러는 변환과 위임만 한다. 분기, 계산, Repository 호출을 넣지 않는다.
- 클래스에 `@Tag(name, description)`, 메서드에 `@Operation(summary = "{SA API ID} {이름}")`을 붙인다.
- `Pageable`은 공통 규칙(size 10·30·50, 기본 `createdAt,DESC`)이 자동으로 적용된다. 정렬 허용 필드 검사는 application 또는 repository에서 한다.

## 문서
URL·권한·응답 필드를 바꾸면 `docs/services/{service}/README.md`의 제공 API 표를 고친다.
