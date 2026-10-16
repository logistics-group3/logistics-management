# Swagger 수동 테스트와 요청 흐름 보기

Swagger로 API를 하나씩 호출하면서 서버 로그와 서비스 사이 호출 흐름을 함께 보는 로컬 도구다. `script/run.sh`로 띄운 기본 개발 스택(Gateway 8080)에 테스트용 로그 설정을 덧씌운다. 팀 `docker-compose.yml`·`config-repo`·`.env`는 바꾸지 않는다.

분산 추적(Micrometer Tracing + Zipkin)은 이 도구가 아니라 서비스 기본 기능이다. 모든 서비스가 로그 줄에 traceId를 남기고 구간을 Zipkin(http://localhost:9411)으로 보낸다([ADR 0017](../decisions/0017-distributed-tracing-micrometer-zipkin.md)). 이 도구는 그 traceId로 로그 줄을 모으고 Zipkin 링크를 보여 준다.

## 준비

- `script/run.sh`로 기본 스택이 떠 있어야 한다. Docker, Python 3, curl, perl이 필요하다.
- 실행 상태(시드 결과, 토큰, 로그 파일)는 Git에서 제외된 `.local/api-test/`에 쌓인다.

## 명령

| 명령 | 하는 일 |
|---|---|
| `script/api-test.sh up` | 접근 로그·Gateway 라우팅 로그를 켜고 Slack을 끈 상태로 6개 서비스를 다시 만든다(이미지 재빌드 없음). 끝나면 Gateway를 거쳐 5개 서비스가 200으로 응답하는지 확인한다 |
| `script/api-test.sh seed` | 테스트 계정·허브·업체·상품을 공개 API로 만든다. 다시 실행하면 있는 데이터는 재사용하고 삭제된 업체·상품만 새로 만든다 |
| `script/api-test.sh tokens` | 테스트 계정 토큰을 모두 다시 발급한다(60분 유효). `.local/api-test/tokens.txt`에도 저장한다 |
| `script/api-test.sh logs` | 6개 서비스 로그 전체를 한 화면에 모아 보여 주고 `.local/api-test/logs/`에 파일로도 저장한다 |
| `script/api-test.sh flow` | `logs`와 같되 요청 흐름 줄(Gateway 라우팅, 접근 로그, SQL, 경고·오류)만 보여 준다 |
| `script/api-test.sh trace <ID>` | 저장된 로그에서 traceId 하나의 줄만 순서대로 모으고 Zipkin 링크를 출력한다 |
| `script/api-test.sh status` | 컨테이너 상태와 Slack·접근 로그·SQL 설정, Zipkin 실행 여부를 확인한다 |
| `script/api-test.sh restore` | 팀 기본 설정으로 되돌린다. 접근 로그가 꺼지고 `.env`의 Slack 설정이 다시 적용된다 |

- `API_TEST_SQL=DEBUG script/api-test.sh up`이면 실행 SQL도 로그에 찍힌다. 변수 없이 다시 `up` 하면 꺼진다.
- `API_TEST_PREFIX=jh2 script/api-test.sh seed`처럼 접두어를 바꾸면 새 계정 묶음을 만든다. 기본 접두어는 `jh`다.
- `logs`·`flow`는 별도 터미널 탭에 켜 둔다. `up`·`restore`로 컨테이너를 다시 만들어도 보통 이어지며, 화면이 멈추면 다시 실행한다.

## 테스트 설정이 바꾸는 것

| 항목 | `up` 뒤 | `restore` 뒤 |
|---|---|---|
| 접근 로그 | Gateway(Netty)와 각 서비스(Tomcat)가 요청마다 한 줄 | 없음 |
| Gateway 라우팅 | `Route matched: <서비스>` 줄 | 없음 |
| SQL | `API_TEST_SQL=DEBUG`일 때 `org.hibernate.SQL` 줄 | 없음 |
| Slack | Webhook·DM 끔 | `.env` 값 |

DB는 기본 스택과 같다. 시드와 테스트로 만든 데이터는 `restore` 뒤에도 남는다.

## 테스트 데이터

`seed`는 아래를 만들고 ID를 `.local/api-test/data.json`에 저장한다. 공통 비밀번호는 `ApiTest!2026`이며 로컬 테스트 전용이다.

| 이름 | 기본 값 | 설명 |
|---|---|---|
| MASTER | `jhmaster` | 가입 후 `script/promote-master.sh`로 MASTER 승격 |
| SUP_USER, RCV_USER | `jhsup01`, `jhrcv01` | 공급업체·요청업체 관리자(USER) |
| OTHER_USER | `jhoth01` | 어느 업체도 관리하지 않는 USER |
| DRV_A1, DRV_A2, DRV_B1 | `jhdrva1`, `jhdrva2`, `jhdrvb1` | 서울특별시 센터 배송담당자 2명, 경기 남부 센터 1명 |
| HUB_A, HUB_B, HUB_C | 서울특별시·경기 남부·부산광역시 센터 | HUB_C에는 배송담당자를 두지 않는다(배송 생성 실패 확인용) |
| SUP_CO, RCV_CO, PRODUCT | `JH 테스트 공급`, `JH 테스트 수령`, `JH 테스트 상품`(12,000원) | 모두 HUB_A 소속 |

## 로그 읽는 법

로그 줄의 스레드 이름 뒤 `[traceId-spanId]`가 분산 추적 ID다. Gateway가 외부 요청마다 traceId를 만들고 서비스 사이 호출 헤더(`traceparent`)로 넘긴다. 그래서 한 요청이 거친 모든 서비스의 줄이 같은 traceId(앞 32자)를 가진다. spanId(뒤 16자)는 서비스마다, 호출마다 다르다.

| 줄 | 예 | 뜻 |
|---|---|---|
| Gateway 라우팅 | `[6ad1…-bd80…] o.s.c.g.h.RoutePredicateHandlerMapping : Route matched: order-service` | 이 요청을 어느 서비스로 보냈는지 |
| Gateway 접근 로그 | `"POST /api/v1/orders HTTP/1.1" 404 ... 674` | 외부 요청, 상태 코드, 응답 바이트, 처리 시간(ms) |
| 서비스 접근 로그 | `ACCESS POST /api/v1/orders HTTP/1.1 -> 404 646ms user=jhrcv01 traceparent=00-<traceId>-<spanId>-01` | 그 서비스가 받은 요청, Gateway가 넣은 사용자, 넘겨받은 추적 헤더 |
| 내부 호출 | `ACCESS GET /internal/companies/{id} ... user=jhrcv01` | 서비스 사이 Feign 호출. `user=-`인 `/internal/users/{id}`는 Gateway의 권한 재확인이며 같은 사용자는 60초 캐시된다 |
| SQL | `[6ad1…-f84e…] org.hibernate.SQL : select ...` | 그 서비스가 실행한 쿼리(`API_TEST_SQL=DEBUG`일 때) |

- 공통 예외 처리기는 4xx를 로그에 남기지 않는다. 상태 코드는 접근 로그로, 에러 코드는 Swagger 응답 본문으로 본다. 5xx는 `ERROR`와 스택 트레이스가 찍힌다.
- 저장된 로그에서 실패만 찾기: `grep -E ' -> [45][0-9]{2}|ERROR' .local/api-test/logs/<파일>.log`

## 요청 하나의 흐름 따라가기

1. `flow`나 `logs`를 켜 둔 채 Swagger에서 요청을 보낸다.
2. 그 요청 줄의 `[traceId-spanId]`에서 앞 32자를 복사한다. 서비스 접근 로그 `traceparent`의 가운데 값도 같다.
3. `script/api-test.sh trace <traceId>`로 그 요청의 줄만 모아 본다. 끝에 Zipkin 링크가 나온다.
4. Zipkin(`http://localhost:9411`)에서는 오른쪽 위 검색창에 traceId를 넣거나 Run Query로 최근 요청을 연다. 서비스별 구간과 걸린 시간이 막대로 보인다.

RCV_USER가 없는 상품으로 주문(O-01)했을 때 Zipkin의 주요 구간이다(2026-10-16 실제 확인, 시작 시각·걸린 시간. Gateway 보안 필터 구간과 요청업체 조회의 받는 쪽 구간은 줄였다). 주문은 업체·상품을 확인하다 404로 거절되고 주문 기록은 남지 않는다.

```text
  0.0ms 674.2ms gateway-service  SERVER  http post /api/v1/orders
  2.2ms   8.9ms   gateway-service  CLIENT  http get http://user-service/internal/users/{RCV_USER}   권한 재확인
  4.7ms   5.5ms     user-service     SERVER  http get /internal/users/{userid}
 15.0ms 652.8ms   gateway-service  CLIENT  http post                                              order-service로 전달
 21.2ms 643.0ms     order-service    SERVER  http post /api/v1/orders
362.7ms 209.7ms       order-service    CLIENT  http get /companies/{companyId}                     공급업체(Feign)
551.6ms  18.2ms         company-service  SERVER  http get /internal/companies/{companyid}
578.8ms   5.6ms       order-service    CLIENT  http get /companies/{companyId}                     요청업체
585.1ms  60.3ms       order-service    CLIENT  http get /products/{productId}                      상품 → 404
586.3ms  59.1ms         company-service  SERVER  http get /internal/products/{productid}
```

- 들여쓰기가 부모·자식 관계다. Feign 호출(CLIENT)과 받는 쪽 처리(SERVER)가 짝을 이룬다.
- SQL은 구간으로 남지 않는다. 쿼리는 같은 traceId의 `org.hibernate.SQL` 로그 줄로 본다([ADR 0017](../decisions/0017-distributed-tracing-micrometer-zipkin.md) 범위 밖).

## 주의

- Swagger 목록 API(U-05·H-01·C-02·P-02·D-01)는 `request`에 `{}`나 필터 JSON, `pageable`에 `{"page": 0, "size": 10}`을 넣는다. 기본값의 `"sort": ["string"]`을 남기면 400 `VALIDATION_ERROR`다.
- Swagger는 필수 헤더가 비면 요청을 보내지 않는다. 헤더 누락 같은 경우는 curl로 확인한다.
- Zipkin은 메모리에 저장한다. Zipkin 컨테이너를 다시 만들면 기록이 사라진다.
- 테스트를 마치면 `restore`로 되돌린다. Slack 알림을 확인할 때도 `restore` 후 본다.
- 시나리오와 입력값은 팀 Notion 「기능 테스트」에 있다. 실행 기록은 각자의 테스트 일지에 남긴다.
