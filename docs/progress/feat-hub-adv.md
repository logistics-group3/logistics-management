# feat/hub-adv

## 지금 하는 일
hub-service 재고 관리 고도화(SA v2.0 5.3·6.3·6.5·7.3·7.4 기준): 구현과 검증을 마쳤고 company·order 담당자 답변(COMPANY-002, ORDER-002)을 기다린다. 업체 관리자 입고 권한(H-06)을 2026-10-20에 추가했다.

## 진행 상태
- 기본 범위(H-01~H-04, I-02, I-03, 시드 허브 17개·경로 272건)는 `dev`에 병합돼 있다. 상세 기록은 [`feat-hub-crud.md`](feat-hub-crud.md), [`feat-hub-query.md`](feat-hub-query.md)에 있다.
- [x] 브랜치 `feat/hub-adv` 생성, 작업 트리 깨끗함(2026-10-19, `git status` 확인).
- [x] 구현 기준 확정(2026-10-19, 사용자 결정): 팀 합의 기록인 SA v2.0 그대로 구현한다. 최승근 ADR(`docs/services/hub/decisions/0001`, `0002`: 공급업체 허브 1곳 재고, 조건부 UPDATE)은 팀 승인 기록이 없어 채택하지 않는다. 다만 재고 할당(I-05·I-06)은 아래 ADR 0003으로 공급업체 허브 한 곳으로 한정한다. 입고 권한(H-06)도 2026-10-20에 같은 ADR 0003으로 업체 관리자까지 넓혔다. SA는 어떤 경우에도 수정하지 않고 차이는 ADR로만 남긴다(사용자 방침).
- [x] 재고 할당 범위 결정(2026-10-19, 사용자 결정): SA v2.0 "공급업체가 항상 출고"와 "허브 간 이동은 배송으로 처리"에 맞춰 I-05를 공급업체 허브 한 곳에서만 차감한다. 여러 허브에서 모아 차감하면 허브 간 이동이 생기는데 delivery(I-09는 `departureHubId` 하나)가 이를 다루지 않고 order·delivery 코드에도 재고 연동이 없다. 계약 변경: I-05 요청 `destinationHubId` → `supplierHubId`, 응답 `allocations` 목록 → `{hubId, quantity}` 한 건. 락은 단일 재고 행 `FOR UPDATE`다. 고도화 범위는 팀 합의가 없어 최소로 유지한다. 근거와 감수할 점은 [ADR 0003](../services/hub/decisions/0003-stock-allocation-supplier-hub-only.md)(제안)에 있다.
- 범위(SA 기준, hub-service 몫):
  - 테이블 `p_hub_stocks`(허브×상품 1행, `quantity >= 0` CHECK), `p_hub_stock_histories`(INBOUND/DECREASE/RESTORE, 멱등 유니크 `(hub_stock_id, order_id, change_type) WHERE order_id IS NOT NULL`).
  - H-05 재고 검색(로그인), H-06 재고 입고(MASTER와 상품 업체 관리자(2026-10-20 추가, ADR 0003), MASTER는 레코드가 없으면 company I-08로 상품 존재 확인 후 생성, 수량 증가와 이력은 한 트랜잭션).
  - I-04 재고 초기화(company 호출, 이미 있으면 기존 값 반환), I-05 재고 할당(order 호출, 공급업체 허브 한 곳에서 차감(ADR 0003), 부족하면 409, 같은 `orderId`는 기존 할당 반환), I-06 재고 복원(order 호출, 이력이 없거나 이미 복원했으면 0).
  - 동시성: 공급업체 허브의 재고 행 하나를 `FOR UPDATE`로 잠근 뒤 한 로컬 트랜잭션에서 차감한다(SA 7.4의 다중 행 `hub_id` 순 잠금은 ADR 0003으로 단일 행이 됐다).
- [x] H-06의 company I-08 호출 방식 확정(2026-10-19, 사용자 결정): SA대로 hub가 company를 호출한다(A안). SA 2.3의 "hub는 호출하지 않는 최하위 서비스"는 재고가 고도화로 빠진 기본 범위 기준 설명이고 H-06은 "(고도화)"로 표시돼 있어 SA와 어긋나지 않는다. 그래서 ADR은 쓰지 않고 SA 2.3도 고치지 않는다. 현재 서비스 간 호출에 양방향 쌍은 없고(gateway→user, order→company·delivery·user, delivery→hub·user, company→hub·user, user→hub) 이 호출이 company↔hub의 첫 양방향이 된다. 요청 단위 순환은 없다(I-02·I-04는 업체·상품 생성 때, I-08은 입고 때만 호출). 입고는 company 장애 시 503 `SERVICE_UNAVAILABLE`로 실패한다.
- [x] 에러 코드: `STOCK_INSUFFICIENT`(409)를 `HubErrorCode`와 `docs/error-codes.md`에 추가했다(복원 뒤 재할당 거부용 `STOCK_ALREADY_RESTORED`(409)는 2026-10-20에 더했다). `PRODUCT_NOT_FOUND`·`COMPANY_NOT_FOUND`는 company 코드를 Feign으로 전달받고 `SERVICE_UNAVAILABLE`은 공통 코드다. `script/error-codes-check.sh` 통과(2026-10-19).
- [x] 구현(2026-10-19): V4 마이그레이션, `HubStock`·`HubStockHistory`, 저장소, `StockService`·`StockRegistrationService`·`StockQueryService`, `ProductClient`(company I-08), `HubStockController`(H-05, H-06), `InternalStockController`(I-04~I-06). 도메인 6, 서비스 단위 23, MVC 16, PostgreSQL 통합 9, 동시성 통합 4개를 추가했고 `:hub-service:test` 전체가 통과했다.
- [x] 동시성 실측: 재고 10개에 20건 동시 할당 시 정확히 10건 성공, 같은 주문 동시 10건은 1회 차감, 20건 동시 입고 합계 100, 같은 주문 동시 복원 10건은 1회 복원. 락을 일부러 뺐을 때 실패하는지는 확인하지 않았다(2026-10-20에 확인함, 아래 "남은 검증 수행").
- [x] 문서 갱신: `docs/services/hub/README.md` 제공 API·호출·구현 현황, `notes.md`, ADR 0003, company·order 협업 로그 요청(COMPANY-002, ORDER-002, OPEN). `script/docs-check.sh` 통과.
- [x] 실제 기동·호출 확인(2026-10-19): `./gradlew build` 성공(BUILD SUCCESSFUL, 테스트 XML 집계 824개 중 실패·오류·skip 0). `script/run.sh hub-service company-service user-service gateway-service`로 기동했고 Flyway가 V4를 적용했다(스키마 v3→v4).
  - Gateway(8080) 경유 H-06: MASTER 입고 200(재고 행 없음 → company I-08 호출 성공, 50→55), 없는 상품 404 `PRODUCT_NOT_FOUND`, 수량 0 400, 없는 허브 404 `HUB_NOT_FOUND`, USER 403 `FORBIDDEN`. H-05: `productId` 필터·`quantity` 정렬 200, 토큰 없음 401, USER 조회 200. `/internal/**`은 Gateway에서 404.
  - 컨테이너 안(user-service에서 `hub-service:8082` 호출) I-04~I-06: I-04 멱등, I-05 40 할당 후 같은 주문 재요청은 같은 결과, 부족·재고 행 없음 409 `STOCK_INSUFFICIENT`, I-06 복원 40 후 재복원 0, 이력 없는 주문 0.
  - company를 내린 상태에서 재고 행이 없는 상품 입고는 503 `SERVICE_UNAVAILABLE`, 재고 행이 있는 상품 입고는 company 호출 없이 200.
  - **결함 확인**: I-05로 할당하고 I-06으로 복원한 주문을 같은 `orderId`로 다시 I-05하면 200과 `quantity 40`을 반환하지만 재고는 차감되지 않는다(재할당 뒤 재고 55, 기대 15). DECREASE 이력이 남아 있어 멱등 분기가 기존 결과를 돌려주기 때문이다. → 2026-10-20 A안으로 수정(사용자 결정): 복원까지 끝난 주문의 재할당은 409 `STOCK_ALREADY_RESTORED`로 거부하고 유니크 키는 유지한다. 단위 테스트 `allocateAfterRestore` 추가, `StockServiceTest`·동시성·MVC 테스트 통과, `error-codes-check.sh` 통과. 실제 기동 재확인은 하지 않았다. order가 취소 뒤 같은 `orderId`로 재할당하는 흐름이 있는지는 ORDER-002에서 확인해야 한다(있으면 B안 재검토).
  - 하지 않은 것: 프런트 검증(`npm --prefix frontend ...`, 프런트가 쓰는 API 변경은 없다), 실제 order·company의 I-04~I-06 호출(두 서비스에 호출 코드가 없다), 락을 뺐을 때 동시성 테스트가 실패하는지, 서비스 간 호출 중 delivery·order 컨테이너 사용. 확인용 데이터(업체 `stockco`, 상품 `stockprod`, 사용자 `stockusr`, 부산 허브 재고)가 로컬 DB에 남아 있다.
- [x] 성능 검토 반영(2026-10-20, 사용자 승인): ① 재고 행 락 대기 제한. `config-repo/hub-service.yml`의 `hikari.connection-init-sql: SET lock_timeout = '3s'`로 DB가 3초 뒤 락 대기를 끊고, `StockService`가 `PessimisticLockingFailureException`을 503 `SERVICE_UNAVAILABLE`로 바꾼다. 실제 PostgreSQL 통합 테스트 `HubStockLockTimeoutIntegrationTest`로 확인했다. ② 멱등 조회를 `(hubStockId, orderId, changeType)` 단건·exists로 바꿔 유니크 인덱스를 쓰고 복원 반복문 안의 목록 재조회를 없앴다. ③ 할당의 허브 조회를 재고 행이 없을 때로 미뤄 정상 경로의 쿼리를 1개 줄였다. `:hub-service:test` 전체 통과. 하지 않은 것: `EXPLAIN` 실측, 부하 테스트, 실제 기동 재확인. 기록만 하고 미룬 것: 이력 PK(UUID v4) 삽입 분산, I-05 배치 API.
  - [x] 추가 반영(2026-10-20, 사용자 승인): ④ company Feign 타임아웃을 `config-repo/hub-service.yml`의 `spring.cloud.openfeign.client.config.company-service`에서 연결 1초·읽기 2초로 줄였다(공통 20초 × 재시도 3회면 상품 존재 확인 하나가 최악 약 60초 스레드를 잡는다). ⑤ `StockRegistrationService.retryOnceOnDuplicate`가 유니크 위반(SQLState `23505`)일 때만 재시도하도록 좁혔다. CHECK·FK 위반은 다시 해도 같은 결과라 바로 던진다. 단위 테스트 `inboundDoesNotRetryOnOtherIntegrityViolation` 추가, `:hub-service:test` 전체 통과(통합 테스트 포함). 하지 않은 것: Feign 타임아웃이 실제로 적용되는지 기동 확인, 부하 테스트. 근거와 보류한 개선(원자적 UPDATE, 허브 캐시, 검색 인덱스, 이력 보관 정책)은 `docs/services/hub/notes.md`에 있다.
  - [x] 코드 검토 반영(2026-10-20, 사용자 승인): ⑥ `HubStock.increase`가 `Math.addExact`로 int 오버플로를 400 `INVALID_REQUEST`로 막는다. ⑦ `StockRegistrationService`가 재고 생성 중 락 대기 초과(`PessimisticLockingFailureException`)를 503으로 바꾼다. ⑧ 복원에서 재고 행이 없으면 경고 로그를 남기고, I-05 멱등 재요청은 수량이 달라도 처음 결과를 돌려준다는 전제를 주석에 적었다. 불필요한 `Note:`를 줄이고 `missingStock`을 `missingStockException`으로 바꿨다. `:hub-service:test` 전체 통과. 허브 삭제 정책은 개발 범위가 아니라 다루지 않는다. 하지 않은 것: Flyway의 `lock_timeout` 분리(같은 Hikari 풀을 써서 `init-sqls`가 풀 연결에 남으므로 되돌렸고 주석으로만 남겼다).
  - [x] Feign 타임아웃 적용 확인(2026-10-20): `ProductClientTimeoutIntegrationTest`가 운영 설정(`config-repo/application.yml`, `hub-service.yml`)을 불러와 응답하지 않는 서버를 `ProductClient`로 호출한다. 요청 3회(재시도), 총 약 6.9초에 실패해 `company-service` 읽기 2초가 공통 20초를 덮어쓴다. 실제 서비스 기동은 하지 않았다.
  - [x] 남은 검증 수행(2026-10-20):
    - 락 제거 실험: `HubStockJpaRepository`의 `@Lock`을 임시로 주석 처리하자 `HubStockConcurrencyIntegrationTest` 4개가 모두 실패했다(갱신 손실·중복 차감·중복 복원). 락을 복원했고 테스트는 다시 통과한다.
    - 실제 기동(`script/run.sh config-server hub-service company-service user-service gateway-service`): 처음에는 config-server 이미지가 낡아 `company-service` 타임아웃이 적용되지 않았고 company를 멈춘 입고가 40.8초 걸렸다. config-server를 다시 빌드한 뒤 같은 호출이 6.6초에 503 `SERVICE_UNAVAILABLE`로 끝난다(config-server `/hub-service/default`에 읽기 2000이 보인다).
    - 실제 호출: 재고 `Integer.MAX_VALUE`에 1을 더하면 400 `INVALID_REQUEST`, 같은 주문 재할당은 처음 결과 반환(수량을 다르게 보내도 처음 수량), 복원 100 뒤 재복원 0, 복원 뒤 재할당 409 `STOCK_ALREADY_RESTORED`.
    - `EXPLAIN (ANALYZE)`(허브 한 곳에 재고 20만 행, 이력 40만 행 임시 데이터, 확인 뒤 삭제): 멱등·복원 이력 조회와 productId 검색은 인덱스를 써 0.1ms 안쪽이다. H-05 목록(productId 없음)과 count는 허브 재고 전체를 순차 스캔해 12~20ms다. 허브당 재고가 수만 건 이하인 현재 규모에서는 문제없어 인덱스를 추가하지 않았다. 늘어나면 `(hub_id, created_at DESC)` 인덱스를 검토한다.
    - 부하: 한 재고 행(수량 200)에 서로 다른 주문 300건을 동시 50개로 할당하자 200건 200, 100건 409, 잔량 0, 503 없음, 지연 p50 0.30초·p95 0.34초·최대 0.55초(curl 기동 시간 포함)였다. 락 대기 3초 초과 상황은 이 부하에서 만들지 못했고 `HubStockLockTimeoutIntegrationTest`가 따로 확인한다.
    - 하지 않은 것: 여러 허브·여러 상품이 섞인 혼합 부하, 더 높은 동시성, order·company의 실제 I-04~I-06 호출(두 서비스에 호출 코드가 없다). 로컬 DB에 부산 허브 재고 확인용 행이 남아 있다.
  - [x] H-06 입고 권한 확장(2026-10-20, 사용자 결정): MASTER에 더해 상품 업체의 관리자(`Company.managerId`)도 입고한다. 근거는 [ADR 0003](../services/hub/decisions/0003-stock-allocation-supplier-hub-only.md)의 입고 권한 절(제안, SA와 다름)이다.
    - 구현: 컨트롤러의 `@RequireRole`을 없애고 `StockRegistrationService.inbound(user, command)`가 판단한다. 업체 관리자는 company I-08(`companyId`)·I-07(`managerId`, `hubId`)을 호출해 본인이고 업체 소속 허브일 때만 허용하며 아니면 403 `FORBIDDEN`이다. MASTER는 허브 제한이 없다. company 코드는 바꾸지 않았다.
    - 결정: company 호출 2회(I-08, I-07)는 줄이지 않는다(사용자 결정). I-08에 `managerId`·`hubId`를 넣는 안은 상품 API의 책임을 흐려 채택하지 않았고 COMPANY-003 요청은 쓰지 않았다.
    - 테스트: `CompanyServiceClientIntegrationTest`(company I-07·I-08 응답 필드, 404 코드 전달, 5xx는 503)와 I-07 타임아웃 테스트, 서비스 단위 테스트(업체 관리자 허용·거절, 재고 행이 있어도 company 확인, company 장애)를 더했다. `:hub-service:test` 138개 통과.
    - 생성 경합: `HubStockCreationRaceIntegrationTest`로 재고 행이 없을 때 동시 입고·초기화 10건이 모두 성공하고 행이 하나임을 확인했다. 재시도를 일부러 껐을 때 두 테스트가 실패함을 확인하고 원복했다. `StockService.restore`의 null 분기와 `Note:` 주석 일부를 정리했다. `:hub-service:test` 140개 통과.
    - 실제 기동 확인(2026-10-20): `script/run.sh hub-service`로 hub 이미지만 다시 빌드해 띄우고 Gateway(8080)로 호출했다. 확인용 사용자 USER 둘(`mgrusr20`, `othusr20`), 업체 `mgrco20`(관리자 `mgrusr20`, 허브 A), 상품 `mgrprod20`을 만들었다.
      - 업체 관리자가 자기 허브에 입고 200(재고 행 없음, 7), 같은 입고 한 번 더 200(재고 행 있음, 합계 10), 다른 허브 403 `FORBIDDEN`. 관리자가 아닌 USER 403, MASTER의 다른 허브 입고 200(5), 없는 상품 404 `PRODUCT_NOT_FOUND`, 없는 허브 404 `HUB_NOT_FOUND`, 토큰 없음 401.
      - C-04로 관리자를 `othusr20`으로 바꾸자 이전 관리자 입고 403, 새 관리자 입고 200이 바로 반영됐다.
      - company-service를 멈춘 상태에서 업체 관리자 입고(재고 행 있음) 503 `SERVICE_UNAVAILABLE`(3초), MASTER 입고(재고 행 있음)는 company 호출 없이 200(1초). 응답하지 않는 서버의 타임아웃(읽기 2초, 재시도 3회)은 이번에 기동으로 재현하지 않았고 통합 테스트로만 확인했다.
      - 확인용 데이터(사용자 2명, 업체, 상품, 허브 A·B 재고)가 로컬 DB에 남아 있다.
    - 전체 빌드(2026-10-20): `./gradlew build --rerun-tasks` 성공(4분 10초). 모듈별 테스트 합계 844개(common-core 2, common 50, company 46, config-server 1, delivery 39, gateway 27, hub 140, order 451, user 88), skipped·실패·오류 0. 이전 기록의 824개는 당시 시점 값이고 이후 hub 테스트가 늘었다. 프런트 검증은 프런트 변경이 없어 하지 않았다.
    - 응답 필드 검증: `CompanyResponse`의 JSON 이름을 일부러 틀리게 바꾸자 `CompanyServiceClientIntegrationTest`의 I-07 응답 테스트가 실패했고, 원복한 뒤 통과한다.
    - 하지 않은 것: 응답하지 않는 company의 타임아웃을 기동으로 재현하는 것, 부하 테스트.
- 면접 포인트: 한 행만 잠그면 왜 교착이 없고, 여러 허브 행을 잠그게 되면 교착을 어떻게 막는가(hub_id 순 잠금)? 왜 허브 간 재고 이동을 재고 할당이 아니라 배송으로 처리해야 하는가? 같은 주문이 두 번 오면 어떻게 한 번만 차감하는가(멱등 유니크)? 비관적 락과 조건부 UPDATE는 언제 각각 맞는가?

## 다음 단계
1. (완료) 호출 방식·할당 범위 결정, V4·domain·infrastructure·application·presentation 구현, 문서와 협업 로그 요청.
2. (완료) 실제 기동과 호출 확인. 결과는 위 진행 상태에 있다. 복원 뒤 재할당 결함은 A안(409 거부)으로 처리했다.
3. company·order 담당자 답변(COMPANY-002, ORDER-002)을 받아 `DECIDED`가 되면 README의 호출 현황을 갱신한다. 프런트가 쓰는 API 변경은 없지만 전체 빌드를 요청받으면 `docs/guides/full-build.md`의 프런트 검증도 한다.
4. 허브 간 이동 연동(ADR 0003의 한계)은 팀이 정하면 다시 본다.

## 직접 해보기
1. 현재 상태를 확인한다.
   - CLI: `git branch --show-current`, `git status --short`.
   - 확인: 브랜치는 `feat/hub-adv`이고 `hub-service`에 허브·경로 조회 API, 재고 API(H-05·H-06, I-04~I-06)와 V1~V4 마이그레이션이 있다.
2. 기존 구현과 고도화 설계를 대조한다.
   - CLI: `docs/services/hub/README.md`의 제공 API 표와 SA 5.3, 7.4를 나란히 읽는다.
   - 확인: 구현된 API(H-01~H-04, I-02, I-03)와 고도화 API(H-05·H-06, I-04~I-06)를 구분하고, 재고 테이블 제약과 멱등키를 설명할 수 있다.
3. 기준선 테스트를 실행한다.
   - CLI: Docker Desktop을 켠 뒤 `.\gradlew.bat :hub-service:test`.
   - 확인: 테스트가 모두 통과한다(2026-10-20 기준 hub 140개, 전체 844개). 이 결과가 이후 회귀 비교 기준이다.

## 메모
- cherry-pick 하지 않기로 했다(2026-10-19, 사용자 결정). `eb888c3`(협업 로그 HUB-001 두 건 답변), `8aba0b1`(검색 경계·DB 제약 테스트), `3b4d89a`(협업 창구 구현 현황·노트)는 `feat/hub-query`와 `backup/feat-hub-query-1016`에만 남는다. 이 브랜치의 `docs/services/hub/README.md`에는 HUB-001 두 건이 OPEN으로 남아 있고 `notes.md`가 없다. 필요하면 그때 해당 문서만 직접 갱신한다.
- `feat-hub-query.md`는 완료한 일이 `[ ]`로 남아 낡았으므로 상태 판단은 `docs/services/hub/README.md`와 코드 기준으로 한다.
- 미결정 항목(`feat-hub-query.md` 메모에서 이어짐): 거리 반올림 정책, I-03에 같은 허브 ID를 보냈을 때의 오류, 허브 UUID 시드 합의, 문자열 정렬 콜레이션.
- SA는 재고를 기본 범위에서 무한으로 가정한다(결정 9). 고도화로 재고를 켜면 order·company 쪽 연동(주문 시 I-05·I-06, 상품 생성 시 I-04)도 바뀌므로 해당 서비스 담당자와 일정을 맞춰야 한다.
- 관련 결정: `docs/decisions/0010-internal-api-unwrapped-response.md`, `0011-service-boundaries.md`, `0012-schema-per-service-uuid-reference.md`, `0013-sync-openfeign-communication.md`.
- 인계 유지 규칙: 이 문서의 다섯 섹션 제목을 유지한다. 완료 체크에는 검증 근거를 붙이고, 완료한 작업의 상세는 지우지 않고 누적한다.
