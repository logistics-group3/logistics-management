# 배송 테스트 실행 가이드

배송 테스트는 세 갈래다. 앞의 두 개는 `./gradlew`로 돌고, 세 번째는 전체 서비스를 띄운 뒤 직접 돌린다.

| 구분 | 무엇을 확인하나 | 실행 | 필요한 것 |
|---|---|---|---|
| 단위·서비스 테스트 | 상태 전이, 자동 배정, 권한 분기 | `./gradlew :delivery-service:test` | 없음 |
| 통합 테스트 | orderId 멱등, 담당 건수, 조회 범위, 논리 삭제, **동시 변경** | 같은 명령에 포함 | Docker(Testcontainers) |
| E2E | Gateway를 거친 주문 → 배송 → 완료 전체 연결, 배송 API 권한·검증 | `script/delivery-e2e-check.sh` | `script/run.sh`로 전체 기동 |
| 부하(k6) | 요청량을 올렸을 때의 응답 시간·실패율·처리량 | [`load-test.md`](load-test.md) | 전체 기동, k6 |

## 1. 동시성 통합 테스트

`DeliveryConcurrencyIntegrationTest`는 같은 배송을 바꾸는 요청이 겹칠 때 서로의 변경을 덮어쓰지 않는지 확인한다. 배송 행 잠금을 둔 이유는 [결정 로그 19](domain/delivery-decision-log.md)에 있다.

| 겹치는 요청 | 확인하는 것 |
|---|---|
| 취소(I-11) + 집하 시작(D-05) | 하나만 성공하고 진 쪽은 409. 배송과 구간 상태가 어긋나지 않는다 |
| 재배정(D-04) + 같은 구간 출발(D-05) | 바뀐 담당자와 MOVING이 함께 남는다 |
| 수령인 수정(D-03) + 집하 시작(D-05) | 바뀐 수령인과 PICKING_UP이 함께 남는다 |
| 같은 구간 출발(D-05) 두 번 | 한 번만 성공한다 |

겹침을 우연에 맡기지 않는다. 먼저 온 요청을 트랜잭션에 묶어 커밋 직전에 멈추고, 그 사이 나중 요청을 보낸다. 잠금이 없으면 나중 요청이 먼저 커밋하고 앞 요청이 그것을 덮어써 테스트가 실패한다(잠금을 넣기 전 4개 모두 실패를 확인했다).

```bash
./gradlew :delivery-service:test --tests "*DeliveryConcurrencyIntegrationTest"
```

동시성 테스트를 두지 않은 곳과 이유:
- I-09 같은 orderId 동시 생성: `DeliveryIntegrationTest.concurrentCreate`가 이미 확인한다(유니크 제약).
- 다른 배송 사이의 자동 배정 편차: 잠금을 두지 않기로 했다(결정 로그 16). 편차는 1~2건이다.
- I-11 취소와 I-12 원복의 경쟁: 둘 다 order가 한 주문 안에서 차례로 부른다.

## 2. E2E 직접 실행

### 준비
1. Docker Desktop을 켠다.
2. 전체를 띄운다. 처음이거나 DB 상태가 꼬였으면 `--reset`을 붙인다(DB 데이터가 지워진다).
   ```bash
   script/run.sh            # 또는 script/run.sh --reset
   ```
3. 배송 코드를 고친 뒤라면 그 서비스만 다시 빌드해 올린다.
   ```bash
   script/run.sh delivery-service
   ```

### 실행
```bash
script/delivery-e2e-check.sh
```
- 서비스 6개가 Eureka에 UP으로 올라올 때까지 최대 5분 기다린다.
- 시드 데이터(`infra/postgres/seed/delivery-e2e.sql`)를 넣고 시작한다. 이미 있으면 건너뛰므로 여러 번 돌려도 된다. 다시 넣지 않으려면 `--no-seed`.
- 끝에 `결과: PASS n / FAIL m`이 나온다. FAIL이 있으면 종료 코드가 1이다.

### 확인하는 것
| 단계 | 내용 |
|---|---|
| [1] 전체 연결 | 수령업체 담당자가 주문(O-01) → 배송 생성(I-09)과 자동 배정 → 구간 담당자가 집하·업체 배송을 출발·도착(D-05) → `DELIVERED` → MASTER가 주문 삭제(O-06이 I-10으로 완료 확인 후 I-13) → 배송 404 |
| [2] 조회 권한 | 담당 구간이 없는 배송담당자는 D-02 403, D-01 0건. 허용하지 않는 정렬은 400 |
| [3] D-03 | 빈 요청 400, 집하 담당자 403, 업체 배송 담당자 200 |
| [4] D-04 | MASTER가 아니면 403, 다른 허브 담당자 400, 배송담당자가 아니면 404, 같은 허브 담당자 200 |
| [5] D-05 | 앞 구간 도착 전 출발 409, `WAITING` 요청 409, 담당자가 아니면 403 |
| [6] 취소·삭제 | 집하 전 주문 취소(O-05 → I-11) 시 배송·구간 `CANCELED`, 집하 시작 후 취소는 409이고 배송은 그대로 |

### 시드 데이터
비밀번호는 모두 `Sparta!2026`이다. 수동 호출(Swagger `http://localhost:8080/swagger-ui.html`, curl)에도 그대로 쓴다.

| username | 역할 | 쓰임 |
|---|---|---|
| `dmaster` | MASTER | 재배정, 주문 삭제 |
| `dmgr1`, `dmgr2` | 서울 허브 배송담당자 | 자동 배정 후보, 재배정 대상 |
| `dmgr3` | 경기 북부 허브 배송담당자 | 담당 아닌 조회 403, 허브가 다른 재배정 400 |
| `dsupplier` | 공급업체 담당자 | `배송E2E 공급업체`(`...0101`), 상품 `...0201` |
| `dreceiver` | 수령업체 담당자 | `배송E2E 수령업체`(`...0102`), 주문자 |
| `duser` | 일반 사용자 | 배송담당자가 아닌 재배정 대상 404 |

ID는 `e2e00000-0000-4000-8000-0000000000xx` 형식으로 고정했다. 정확한 값은 시드 파일에 있다.

시드만 넣고 직접 호출해 보려면:
```bash
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -v ON_ERROR_STOP=1' < infra/postgres/seed/delivery-e2e.sql
```

수동 호출 예:
```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"dreceiver","password":"Sparta!2026"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])')
curl -s -X POST localhost:8080/api/v1/orders -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: manual-$(date +%s)" \
  -d '{"supplierCompanyId":"e2e00000-0000-4000-8000-000000000101","receiverCompanyId":"e2e00000-0000-4000-8000-000000000102","productId":"e2e00000-0000-4000-8000-000000000201","quantity":1}'
```

### 한계
- 업체 두 곳이 같은 허브(서울)라 2구간 흐름만 확인한다. 3구간(허브 이동)은 hub I-03(`HUB-001`)이 생긴 뒤 다른 허브 업체를 시드에 더해 확인한다.
- 내부 API(I-09~I-13)는 Gateway가 막아(404) 주문 API를 거쳐 간접으로 부른다. I-12(취소 원복)는 주문 저장 실패 보상에서만 불려 E2E로 만들 수 없다. 서비스 테스트로 확인한다.
- 서울 허브에 시드 밖의 배송담당자가 있으면 그 사람이 배정될 수 있다. 이때 "구간 담당자는 서울 허브 배송담당자" 항목이 FAIL이고, 나머지 진행은 MASTER로 대신한다. `script/run.sh --reset`으로 새로 시작하면 사라진다.

### 실패할 때
| 증상 | 확인 |
|---|---|
| `준비되지 않았다` | `docker compose ps`, `docker compose logs -f delivery-service`. Eureka 등록에 수십 초 걸린다 |
| `시드 적용` 실패 | user·company 서비스가 마이그레이션을 끝냈는지(테이블이 있어야 한다). 시드 파일의 오류 메시지는 `--no-seed` 없이 위 psql 명령으로 직접 넣어 본다 |
| 로그인 실패 | 시드가 들어갔는지 `docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -c "select username, created_by from user_schema.p_users"'`에서 `dmaster` 등이 `SEED`로 보이는지 |
| O-01이 404 `DELIVERY_MANAGER_NOT_FOUND` | 서울 허브 배송담당자가 없다. 시드를 다시 넣는다 |
| O-01이 503 | order → company·delivery 호출 실패. `docker compose logs order-service delivery-service` |
