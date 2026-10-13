# 16. 로컬 실행 가이드 — 서버 올리기 · 바꾼 것만 다시 올리기

> 최종 수정: 2026-10-13 · 대상: **전원**
> 관련: `docker-compose.yml`, `script/run.sh`, [12-config-ops](12-config-ops.md) (설정 변경), [15-troubleshooting](15-troubleshooting.md) (문제 해결)

## 0. 처음 한 번

```bash
cp .env.example .env     # JWT_SECRET 등 값 확인 (32자 이상)
script/run.sh            # 빌드 → 공통 인프라 기동
```

| 확인 | 주소 |
|---|---|
| Swagger (Gateway 통합) | http://localhost:8080/swagger-ui.html |
| Eureka 대시보드 | http://localhost:8761 |
| Config Server | http://localhost:8888/user-service/docker |

## 1. 무엇이 뜨나 — compose `profiles`

`docker-compose.yml`에서 `profiles: [domain]`이 붙은 서비스는 **domain 프로파일을 켰을 때만** 뜬다. 이름표가 없는 서비스는 항상 뜬다.

| 구분 | 서비스 | 기본 `up` | `--profile domain` |
|---|---|---|---|
| 공통 인프라 | user-db, eureka-server, config-server, gateway-service, user-service | O | O |
| 도메인 | hub / company / order / delivery 서비스 + 각 DB | X | O |

> compose `profiles`(어떤 **컨테이너**를 띄울지)와 Spring 프로파일 `local`/`docker`(서비스가 어떤 **설정 파일**을 읽을지)는 이름만 같고 관계없다. compose로 띄운 서비스는 항상 Spring `docker` 프로파일이다.

**매번 `--profile domain`을 붙이기 싫으면** `.env`에 한 줄 추가 → `docker compose up -d`, `script/run.sh`만으로 전체가 뜬다.
```
COMPOSE_PROFILES=domain
```

## 2. 서버 올리는 방법

### 2.1 전체 (공통 인프라 + 도메인 4개)

```bash
script/run.sh --profile domain
```
처음, 여러 모듈을 한꺼번에 바꿨을 때, 주문 흐름 전체를 테스트할 때.

### 2.2 필요한 서비스만 (예: hub, order)

```bash
./gradlew :hub-service:bootJar :order-service:bootJar
docker compose up -d --build hub-service order-service
```
- **이름을 직접 지정한 서비스는 프로파일이 꺼져 있어도 뜬다.** 그 서비스의 `depends_on`(hub-db, order-db, config-server, eureka)도 함께 뜬다
- Dockerfile이 `build/libs/app.jar`를 복사하므로 **jar를 먼저 빌드**해야 한다

### 2.3 작업 중인 서비스는 IDE로 (가장 빠름)

```bash
docker compose up -d hub-db eureka-server config-server   # 필요한 인프라만 도커로
docker compose stop hub-service                           # 도커로 떠 있었다면 내림
# IntelliJ에서 HubApplication 실행 (Spring local 프로파일, DB localhost:5434)
```
- 코드 수정 → IDE 재실행(5~10초), 디버거 사용 가능
- 서비스를 `localhost:{포트}`로 직접 호출하면 Gateway를 거치지 않아 `X-User-*` 헤더가 없다 → `@CurrentUser` API는 401 (헤더를 직접 넣거나 Gateway로 호출)
- 도커 안의 gateway는 IDE로 띄운 서비스(호스트 IP로 Eureka 등록)에 닿지 못할 수 있다 → 그때는 gateway도 IDE로 실행

### 2.4 서비스별로 함께 필요한 것

order-service는 주문 생성 시 Feign으로 다른 서비스를 호출한다. 호출 대상이 없으면 **503 `SERVICE_UNAVAILABLE`**.

| 하려는 것 | 필요한 서비스 (공통 인프라 외) |
|---|---|
| 허브·경로·재고 API | hub |
| 업체·상품 API | company (+ 허브 존재 확인을 하면 hub) |
| 주문 생성 | order + company + hub + delivery |
| 배송 조회·상태 변경 | delivery (+ hub) |

## 3. 바꾼 것만 다시 올리기 — 이미지·컨테이너를 지울 필요 없다

| 바꾼 것 | 할 일 | 소요 |
|---|---|---|
| `config/*.yml` | **없음** — config-server가 감지해 자동 refresh. 재시작 대상 키(12-config-ops §3.2 C)면 `docker compose restart {서비스}` | 0~10초 |
| `.env` | `docker compose up -d {서비스}` (환경 변수는 컨테이너 생성 시 고정 → `restart`로는 안 바뀜) | 수 초 |
| 서비스 Java 코드 | `./gradlew :{서비스}:bootJar && docker compose up -d --build {서비스}` | 20~40초 |
| common 모듈 | common을 쓰는 서비스 전부 위 명령 (또는 `script/run.sh`) | |
| eureka-server·config-server 코드·`application.yml` | 해당 모듈 `bootJar` + `up -d --build` (jar 안에 설정이 있음) | |
| `doc/sql/*.sql` (DDL) | `script/run.sh --reset` (**DB 데이터 삭제**) | 전체 |

- `up -d --build {서비스}`는 이미지가 바뀌었으면 **기존 컨테이너를 알아서 교체**한다. 먼저 down할 필요 없다
- 교체 직후 30초 정도는 Eureka·LoadBalancer 캐시 때문에 Gateway에서 503이 날 수 있다
- 코드는 그대로인데 컨테이너만 새로 만들고 싶으면 `docker compose up -d --force-recreate {서비스}`
- 쌓인 옛 이미지 정리: 가끔 `docker image prune -f`

## 4. 내리기 · 조심할 명령

| 명령 | 효과 |
|---|---|
| `docker compose stop {서비스}` | 그 서비스만 멈춤 (`start`로 다시) |
| `docker compose rm -sf {서비스}` | 그 서비스만 멈추고 컨테이너 삭제 |
| `docker compose down` | ⚠ **프로젝트 전체** 컨테이너·네트워크 삭제 (DB 데이터는 유지) |
| `docker compose down -v` / `script/run.sh --reset` | ⚠ **DB 볼륨까지 삭제** → 데이터·최초 MASTER 계정 재생성 |

## 5. 서비스를 직접 호출하고 싶을 때

비즈니스 서비스 포트는 호스트에 공개하지 않는다 (외부 진입점은 Gateway 8080뿐). 내부 API 테스트 등으로 직접 호출하려면:

```bash
# 도커 네트워크 안에서 curl (설정 변경 없음, /internal/**도 호출 가능)
docker run --rm --network sparta-logistics_logistics-net curlimages/curl \
  -s http://user-service:8081/internal/users/{userId}

# 헤더가 필요한 API는 Gateway가 넣어주던 헤더를 직접
  -H 'X-User-Id: {uuid}' -H 'X-Username: master01' -H 'X-User-Role: MASTER' -H 'X-Delivery-Manager: false'
```
내 PC에서만 포트를 열고 싶다면 루트에 `docker-compose.override.yml`(커밋 금지)을 만들어 `ports: ["127.0.0.1:8081:8081"]`을 추가한다. 이 경우 `--scale`은 포트 충돌로 쓸 수 없다.

## 6. 로그 보기

```bash
docker compose logs -f user-service          # 서비스 로그
docker compose logs -f config-server         # 설정 변경 감지·refresh 결과
docker compose logs user-service | grep MASTER   # 최초 MASTER 계정 생성 여부
docker compose ps                            # 떠 있는 컨테이너·상태
```
