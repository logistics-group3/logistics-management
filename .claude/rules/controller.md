---
paths:
  - "backend/**/presentation/**"
---
# Controller · presentation 규칙

## URL
- 외부: `/api/v1/{복수 리소스}` (`/api/v1/hubs`, `/api/v1/hubs/{hubId}/stocks`). Gateway 라우트(`config/gateway-service.yml`)에 있는 prefix만 사용
- 내부: `/internal/{복수 리소스}` — 별도 클래스 `Internal{X}Controller`
- 경로 변수는 `{리소스Id}` (UUID). 동작이 CRUD로 표현되지 않으면 하위 리소스 동사: `POST /api/v1/orders/{orderId}/cancel`

## 응답
| 종류 | 반환 타입 | 생성 |
|---|---|---|
| 외부 단건 | `ResponseEntity<ApiResponse<XResponse>>` | `ApiResponse.ok(XResponse.from(result))` |
| 외부 생성 | 같음, 201 | `ApiResponse.created("허브가 생성되었습니다.", response)` |
| 외부 목록 | `ResponseEntity<ApiResponse<PageResponse<XResponse>>>` | `ApiResponse.ok(PageResponse.from(page, XResponse::from))` |
| 외부 삭제 | `ResponseEntity<ApiResponse<Void>>` | `ApiResponse.ok("삭제되었습니다.", null)` |
| 내부 | DTO 그대로 (`XResponse`), 래퍼 없음 | ADR-0005 |

Entity, `Result`를 그대로 응답하지 않는다. 항상 `XResponse.from(result)`.

## 권한 · 사용자
- 로그인 사용자: 파라미터 `@CurrentUser UserContext user` → application에 그대로 넘긴다
- MASTER 전용: 메서드(또는 클래스)에 `@RequireRole(UserRole.MASTER)` — Gateway는 역할을 막지 않으므로 **누락 = 보안 사고** (ADR-0004)
- 소유자 검증(본인 주문 등)은 컨트롤러가 아니라 application에서
- 내부 컨트롤러에는 `@RequireRole`을 붙이지 않는다 (호출자가 서비스, Gateway가 외부 차단)

## Request / Response DTO (`presentation/dto`)
- `record`. 이름: `Create{X}Request`, `Update{X}Request`, `{X}SearchRequest`, `{X}Response`
- Bean Validation을 Request에: `@NotBlank`, `@NotNull`, `@Size(max = DDL 길이)`, `@Positive`, `@Pattern(message = "한국어 안내")`
- `Request.toCommand()`로 Command 변환, `Response.from(Result)` 정적 팩토리
- boolean JSON 이름이 `isXxx`여야 하면 `@JsonProperty("isXxx")`

## 메서드 형태
```java
@Operation(summary = "H-01 허브 생성", description = "MASTER 전용")
@RequireRole(UserRole.MASTER)
@PostMapping
public ResponseEntity<ApiResponse<HubResponse>> create(@CurrentUser UserContext user,
                                                       @Valid @RequestBody CreateHubRequest request) {
    HubResponse response = HubResponse.from(hubService.create(user, request.toCommand()));
    return ApiResponse.created("허브가 생성되었습니다.", response);
}

@Operation(summary = "H-02 허브 검색")
@GetMapping
public ResponseEntity<ApiResponse<PageResponse<HubResponse>>> search(@ModelAttribute HubSearchRequest request,
                                                                     Pageable pageable) {
    Page<HubResult> page = hubQueryService.search(request.toCondition(), PageRequests.normalize(pageable));
    return ApiResponse.ok(PageResponse.from(page, HubResponse::from));
}
```
- 컨트롤러는 변환·위임만. 분기·계산·Repository 호출 금지
- 클래스에 `@Tag(name, description)`, 메서드에 `@Operation(summary = "{SA API ID} {이름}")`
- `Pageable`은 반드시 `PageRequests.normalize`를 거친다 (size 10/30/50, 기본 정렬)

## 문서
URL·권한·DTO 필드를 바꾸면 `doc/design/04-api.md`의 해당 서비스 섹션.
