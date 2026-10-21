# 로컬 실행 가이드

서버를 띄우고, 바꾼 것만 다시 올리고, 내리는 방법을 정리한다. 원본은 haneol `doc/guide/16-local-run.md`와 kej README의 IDE 병행 절차이고, 이 저장소의 compose 구성에 맞췄다. 문제가 생기면 [문제 해결 기록](../troubleshooting.md)을 본다.

## 1. 처음 한 번
```bash
script/run.sh            # .env가 없으면 .env.example을 복사하고, 이미지 빌드 후 전체 기동
script/run.sh --check    # 기동 후 완료 기준 확인(script/e2e-check.sh)까지
```

| 확인 | 주소 |
|---|---|
| Swagger(Gateway 통합) | http://localhost:8080/swagger-ui.html |
| Eureka 대시보드 | http://localhost:8761 |
| Config Server가 내려주는 설정 | http://localhost:8888/user-service/default |
| Grafana 대시보드(로그인 없이 보기) | http://localhost:3000 |
| Prometheus 수집 대상 | http://localhost:9090/targets |

- 빌드는 Dockerfile 안에서 한다. 로컬에서 jar를 먼저 만들 필요가 없다.
- MASTER 계정은 `.env`의 `MASTER_USERNAME`/`MASTER_PASSWORD`(기본 `master` / `Master!2026`)로 user-service가 기동할 때 만든다.
- 토큰: `POST /api/v1/auth/login`으로 받은 `accessToken`을 Swagger 오른쪽 위 Authorize에 넣는다. `Bearer` 없이 토큰만 넣는다.
- 공통 인프라 통합 전에 쓰던 DB 볼륨은 이어 쓰지 않는다. 처음 한 번은 `script/run.sh --reset`으로 시작한다(팀 결정, 루트 `README.md`).

## 2. 무엇이 뜨나
`docker compose up`은 컨테이너 12개를 모두 띄운다. compose `profiles`로 나누지 않았다.

| 구분 | 컨테이너 | 호스트 포트 |
|---|---|---|
| DB | `postgres` (스키마 5개, 서비스 계정 5개) | `127.0.0.1:5432` |
| 인프라 | `config-server`, `eureka-server` | `127.0.0.1:8888`, `127.0.0.1:8761` |
| 분산 추적 | `zipkin` (Micrometer Tracing이 보낸 구간 수집·조회, 메모리 저장) | `127.0.0.1:9411` |
| 메트릭 | `prometheus` (Eureka에서 찾은 서비스의 `/actuator/prometheus`를 15초마다 수집), `grafana` (대시보드) | `127.0.0.1:9090`, `127.0.0.1:3000` |
| 진입점 | `gateway-service` | `8080` |
| 비즈니스 | `user`, `hub`, `company`, `order`, `delivery` 서비스 | 공개하지 않음 |

비즈니스 서비스 포트를 공개하지 않으므로 외부 요청은 Gateway(8080)로만 들어온다. 같은 이유로 `--scale`로 인스턴스를 늘려도 포트가 충돌하지 않는다. 구역별 포트 노출은 [로컬 Docker 네트워크 구역도](../design/local-network.md)에 그림으로 정리했다.
Docker 네트워크는 앞단 `logistics-net`(대역 `10.210.10.0/24`)과 데이터 `logistics-data` 둘이다. postgres는 데이터 쪽에만 있어 Gateway·관측 도구는 DB에 닿지 않고, 업무 서비스만 두 네트워크에 함께 붙는다(ADR 0020). 이전 구성으로 띄운 스택은 `docker compose down` 뒤 다시 올리면 새 네트워크로 만들어진다. DB 볼륨은 그대로다.
Gateway의 actuator(헬스 체크·메트릭)는 공개하지 않는 관리 포트 8090에 있다. 늘린 인스턴스도 Prometheus가 Eureka에서 찾아 수집한다([메트릭 모니터링](monitoring.md)).

## 3. 서버 올리는 방법

### 3.1 전체
```bash
script/run.sh
```

### 3.2 필요한 서비스만
```bash
script/run.sh hub-service order-service
# 직접 하려면: docker compose build hub-service order-service && docker compose up -d hub-service order-service
```
이름을 지정한 서비스와 그 `depends_on`(postgres, config-server, eureka-server)만 뜬다. 다시 빌드하는 것은 지정한 서비스뿐이다. `docker compose up -d --build hub-service`는 의존 서비스까지 다시 빌드하고, 빌드할 때마다 이미지 ID가 바뀌어 config-server와 eureka-server 컨테이너도 다시 만든다. 주문 생성처럼 다른 서비스를 호출하는 기능은 호출 대상도 함께 띄운다. 대상이 없으면 `503 SERVICE_UNAVAILABLE`이다.

| 하려는 것 | 함께 필요한 서비스(인프라·gateway·user 외) |
|---|---|
| 허브·경로·재고 API | hub |
| 업체·상품 API | company, hub |
| 주문 생성 | order, company, hub, delivery |
| 배송 조회·상태 변경 | delivery, hub |

### 3.3 인스턴스 여러 개
```bash
script/run.sh --scale hub-service=2
```
Gateway와 Feign은 Eureka에 등록된 인스턴스에 번갈아 보낸다.

### 3.4 작업 중인 서비스는 IDE로
```bash
docker compose up -d postgres config-server eureka-server gateway-service user-service
docker compose stop hub-service          # 도커로 떠 있었다면 내린다
# IntelliJ에서 HubServiceApplication 실행
```
- 서비스의 접속 기본값이 `localhost`(Config Server 8888, Eureka 8761, DB 5432)와 `.env.example`의 DB 비밀번호라서 따로 지정하지 않아도 된다.
- `.env`에서 호스트 포트를 바꿨다면(8장) IDE 실행 설정에도 같은 값을 환경 변수로 넣는다: `DB_PORT`(=`POSTGRES_PORT`), `CONFIG_SERVER_URL`, `EUREKA_URL`, 필요하면 `SERVER_PORT`. 넣지 않으면 기본값(5432, 8888, 8761)으로 접속한다. 같은 PC에 다른 스택이 그 포트에 떠 있으면 그쪽 DB에 붙어 Flyway가 실행되므로 주의한다.
- user-service와 gateway-service를 IDE로 띄울 때는 실행 설정에 `JWT_SECRET` 환경 변수를 넣는다. 기본값을 두지 않았다.
- 같은 서비스를 Docker와 IDE에서 동시에 띄우면 Eureka에 두 인스턴스가 등록되고 Gateway가 번갈아 호출한다. Docker 쪽은 내려 둔다.
- 서비스를 `localhost:{포트}`로 직접 부르면 Gateway를 거치지 않아 `X-User-*` 헤더가 없다. `@CurrentUser`를 쓰는 API는 401이다. Gateway(8080)로 부른다.
- Docker 안의 Gateway가 IDE로 띄운 서비스에 닿지 못하면 IDE 실행 설정에 `EUREKA_PREFER_IP=true`를 넣거나 Gateway도 IDE로 띄운다. IDE 서비스가 기본값(호스트 이름)으로 Eureka에 등록하면 컨테이너가 그 이름을 찾지 못할 수 있다(미확인, 환경마다 다르다).

## 4. 바꾼 것만 다시 올리기
`script/run.sh {서비스}`는 그 서비스 이미지만 다시 빌드하고 컨테이너를 교체한다. 먼저 내릴 필요가 없다.

| 바꾼 것 | 할 일 |
|---|---|
| 서비스 Java 코드 | `script/run.sh {서비스}` |
| `common`, `common-core` | 쓰는 서비스를 모두 다시 빌드한다. `script/run.sh` |
| `config-repo/*.yml` | `docker compose up -d --build config-server` 후 `docker compose restart {서비스}`. 설정이 config-server jar 안에 들어가고, 서비스는 기동할 때만 설정을 읽는다([설정 보안 가이드](config-security.md) 4장) |
| `.env` | `docker compose up -d {서비스}`. 환경 변수는 컨테이너를 만들 때 정해지므로 `restart`로는 바뀌지 않는다 |
| 새 마이그레이션(`V{n}__*.sql`) 추가 | `script/run.sh {서비스}`. 기동할 때 Flyway가 적용한다 |
| 루트 `build.gradle` 공통 의존성(예: 분산 추적, 메트릭) | `script/run.sh`. 모든 서비스 이미지를 다시 빌드한다 |
| `infra/prometheus/prometheus.yml` | `docker compose restart prometheus` |
| `infra/grafana/**`(데이터소스, 대시보드 JSON) | `docker compose restart grafana`. 화면에서 고친 대시보드는 저장되지 않는다 |
| 이미 적용된 마이그레이션 수정, `infra/postgres/init.sh`, DB 비밀번호 | `script/run.sh --reset` (DB 데이터 삭제) |
| 덧붙임 compose 파일로 설정 켜기(예: 배송 부하 테스트 API) | `COMPOSE_FILE=docker-compose.yml:docker-compose.loadtest.yml script/run.sh delivery-service`. 끌 때는 `COMPOSE_FILE` 없이 같은 명령. 절차는 `delivery-service/docs/load-test.md` |
| 덧붙임 compose 파일 여러 개 합치기(예: 장애 격리 실험) | `COMPOSE_FILE=docker-compose.yml:docker-compose.loadtest.yml:docker-compose.fault.yml script/run.sh --reset`. 뒤 파일이 앞 파일의 설정을 덮어쓴 한 벌로 뜬다. 합친 결과는 `docker compose config`로 확인한다. `docker-compose.fault.yml`은 Gateway 권한 캐시를 1시간으로 늘리고 delivery·order의 Tomcat 스레드 지표를 켠다([장애 격리 실험](../../delivery-service/docs/fault-isolation.md) 5절). 구간 배정 배치 창은 `ASSIGNMENT_BATCH_WINDOW_MS`(기본 0)로 바꿔 띄운다(delivery 결정 로그 28) |
| 주문 → DM 끝단 측정(Slack 대역) | 위 세 파일 뒤에 `docker-compose.order-dm.yml`을 붙인다. `slack-stub` 컨테이너(127.0.0.1:9900)가 함께 뜨고 order의 Slack DM·Webhook이 대역으로 간다. 실제 Slack으로는 보내지 않는다. 절차는 `delivery-service/docs/load-test.md` 8절 |

- 교체 직후 몇 초 동안 Gateway가 503을 줄 수 있다. 새 인스턴스가 Eureka에 등록되고 Gateway가 목록을 다시 받을 때까지다(조회 주기 5초).
- 옛 이미지 정리: 가끔 `docker image prune -f`

## 5. 내리기
| 명령 | 효과 |
|---|---|
| `docker compose stop {서비스}` | 그 서비스만 멈춘다(`start`로 다시 시작) |
| `docker compose down` | 프로젝트의 컨테이너·네트워크 삭제. DB 데이터는 남는다 |
| `docker compose down -v`, `script/run.sh --reset` | DB 볼륨까지 삭제. 다음 기동 때 스키마·계정·MASTER를 새로 만든다 |

## 6. 서비스를 직접 호출할 때
```bash
# 도커 네트워크 안에서 호출한다. /internal/**도 부를 수 있다.
docker compose exec gateway-service curl -s http://user-service:8081/internal/users/{userId}

# 사용자 헤더가 필요한 API는 Gateway가 넣어 주던 헤더를 직접 넣는다
docker compose exec gateway-service curl -s http://user-service:8081/api/v1/users/me \
  -H 'X-User-Id: {uuid}' -H 'X-Username: master' -H 'X-User-Role: MASTER' -H 'X-Delivery-Manager: false'
```

## 7. 로그와 상태
```bash
docker compose ps                               # 컨테이너 상태
docker compose logs -f user-service             # 서비스 로그
docker compose logs user-service | grep MASTER  # MASTER 계정 생성 여부
script/e2e-check.sh                             # 완료 기준 확인
script/db-check.sh                              # 서비스 계정 권한 확인
```

로그 줄의 스레드 이름 뒤 `[traceId-spanId]`가 분산 추적 ID다. 같은 요청이 거친 서비스는 같은 traceId를 가지며, Zipkin(http://localhost:9411)에서 traceId로 서비스별 구간을 본다. 여러 서비스 로그를 한 화면에 모으고 traceId로 줄을 모으려면 `script/api-test.sh`를 쓴다([Swagger 수동 테스트와 요청 흐름 보기](api-test.md)).

## 8. 기존 환경과 병행하기
`.env`의 `COMPOSE_PROJECT_NAME`과 호스트 포트(`POSTGRES_PORT`, `CONFIG_SERVER_PORT`, `EUREKA_PORT`, `GATEWAY_PORT`)를 다르게 정한다. 자세한 내용은 루트 `README.md`에 있다.

## 9. 물류 운영 테스트 UI

전체 메뉴와 주문·배송 기능을 브라우저에서 테스트하려면 [frontend 실행 안내](../../frontend/README.md)를 따른다.
`script/ui-test.sh up`은 별도 `logistics-ui-test` 프로젝트·전용 환경 파일·DB 볼륨을 사용한다. 앞단 네트워크 대역은 개발 스택과 겹치지 않게 `10.210.30.0/24`를 쓴다. 기본 개발 환경은 유지하고 Slack Webhook은 항상 비활성화한다. DM은 기본 비활성화이며 명시한 토큰·워크스페이스·수신 허용 목록으로만 켠다.
`seed`는 공개 API로 테스트 계정·업체·상품·주문과 멘토 가이드 시나리오 데이터(발제 예시 경기·부산 업체, 배송담당자가 없는 대전 허브의 롤백용 업체)를 준비한다. 다시 실행해도 중복되지 않는다. 멘토는 http://127.0.0.1:5174/guide 에서 시작한다([멘토·튜터 테스트 안내](mentor-test.md)). `frontend`는 http://127.0.0.1:5174 에서 실행하고 Gateway는 18080번, Zipkin은 19411번, Grafana는 13000번, Prometheus는 19090번이다.
실제 DM이 켜져 있으면 `seed`와 브라우저 E2E는 주문 생성 전에 중단한다. 최초 DM 배포는 config-server·user·delivery·order를 빌드하고 user Flyway V2·V3를 적용한다. [Slack DM 설정·실패 처리](slack-dm.md)를 따른다.
종료는 프런트 실행 터미널의 Ctrl+C와 `script/ui-test.sh down`이다. 테스트 DB 볼륨은 유지한다.
