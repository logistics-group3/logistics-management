---
title: Gateway의 Swagger UI로 user-service API를 직접 테스트하는 방법
date: 2026-10-16
updated: 2026-10-16
asker: kmh
level: 기초
tags: [swagger, springdoc, gateway, jwt, api-test]
---

# Gateway의 Swagger UI로 user-service API를 직접 테스트하는 방법

## 질문
Swagger UI로 회원 API를 직접 테스트하고 싶다. 어떻게 하나.

## 짧은 답
전체를 띄운 뒤 `http://localhost:8080/swagger-ui.html`을 열고, 오른쪽 위 목록에서 `user-service`를 고른다. 회원가입과 로그인으로 토큰을 받아 `Authorize`에 넣으면 나머지 API를 호출할 수 있다. Swagger 요청도 Gateway를 거치므로 실제 클라이언트와 같은 인증·권한 검사를 받는다. 내부 API(`/internal/**`)는 Swagger에 나오지 않는다.

## 자세히

### 구조: 왜 Gateway 하나에서 모든 서비스 문서를 보나
- 각 서비스는 springdoc이 만든 OpenAPI 문서를 `/v3/api-docs`에 낸다.
- Gateway는 `/{서비스}/v3/api-docs` 요청을 해당 서비스의 `/v3/api-docs`로 보내는 라우트를 가진다(`config-repo/gateway-service.yml`의 `*-docs` 라우트, `RewritePath`).
- Gateway의 Swagger UI는 그 주소들을 목록(`springdoc.swagger-ui.urls`)으로 불러와 서비스별로 고를 수 있게 보여 준다.
- 문서의 서버 주소가 `/`(`CommonOpenApiAutoConfiguration`)라서 `Try it out` 요청은 Swagger를 연 곳, 즉 Gateway(8080)로 간다. 그래서 JWT 검증, 사용자 헤더 주입, 권한 재확인(I-01)을 모두 거친다.
- 내부 API는 `springdoc.paths-to-exclude: /internal/**`로 문서에서 빠지고, Gateway에서도 404로 막힌다.

### Swagger UI가 8080에서 뜨는 이유
Swagger UI 전용 포트 설정은 없다. Swagger UI가 Gateway에 들어 있고 Gateway가 8080에서 뜨기 때문이다.
| 무엇 | 위치 |
|---|---|
| Gateway에 Swagger UI 추가 | `gateway-service/build.gradle`의 `springdoc-openapi-starter-webflux-ui` |
| Gateway 포트 8080 | `config-repo/gateway-service.yml`의 `server.port: 8080` |
| 호스트 포트 연결 | `docker-compose.yml` gateway-service의 `"${GATEWAY_PORT:-8080}:8080"` (`.env`의 `GATEWAY_PORT`로 호스트 쪽만 바꾼다) |
| 서비스 목록, 인증 예외 | `config-repo/gateway-service.yml`의 `springdoc.swagger-ui.urls`, `auth.whitelist` |

각 서비스는 `common/build.gradle`의 `springdoc-openapi-starter-webmvc-api`(UI 없음)로 `/v3/api-docs` JSON만 낸다. 화면은 Gateway 한 곳, 문서는 각 서비스가 맡는 구조다.

### 따라 하기
1. 전체 기동: 저장소 루트에서 `script/run.sh`를 실행한다(코드를 안 바꿨으면 `script/run.sh --no-build`). 서비스가 Eureka에 등록될 때까지 1분 정도 기다린다.
   - 확인: `http://localhost:8761`에 서비스 6개가 UP으로 보인다.
2. Swagger 열기: 브라우저에서 `http://localhost:8080/swagger-ui.html`을 연다. `/swagger-ui/index.html`로 이동한다.
   - 확인: 오른쪽 위 `Select a definition` 목록에 5개 서비스가 보인다.
3. 서비스 고르기: 목록에서 `user-service`를 고른다. 처음에는 알파벳 순서상 다른 서비스가 선택되어 있을 수 있다.
4. 회원가입(U-01): `POST /api/v1/auth/signup` → `Try it out` → 아래 본문 → `Execute`.
   ```json
   {
     "username": "test01",
     "password": "Sparta!2026",
     "name": "테스트",
     "affiliationType": "COMPANY",
     "affiliationName": "테스트업체",
     "slackEmail": "test01@example.com"
   }
   ```
   - 2026-10-16 ADR 0016 반영 이후 U-01은 `slackId`를 받지 않고 `slackEmail`(이메일 형식, 필수)을 받는다.
   - 확인: 201, `data.role`이 `USER`.
   - username은 소문자·숫자 4~10자, 비밀번호는 8~15자에 대문자·소문자·숫자·특수문자가 각각 하나 이상 있어야 한다.
5. 로그인(U-02): `POST /api/v1/auth/login`에 username과 password를 보낸다.
   - 확인: 200, `data.accessToken`이 나온다. 이 값을 복사한다.
6. 토큰 넣기: 페이지 위쪽 `Authorize` → `bearerAuth` 칸에 토큰만 붙여 넣는다(`Bearer `는 붙이지 않는다. Swagger가 붙인다) → `Authorize` → `Close`.
   - 확인: 자물쇠 아이콘이 잠긴 모양으로 바뀐다.
7. 일반 사용자 API: `GET /api/v1/users/me`(U-03) → `Execute`.
   - 확인: 200과 내 정보. 다른 사람 ID로 `GET /api/v1/users/{userId}`(U-04)를 부르면 403.
8. MASTER로 바꾸기: 로그아웃(`Authorize` → `Logout`) 후 MASTER로 로그인해 받은 토큰을 다시 넣는다. MASTER 계정은 `.env`의 `MASTER_USERNAME`, `MASTER_PASSWORD`다.
   - 확인: `GET /api/v1/users`(U-05)가 200.
9. MASTER 전용 API
   - U-06 `PATCH /api/v1/users/{userId}`: `{"slackId":"U0NEW"}`처럼 바꿀 필드만 보낸다.
   - U-07 `PATCH /api/v1/users/{userId}/role`: 배송담당자 지정은 허브 ID가 필요하다. 목록에서 `hub-service`를 골라 `GET /api/v1/hubs`로 `hubId`를 얻은 뒤 다시 `user-service`로 돌아와 `{"isDeliveryManager":true,"hubId":"<hubId>"}`를 보낸다. 다른 서비스로 바꿔도 `Authorize`에 넣은 토큰은 그대로 유지되는 것으로 보이지만 확인하지 않았다(미확인). 401이 나오면 토큰을 다시 넣는다.
   - U-08 `DELETE /api/v1/users/{userId}`: 응답 200, `data: null`.

### 토큰을 넣었는데 401이 나올 때
Swagger 응답 아래 `Curl` 칸에서 실제로 보낸 헤더를 확인한다.
| 보이는 것 | 원인 | 조치 |
|---|---|---|
| `Authorization` 줄이 없다 | 창 안의 `Authorize`를 누르지 않고 닫았거나, 새로고침으로 토큰이 사라졌다 | 다시 넣고 창 안의 `Authorize`까지 누른다 |
| `Bearer Bearer eyJ...` | 토큰 앞에 `Bearer `를 붙여 넣었다. HTTP bearer 방식은 Swagger가 접두어를 자동으로 붙인다 | 토큰 값만 넣는다 |
| 401 `TOKEN_ROLE_MISMATCH` | 토큰의 사용자가 삭제됐거나 권한이 바뀌었다 | 다시 로그인한다 |
| Request URL이 8080이 아니다 | 서비스 포트의 Swagger를 열어 Gateway를 거치지 않았다 | `http://localhost:8080/swagger-ui.html`에서 테스트한다 |

토큰 유효시간은 60분이다. 만료되면 401 `UNAUTHORIZED`가 나오므로 다시 로그인한다.

### 테스트할 때 알아 둘 동작
- 권한을 바꾸거나 삭제한 사용자의 기존 토큰은 Gateway 캐시(60초) 때문에 바로 막히지 않을 수 있다. 직전에 그 토큰을 썼다면 최대 60초 뒤에 401 `TOKEN_ROLE_MISMATCH`가 된다. 권한을 바꾼 사용자는 다시 로그인한다.
- U-05의 `size`는 10, 30, 50만 허용하고 다른 값은 10으로 보정한다.
- 자기 자신의 권한 변경과 자기 삭제는 400 `INVALID_REQUEST`다.
- 같은 username으로 다시 가입하면 409이므로 테스트 계정 이름을 바꿔 가며 쓴다. 로컬 DB를 비우려면 `script/run.sh --reset`(모든 서비스의 로컬 데이터가 지워진다).

### 내부 API(I-01, I-14)를 확인하려면
Swagger와 Gateway로는 호출할 수 없다. Docker 네트워크 안에서 user-service를 직접 부른다.
```bash
docker run --rm --network base-setting_logistics-net curlimages/curl:8.10.1 -s http://user-service:8081/internal/users/<userId>
docker run --rm --network base-setting_logistics-net curlimages/curl:8.10.1 -s "http://user-service:8081/internal/users/delivery-managers?hubId=<hubId>"
```
네트워크 이름은 Compose 프로젝트 이름을 따른다. `docker network ls`로 확인한다.

### 더 깊이
- Swagger 테스트는 "계약대로 동작하는가"를 사람이 눈으로 보는 탐색 테스트다. 반복 확인은 WebMvc 테스트(응답 형식, 검증)와 통합 테스트(DB 포함)로 자동화하고, Swagger는 새 API를 처음 확인하거나 재현이 필요할 때 쓴다.
- 운영 환경에서는 Swagger UI와 `/v3/api-docs`를 끄거나 인증 뒤에 두는 경우가 많다. API 구조가 그대로 노출되기 때문이다. 이 프로젝트는 로컬 개발용으로 Gateway에서 인증 없이 열어 두었다(`config-repo/gateway-service.yml`의 인증 제외 경로).

## 면접 포인트
- MSA에서 API 문서를 한곳에 모으는 방법: Gateway가 서비스별 OpenAPI 문서를 라우팅하고 Swagger UI가 목록으로 불러온다. 서비스는 자기 문서만 책임진다.
- Swagger의 `Try it out`이 Gateway를 거치게 하는 이유: 인증·권한 검사를 포함한 실제 경로를 테스트하기 위해서다. 서비스 포트로 직접 부르면 Gateway가 넣는 사용자 헤더가 없어 실제 동작과 달라진다.
