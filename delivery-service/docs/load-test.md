# 배송 부하 테스트 (k6)

먼저 지금 상태를 재고(기준선), 하나씩 고친 뒤 같은 조건으로 다시 재서 비교한다. 무엇이 병목일지에 대한 분석은 [학습 노트](../../docs/learning/haneol/qna/2026-10-15-delivery-high-traffic-bottlenecks.md)에 있다. 이 문서는 그 가설을 숫자로 확인하는 절차다. 실험 3(user 장애와 장애 격리)은 따로 [장애 격리 실험](fault-isolation.md)에 자세히 정리했다.

## 1. 구성

| 파일 | 역할 |
|---|---|
| `load-test/k6/delivery-create.js` | 쓰기 부하. `TARGET=delivery`(기본)는 배송 생성만, `TARGET=order`는 주문 생성부터 |
| `load-test/k6/delivery-read.js` | 읽기 부하. D-01 허브별 목록, D-01 배송담당자 목록, D-02 단건을 동시에 |
| `infra/postgres/seed/delivery-e2e.sql` | 계정·업체·상품 시드(E2E와 같다) |
| `infra/postgres/seed/delivery-load.sql` | 과거 배송 대량 데이터(기본 30만 건). 몇 달 운영한 DB를 흉내 낸다 |
| `docker-compose.loadtest.yml` | delivery-service에 부하 테스트 API를 켜는 덧붙임 설정 |
| `load-test/k6/fault-isolation.js`, `load-test/fault-isolation.sh` | 장애 격리 실험. 생성·조회를 동시에 보내고 중간에 user-service를 멈춘다([fault-isolation.md](fault-isolation.md)) |
| `docker-compose.fault.yml` | 장애 격리 실험용 덧붙임 설정(Gateway 권한 캐시 10분, Tomcat 스레드 지표) |
| `LoadTestDeliveryController` | `POST /api/v1/deliveries/load-test`. I-09와 같은 처리를 Gateway로 부른다(결정 로그 24) |

주문을 거치지 않는 API를 둔 이유: 주문 생성(O-01)을 재면 order·company의 시간이 섞여 배송 쪽 변화가 묻힌다. 배송만 바꿨을 때의 효과를 보려면 배송 생성만 재야 한다. 전체 경로는 `TARGET=order`로 따로 잰다.

부하 테스트 API는 기본으로 꺼져 있다. 프로퍼티가 없으면 빈이 없어 호출할 수 없고(405), 켜도 MASTER만 호출할 수 있으며 Swagger에 나오지 않는다.

## 2. 준비 (한 번)

```bash
brew install k6          # 또는 Docker: 아래 "Docker로 k6 실행"
```

## 3. 실행 순서

비교가 공정하려면 **매 측정을 같은 DB 상태에서 시작한다.** 앞 측정이 만든 배송이 담당 건수와 목록 크기를 바꾸기 때문이다.

```bash
# 1) DB를 비우고 전체 기동. 덧붙임 설정으로 delivery-service의 부하 테스트 API를 켠다
COMPOSE_FILE=docker-compose.yml:docker-compose.loadtest.yml script/run.sh --reset

# 2) 서비스가 모두 뜰 때까지 기다린 뒤 시드와 대량 데이터 적용(대량 데이터는 30만 건에 1~2분)
script/delivery-e2e-check.sh          # 기동 대기 + 시드 적용 + 기능 확인을 한 번에 한다. 모두 PASS인지 본다
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -v ON_ERROR_STOP=1 -v count=300000' \
  < infra/postgres/seed/delivery-load.sql

# 3) 측정. 결과 JSON은 load-test/results/에 남긴다(저장소에 올리지 않는다)
mkdir -p load-test/results
k6 run --summary-export=load-test/results/create-baseline.json load-test/k6/delivery-create.js
k6 run --summary-export=load-test/results/read-baseline.json   load-test/k6/delivery-read.js
```

- `COMPOSE_FILE`은 docker compose가 읽는 환경 변수다. 두 파일을 합쳐 쓰므로 delivery-service에만 `DELIVERY_LOAD_TEST_ENABLED=true`가 더해진다. 빼고 다시 띄우면(`script/run.sh delivery-service`) 부하 테스트 API가 꺼진다.
- 켜졌는지 확인: MASTER 토큰을 붙여 빈 본문(`{}`)으로 `POST /api/v1/deliveries/load-test`를 보낸다. 400(`VALIDATION_ERROR`)이면 켜져 있고, 405면 꺼져 있다(매핑이 없어 같은 모양의 경로 `/{deliveryId}`로 해석되는데 거기에는 POST가 없다).
- 부하 크기: `PEAK_RATE=40 k6 run ...`(생성, 초당 건수), `RATE=50 DURATION=3m k6 run ...`(읽기, 시나리오별 초당 건수).
- 주문 경로 전체: `TARGET=order k6 run load-test/k6/delivery-create.js`.

### Docker로 k6 실행
k6를 설치하지 않으려면 컨테이너로 돌린다. 컨테이너 안에서는 `localhost`가 자기 자신이라 `host.docker.internal`로 Gateway를 찾는다.
```bash
docker run --rm -i -v "$PWD/load-test:/load-test" -e BASE_URL=http://host.docker.internal:8080 \
  grafana/k6 run --summary-export=/load-test/results/create-baseline.json /load-test/k6/delivery-create.js
```

## 4. 결과 읽는 법

k6 요약에서 볼 줄:
| 지표 | 뜻 | 볼 것 |
|---|---|---|
| `http_req_duration{name:...}` | API별 응답 시간 | p95, p99. 평균은 느린 요청을 가린다 |
| `http_req_failed{name:...}` | 실패율 | 0%가 아니면 어떤 응답인지 서비스 로그를 본다 |
| `dropped_iterations` | 정한 속도로 보내지 못한 요청 수 | 0보다 크면 서버가 밀려 VU가 모자랐다는 뜻. 처리량 한계 신호 |
| `iterations` | 실제 보낸 요청 수 | 목표 속도 × 시간과 비교 |
| `checks` | 상태 코드 확인 통과율 | 100%가 아니면 결과를 비교에 쓰지 않는다 |

임계값(`thresholds`)을 넘으면 마지막에 표시되고 종료 코드가 0이 아니다. 기준선을 잰 뒤 목표에 맞게 조정한다.

## 5. 서버 쪽에서 같이 볼 것

응답 시간만으로는 원인을 모른다. 같은 시간에 DB와 커넥션을 본다.

```bash
# 느린 쿼리 실행계획: 담당 건수(I-09가 매번 실행)
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics' <<'SQL'
EXPLAIN (ANALYZE, BUFFERS)
SELECT manager_id, count(*) FROM delivery_schema.p_delivery_routes
WHERE manager_id IN ('e2e00000-0000-4000-8000-000000000002', 'e2e00000-0000-4000-8000-000000000003')
  AND status IN ('WAITING', 'MOVING') AND deleted_at IS NULL
GROUP BY manager_id;
SQL

# 느린 쿼리 실행계획: D-01 허브별 최신순
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics' <<'SQL'
EXPLAIN (ANALYZE, BUFFERS)
SELECT delivery_id, created_at FROM delivery_schema.p_deliveries
WHERE departure_hub_id = '6f39d519-4947-4a95-a454-58c25728f388' AND deleted_at IS NULL
ORDER BY created_at DESC LIMIT 10;
SQL

# 측정 중 서비스 로그(타임아웃, 커넥션 대기 에러)
docker compose logs -f delivery-service
```

실행계획에서 볼 것: `Index Scan`인지 `Seq Scan`인지, `Rows Removed by Filter`가 큰지(인덱스로 많이 읽고 버렸다), `Sort`가 따로 있는지, `Buffers: shared read`(디스크에서 읽은 블록).

## 6. 실험 계획

한 번에 하나만 바꾼다. 둘을 같이 바꾸면 어느 쪽 효과인지 알 수 없다.

| # | 가설 | 바꿀 것 | 볼 지표 |
|---|---|---|---|
| 0 | (기준선) | 없음 | 생성·읽기 전 지표, 두 쿼리의 실행계획 |
| 1 | 담당 건수 쿼리가 과거 도착 구간까지 훑어 생성이 느려진다 | 진행 중 구간 부분 인덱스(V2) | `I-09 직접` p95, 담당 건수 실행계획 `Rows Removed by Filter` |
| 2 | 생성마다 user를 2번 부르는 시간이 크다 | I-14·I-01 캐시 | `I-09 직접` p95, 최대 처리량(`dropped_iterations`가 생기기 시작하는 PEAK_RATE) |
| 3 | user가 느려지면 배송 전체가 멈춘다 | 측정만: 생성 부하 중 `docker compose pause user-service` 30초 → `unpause`. 그다음 짧은 타임아웃·서킷 브레이커 | 멈춘 동안 실패가 빨리 나는지, 스레드가 쌓여 읽기 API까지 느려지는지. 절차·지표·개선 단계는 [fault-isolation.md](fault-isolation.md) |
| 4 | 허브별 최신순 목록이 정렬을 따로 한다 | `(departure_hub_id, created_at DESC)` 부분 인덱스 | `D-01 허브별` p95, 실행계획의 `Sort` |

3번은 Gateway의 권한 캐시(60초)도 user-service를 부르므로, 캐시가 만료되면 모든 요청이 영향을 받는다. 배송만의 효과를 보려면 멈추는 시간을 60초보다 짧게 한다.

## 7. 결과 기록

측정할 때마다 한 줄씩 채운다. 같은 PEAK_RATE·RATE, 같은 데이터(`count`), 같은 PC에서 잰 값끼리만 비교한다.

| 날짜 | 실험 | 조건 | 생성 p95 / p99 (ms) | 생성 실패율 | dropped | D-01 허브별 p95 | D-01 담당자 p95 | D-02 p95 | 메모 |
|---|---|---|---|---|---|---|---|---|---|
| | 0 기준선 | PEAK_RATE=20, RATE=20, count=300000 | | | | | | | |
