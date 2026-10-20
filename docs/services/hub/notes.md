# hub 구현 노트

## 재고 고도화 H-05·H-06, I-04~I-06 (2026-10-19)
### 처리 과정
- V4 마이그레이션으로 `p_hub_stocks`(허브×상품 부분 유니크, 수량 CHECK)와 `p_hub_stock_histories`(멱등 부분 유니크)를 만들었다.
- domain: `HubStock`(`increase`, `decrease`), `HubStockHistory`, 저장소 인터페이스. infrastructure: 행 락 쿼리, QueryDSL 검색, company I-07·I-08 `ProductClient`.
- application: `StockService`(재고 변경 트랜잭션), `StockRegistrationService`(트랜잭션 밖에서 허브·상품 확인과 생성 경합 재시도), `StockQueryService`.
- presentation: `HubStockController`(H-05, H-06 MASTER·상품 업체 관리자, ADR 0003), `InternalStockController`(I-04~I-06).

### 핵심
- 입고·할당은 재고 행을 `FOR UPDATE`로 잠그고 바꾼다. 안 잠그면 동시 요청이 서로의 변경을 덮어쓴다.
- 같은 주문의 중복 차감·복원은 잠금 뒤 이력 확인과 이력 유니크 인덱스가 막는다.
- company 호출은 DB 트랜잭션 밖에서 하려고 클래스를 나눴다. 같은 클래스 안 호출은 프록시를 거치지 않는다.
- I-05는 공급업체 허브 한 곳만 차감한다(ADR 0003). 허브 간 이동은 배송으로 처리해야 하는데 delivery 설계가 이를 다루지 않는다.

### 확인 질문
- 입고에서 행 락을 빼면 20건 × 5 동시 입고의 결과가 100보다 작아질 수 있는 이유는?
- 멱등 확인을 잠금 전이 아니라 잠금 뒤에 하는 이유는?
- 같은 클래스 안에서 `@Transactional` 메서드를 호출하면 왜 트랜잭션이 적용되지 않는가?

### 메모
- 실제 기동과 Gateway 경유 H-05·H-06, 컨테이너 안 I-04~I-06 호출은 확인했다(2026-10-19, 결과는 `docs/progress/feat-hub-adv.md`). order의 재고 호출은 코드가 없어 확인하지 못했다.
- 복원(I-06)한 주문을 같은 `orderId`로 다시 할당(I-05)하면 처음에는 차감 없이 처음 결과를 반환하는 결함이 있었다. 2026-10-20에 409 `STOCK_ALREADY_RESTORED`로 거부하도록 고쳤다. order가 취소 뒤 같은 `orderId`로 재할당하는 흐름이 있는지는 ORDER-002에서 확인한다.
- SA와 다른 부분은 ADR 0003에만 기록하고 SA는 수정하지 않았다.

## 재고 성능 검토 반영 (2026-10-20)
### 처리 과정
- 코드 검토로 재고 쓰기 경로의 병목 후보를 정리하고 두 가지를 반영했다. 부하 테스트로 측정한 결과가 아니라 코드를 읽고 추론한 것이다.
- `config-repo/hub-service.yml`: company-service Feign 타임아웃을 연결 1초, 읽기 2초로 줄였다. 공통값(20초)에서 재시도 3회가 곱해지면 상품 존재 확인(I-08) 하나가 최악 60초까지 스레드를 잡는다.
- `StockRegistrationService`: 재시도를 유니크 위반(SQLState `23505`)으로 한정했다. CHECK·FK 위반은 다시 해도 같은 결과라 바로 던진다.

### 핵심
- 한 재고 행의 처리량 상한은 락 보유 시간의 역수다. 락 안의 쿼리 수가 곧 처리량이다.
- `lock_timeout`(3초)이 없으면 대기 요청이 Hikari 연결을 모두 잡아 무관한 API까지 멈춘다.

### 보류한 개선(측정 후 판단)
- 원자적 `UPDATE ... WHERE quantity >= :q`로 SELECT 락 제거: 락 보유 시간이 줄지만 Auditing·`BaseEntity` 규칙을 우회한다.
- 허브 조회 캐시, `(hub_id, created_at, hub_stock_id)` 검색 인덱스, 이력 보관 정책.
- 복수 허브를 차감하게 되면 `restore`의 락 순서를 stockId 오름차순으로 고정해 교착을 막는다.

### 확인 질문
- `lock_timeout`을 `connection-init-sql`로 거는 이유와 적용 범위는?
- 유니크 위반이 아닌 무결성 위반을 재시도하면 왜 안 되는가?

## H-06 업체 관리자 입고 (2026-10-20)
### 처리 과정
- 컨트롤러의 `@RequireRole(MASTER)`를 없애고 `StockRegistrationService.inbound(user, command)`가 판단한다. MASTER는 이전과 같고, 그 밖의 사용자는 company I-08(상품 → `companyId`)과 I-07(업체 → `managerId`, `hubId`)로 확인한다.
- 포트 `CompanyReader`와 어댑터를 새로 두고 I-07은 기존 `ProductClient`에 더했다. 별도 Feign 클라이언트로 나누면 `company-service` 이름에 걸어 둔 타임아웃이 적용되지 않는다.

### 핵심
- 역할(MASTER/USER)은 컨트롤러가, 소유자(업체 관리자) 확인은 application이 한다. 업체 관리자 데이터는 company에 있어 서비스에서 호출로 확인한다.
- 업체 관리자는 자기 업체의 소속 허브에만 입고한다. 근거와 감수할 점(company 호출 2회, 관리자 변경 즉시 반영)은 ADR 0003에 있다.
- 재고 행이 없을 때 동시에 들어온 입고는 유니크 위반을 받아 한 번 다시 시도한다. 실제 PostgreSQL 동시 입고 테스트로 확인했다.

### 확인 질문
- 역할 검사와 소유자 검사를 각각 어느 계층에서 하는가?
- 같은 `company-service`를 부르는 Feign 인터페이스를 둘로 나누면 왜 타임아웃 설정이 빠지는가?
