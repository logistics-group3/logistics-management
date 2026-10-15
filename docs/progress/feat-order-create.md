# feat/order-create

## 지금 하는 일
O-02·O-03을 9a41925로 커밋하고 O-04 요청사항 수정을 구현했다. 주문 191개 테스트·전체 빌드·Gateway 검증·로컬 반영을 마쳤으며 O-04는 미커밋이다.

## 진행 상태
- [x] `git fetch origin`으로 원격 `dev`를 확인했다.
- [x] `git switch --no-track -c feat/order-create origin/dev`로 브랜치를 만들고 전환했다. 생성 시 HEAD와 origin/dev는 모두 `f700c0ca8359fdd34708de8e51285d9269c6a662`다.
- [x] 사용자 발언과 Git 작성자 정보를 근거로 주문 담당자를 Junhyun An으로 기록했다.
- [x] SA 5.5·6.7·7장과 주문 모듈 지침, 서비스 결정 문서의 상태를 확인했다.
- [x] 기존 README 변경(마지막 줄바꿈 추가)을 이름 붙인 stash에 보관했다. `.env`와 `.idea/` 파일 7개의 전환 전후 SHA-256이 같다.
- [x] `script/docs-check.sh`, `git diff --check`와 변경 문서 4개의 로컬 링크·공백 검사를 통과했다. `git rev-list --left-right --count origin/dev...HEAD` 결과는 `0 0`이다.
- [x] 주문 모델·Flyway 마이그레이션·Repository 구현: `Order`, `OrderStatus`, `OrderRepository`, `OrderJpaRepository`, `V1__create_p_orders.sql`
- [x] 주문 테스트 42개 통과: 단위 24개, PostgreSQL 16 통합 18개. 첫 실행의 타입 누락 실패를 확인한 뒤 구현했다.
- [x] `./gradlew build --console=plain` 성공. 전체 테스트 XML 합계 140개, 실패·오류·skip 0. 주문 테스트는 직전 결과를 재사용했다.
- [x] 기반 구현 후 `script/docs-check.sh`, `script/error-codes-check.sh`, `git diff --check`와 변경 파일 14개의 공백·문서 링크 검사를 통과했다.
- [x] 사용자 요청으로 `unitPrice`·`totalPrice`를 BigDecimal·NUMERIC(15,2)로 전환했다. 주문 ADR 0004에 SA 차이와 소수 자릿수·범위 기준을 기록했다.
- [x] 금액 전환 후 주문 테스트 57개 통과(단위 33개, PostgreSQL 통합 24개). 앞선 42개·전체 140개는 초기 기반 구현 당시 기록이다.
- [x] 금액 전환 후 전체 `./gradlew build --console=plain` 성공. 기존 성공 결과를 재사용했고 전체 XML 합계 155개, 실패·오류·skip 0이다. 문서·에러 코드·공백·로컬 링크 검사도 통과했다.
- [x] 사용자 원복 요청으로 Entity·V1·테스트를 long·BIGINT로 복원했다. 현재 기준은 ADR 0005이며 앞선 BigDecimal 전환은 대체된 이력이다.
- [x] 원복 후 `./gradlew build --console=plain` 성공. 주문 테스트 42개(단위 24개, PostgreSQL 통합 18개)를 다시 실행했고 전체 XML 합계 140개, 실패·오류·skip 0이다. 다른 모듈 테스트는 이전 성공 결과를 재사용했다. 문서·에러 코드·공백·로컬 링크 검사도 통과했다.
- [x] 사용자 진행 승인에 따라 기반을 `216a768 feat(order): 주문 엔티티와 저장소 기반 구현`으로 커밋했다. 커밋 직후 작업 트리는 깨끗했다.
- [x] O-01 application 구현: 입력·결과 record, CompanyReader·DeliveryGateway, OrderService·OrderWriter
- [x] application 단위 테스트 23개와 실제 PostgreSQL 생성 트랜잭션 테스트 4개 통과. 커밋 시점 오류의 롤백·배송 보상도 확인했다.
- [x] 주문 모듈 테스트 69개, 전체 `./gradlew build --console=plain` 성공. 전체 XML 합계 167개, 실패·오류·skip 0이며 전체 빌드에서는 기존 성공 결과를 재사용했다.
- [x] application 구현 후 문서·에러 코드 검사, `git diff --check`, 변경 파일 20개의 공백·문서 링크 검사를 통과했다.
- [x] 사용자 요청으로 application 변경을 `3b5a39c feat(order): 주문 생성 흐름과 실패 보상 구현`으로 커밋했다. 커밋 직후 작업 트리는 깨끗했다.
- [x] 실제 Feign 어댑터·OrderService 빈 등록·O-01 Controller 구현. 생성/삭제 Feign contextId와 재시도 책임을 분리했다.
- [x] Controller 11개·HTTP/Feign 13개 추가. 전체 빌드 성공, 주문 93개·전체 XML 합계 191개, 실패·오류·skip 0.
- [x] 현재 Compose 주문 기동·Flyway V1 적용·Gateway OpenAPI 200/O-01 노출·미인증 401 확인.
- [x] 격리 Gateway·JWT·Order·실제 Feign·PostgreSQL로 401·201·400·503, 위조 헤더 제거, Audit·보상·미저장 확인. 외부 user·company·delivery 응답은 대역이며 임시 프로세스와 DB를 정리했다.
- [x] Feign·Controller 변경 후 문서·에러 코드·공백 검사와 변경 파일 26개의 로컬 링크·공백 검사를 통과했다.
- [x] 사용자 요청으로 Feign·Controller 변경 26개 파일을 `9b48adc feat(order): 주문 생성 Feign과 Controller 연결`로 커밋했다. 직후 작업 트리는 깨끗했다.
- [x] 루트가 Git 저장소임을 확인했다. README·build.gradle·settings.gradle·AGENTS.md 추적 중이며 .env·IDE 개인 파일·build 산출물은 무시 대상이다.
- [x] OrderCreatedEvent·AFTER_COMMIT 리스너·@Async Slack 전송·전용 풀·설정 구현. URL 미설정 시 발송을 생략한다.
- [x] 주문 테스트 108개 통과. 커밋 전·실패 시 알림 없음, 별도 DB 연결에서 커밋 확인, Slack 응답 전 201, 오류·타임아웃·큐 포화 때 주문 유지·배송 보상 없음 확인.
- [x] 전체 `./gradlew build --console=plain` 성공. XML 합계 206개, 실패·오류·skip 0. 주문은 직전 결과를 재사용하고 config-server 테스트는 다시 실행했다.
- [x] Slack 변경 후 문서·에러 코드·공백 검사와 변경 파일 26개의 로컬 링크·공백 검사를 통과했다.
- [x] 사용자 요청으로 Slack 구현 26개 파일을 `7a3f93f feat(order): 커밋 후 비동기 Slack 알림 구현`으로 커밋했다. 직후 작업 트리는 깨끗했다.
- [x] `.env`의 Git 무시 여부와 Webhook 미설정 상태를 비밀값 출력 없이 확인했다.
- [x] `docker compose build config-server order-service` 성공. 실행 중인 컨테이너 교체·재시작은 하지 않았다.
- [x] 사용자의 후속 요청으로 Chrome의 앱 관리 화면을 확인했다. 사용자가 선택한 2026 내일배움캠프에서 설치 권한 없음 안내와 앱 설치 한도 초과 안내가 표시됐다. 최종 화면은 한도 초과였다.
- [x] 사용자가 `디스이스스파르타` 워크스페이스를 만들었다고 알렸다. Chrome 앱 생성 양식에서 해당 이름을 선택했고, 스크린샷에서도 기존 권한·설치 한도 경고가 없는 상태를 확인했다.
- [x] 사용자가 앱 생성·`.env`의 Webhook 설정 완료를 알렸다. Compose가 해석한 값의 존재·HTTPS Slack URL 형식·Git 무시 여부를 값 노출 없이 확인했다.
- [x] 사용자 질문에 따라 빈 스코프 목록을 확인했다. 상단 검색으로 incoming-webhook을 찾아 생성 양식에 선택했고 Bot scopes (1)·User scopes (0)을 확인했다. 아직 실제 설치 권한 부여 단계는 아니다.
- 설정 전 제안은 채널 `#logistics-order-dev`·앱 `Logistics Order Dev`였다. 실제 연결은 사용자가 `.env`에 등록한 Webhook을 사용하며, 실제 채널·앱 이름은 별도로 읽어 확인하지 않았다. 팀원 초대는 하지 않았다.
- [x] config-server → order-service를 `up -d --no-deps --no-build --wait --wait-timeout 90`으로 반영했다. 준비한 이미지 ID와 실제 컨테이너 이미지가 일치한다.
- [x] 주문 컨테이너의 URL이 Compose 값과 일치하고 Config Server가 Slack 설정(연결 1초·읽기 2초)을 제공한다. 두 서비스 health UP, Gateway 주문 OpenAPI 200·O-01 존재, 미인증 401 확인.
- [x] 교체된 서비스는 config-server·order-service뿐이다. 나머지 7개 컨테이너 ID·시작 시각과 PostgreSQL 볼륨 연결, `.env` 해시가 유지됐다. 주문 기동 로그 최근 250줄에 Webhook 실제 값이 없다.
- [x] 사용자가 명시적으로 승인한 연동 테스트 메시지 1건을 주문 컨테이너에서 전송했다. HTTP 200·본문 ok·curl 종료 코드 0이며 재시도하지 않았다. 주문은 만들지 않았다.
- [x] 후속 문서 8개의 링크·공백과 문서·에러 코드 검사 통과. 실제 Webhook 값이 변경 파일에 없으며 소스 코드·설정 파일의 추가 변경은 없다.
- [ ] 팀 서비스 실제 데이터로 주문 생성·보상·생성 이벤트의 실제 Slack 알림 전체 연동 검증
- [x] 사용자 요청으로 Slack 설정·실행 기록 8개 파일을 `af0ba34 docs(order): Slack 실제 연동과 설정 검증 기록`으로 커밋했다. 직후 작업 트리는 깨끗했다.
- [x] O-02·O-03 구현: OrderQueryService, 검색 Request·Condition·Criteria, QueryDSL 저장소, ORDER_NOT_FOUND, Controller 연결
- [x] 신규 application 10개·MVC 12개·HTTP/PostgreSQL 22개 추가. 최종 전체 build 성공, 주문 152개·전체 XML 250개, 실패·오류·skip 0. 다른 모듈은 기존 성공 결과를 재사용했다.
- [x] 실제 Gateway·JWT·Order·별도 PostgreSQL에서 200·400·401·403·404 확인. 위조 헤더·타인 주문 파라미터에도 본인 범위·count가 유지되며 회사·배송 호출은 0이다. 임시 프로세스·DB 컨테이너를 정리했다.
- [x] Swagger 기본 페이지 크기 불일치(TS-010)와 공유 테스트 DB의 fixture 간섭(TS-011)을 수정하고 회귀 테스트를 통과했다.
- [x] 현재 Compose에 주문 이미지만 교체했다. health UP, Gateway O-02·O-03 문서 200·size=10·기본 정렬·미인증 401 확인. 나머지 8개 컨테이너 ID·시작 시각과 .env 해시·Webhook 환경 변수는 유지됐다.
- [x] 문서·에러 코드·공백 검사와 조회 작업 파일의 로컬 링크 검사 통과. 에러 코드 16개에 중복·누락이 없고 실제 Webhook 값이 변경 파일에 없다.
- [x] 사용자 요청으로 조회 구현 20개 파일을 `9a41925 feat(order): 주문 검색과 단건 조회 구현`으로 커밋했다. 별도 학습 노트 3개 변경은 제외했다.
- [x] O-04: 도메인 변경 메서드, 잠금 조회, 쓰기 트랜잭션, PATCH 검증, 소유자·MASTER 권한과 취소 상태 409 구현
- [x] 신규 도메인 7개·application 7개·MVC 11개·HTTP/PostgreSQL 14개 통과. 최종 전체 build 성공, 주문 191개·전체 XML 289개, 실패·오류·skip 0
- [x] 실제 PostgreSQL 잠금 대기 후 취소 409·삭제 404, 요청사항 보존, Audit 응답·저장 일치, flush 후 롤백을 확인했다.
- [x] 격리 Gateway·JWT·Order·PostgreSQL에서 수정 200·400·401·403·404·409와 위조 헤더 거부를 확인했다. 회사·배송 호출과 Slack 전송은 0이며 임시 프로세스·DB를 정리했다.
- [x] `docker compose build order-service` 후 `up -d --no-deps --no-build --wait --wait-timeout 90 order-service`로 주문만 반영했다. 준비한 이미지 일치·health UP·Gateway OpenAPI 200/O-04·필수 requestNote/최대 500·미인증 PATCH 401 확인
- [x] 나머지 8개 컨테이너 ID·시작 시각, PostgreSQL 볼륨 연결, .env 해시·Webhook 환경 변수가 유지됐다. 변경 파일과 최근 주문 로그에 실제 Webhook 값이 없다.
- [x] 문서·에러 코드 16개·공백·로컬 링크 검사를 통과했다. 주문 과거 노트 3개 분리 후 링크 경로를 제외한 본문이 원본과 같음을 확인했다.
- 면접 포인트: 변경 감지에 save가 필요 없는 이유, flush와 커밋의 차이, 상태 확인과 변경 사이의 경쟁을 잠금으로 막는 이유

## 다음 단계
1. [구현 노트](../services/order/notes.md)와 [스텝 42](../learning/junhyun/steps/step-42-order-request-note-update.md)을 읽는다. 조회 API는 9a41925에 커밋됐고 O-04 변경은 미커밋이다.
2. Slack URL은 이미 로컬 `.env`와 실행 중인 order-service에 반영됐다. URL을 출력·커밋하지 않는다. 승인받은 테스트 1건은 전송 완료했으므로 같은 검증을 자동으로 반복하지 않는다.
3. 팀 company I-07·I-08, delivery I-09·I-13의 실제 구현·데이터가 준비되면 Gateway 정상 주문과 실패 보상, 실제 생성 이벤트의 Slack 알림을 통합 검증한다. 이번에는 계정·주문 데이터를 새로 만들지 않았다.
4. I-13 재시도는 OrderService에서 총 3회, Feign은 NEVER_RETRY다. 클라이언트를 합치면서 재시도가 중첩되지 않게 유지한다.
5. 기존 의존 서비스가 정상인 경우 주문만 재기동하려면 빌드 후 `docker compose up -d --no-deps --no-build order-service`로 범위를 한정한다. 현재 공동 run.sh는 의존 서비스도 조정한다.
6. 후속 기능은 O-05 취소, O-06 삭제다. SA 6.7·7.2의 권한·상태 전이·보상 계약을 확인한다. 로컬 상태 변경에는 findByIdForUpdate 잠금을 사용하고 외부 호출 동안 DB 잠금을 길게 유지하지 않도록 트랜잭션 경계를 설계한다.

## 직접 해보기
1. 저장소 터미널에서 `git branch --show-current`를 실행한다. `feat/order-create`가 나오면 작업 브랜치에 있다. IntelliJ에서는 Git 브랜치 위젯에서 같은 이름을 확인한다.
2. `git log -1 --oneline`으로 조회 커밋 `9a41925`를 확인한다. Slack 기록은 `af0ba34`, Slack 코드는 `7a3f93f`, Feign·Controller는 `9b48adc`, application은 `3b5a39c`, 기반은 `216a768`이다.
3. IntelliJ에서 `docs/SA/sparta-logistics-sa.md` 5.5를 열고 주문 테이블 필드와 상태 `ORDERED`, `CANCELED`를 확인한다.
4. `./gradlew :order-service:test --tests '*OrderTest'`로 도메인 규칙 24개를 확인한다. IntelliJ에서는 `OrderTest` 클래스 옆 실행 버튼으로 실행한다.
5. `./gradlew :order-service:test --tests 'com.sparta.logistics.order.application.*'`로 생성·조회·수정 application 단위 테스트 40개를 확인한다.
6. `./gradlew :order-service:test --tests '*OrderControllerWebMvcTest'`로 Controller 11개를 확인한다.
7. `./gradlew :order-service:test --tests '*OrderNotificationIntegrationTest'`로 Slack 대역과 실제 비동기 연결 6개를 확인한다. IntelliJ에서는 클래스 옆 실행 버튼을 사용한다.
8. Docker 실행 상태에서 `./gradlew :order-service:test`로 HTTP·DB 통합 테스트를 포함한 191개를 확인한다. O-04 관련 39개만 보려면 `--tests '*OrderRequestNoteTest' --tests '*OrderUpdate*'`를 추가한다. IntelliJ에서는 해당 테스트 클래스의 실행 버튼을 사용하고 결과는 `order-service/build/reports/tests/test/index.html`에서 본다.
9. `docker compose exec -T order-service curl -fsS http://localhost:8084/actuator/health`에서 `UP`을 확인한다. 실제 Webhook URL을 출력하는 명령은 쓰지 않는다.

## 메모
- 작업일: 2026-10-15 (Asia/Seoul). 브랜치 생성·기능 추천 이후 사용자가 주문 기반(Entity·마이그레이션·Repository·테스트) 구현과 설명을 요청했다.
- 팀 브랜치 규칙은 `docs/guides/git-workflow.md`의 `feat/{모듈}-{작업}`이며 통합 대상은 `dev`다. 기반 커밋은 `216a768`이며 푸시는 수행하지 않았다.
- 원격 `dev` 기준 company·delivery 업무 API는 아직 골격과 예정 상태다. 팀원 개인 브랜치의 진행 상황은 확인하지 않았다.
- 서비스 ADR 0001·0002는 `제안` 상태이며 재고 처리와 취소 조건에 SA v2.0과 다른 내용이 있다. 현재 기준은 재고 확인·차감 없음, 집하 시작 전(`PICKUP_WAITING`) 취소다.
- 보관한 stash: `pre-order-branch-2026-10-15: preserve README newline`, 생성 시 객체 `8b7392d5755626822b727ee5cbb5cb0a74752320`. 새 README에는 이미 줄바꿈이 있다. 기존 README 전체를 새 브랜치에 덮어쓰지 않는다.
- 기반·application·Feign·Controller·Slack은 구현했다. 격리 Gateway·비동기·HTTP 대역 검증과 실제 Webhook 테스트 1건의 200·ok 확인까지 마쳤다. 팀 서비스 실제 데이터로 주문 전체 흐름을 검증하는 단계는 남아 있다.
- 기반은 `216a768`, application은 `3b5a39c`, Feign·Controller는 `9b48adc`, Slack은 `7a3f93f`에 커밋했다. 푸시는 수행하지 않았다.
- Slack 연동 기록은 `af0ba34`, O-02·O-03 코드·테스트·기록은 `9a41925`에 커밋했다. O-04 변경은 미커밋이며 DB 스키마·공통 코드·다른 서비스 코드는 바꾸지 않았다.
- 조회 작업 중 나타난 `docs/learning/junhyun/README.md`와 qna의 common-core-split-easy·downstream-error-propagation 학습 노트 변경은 이 작업에서 작성한 것이 아니므로 그대로 보존했다.
- O-04는 상태 검증이 있는 짧은 로컬 쓰기 트랜잭션이다. 응답 Audit를 위해 flush하지만 생성용 OrderWriter·이벤트를 거치지 않는다. 격리 Gateway 검증의 user 인증 응답은 HTTP 대역이며 실제 Slack 메시지는 추가로 보내지 않았다.
- 코드 구현 단계에서는 기존 Compose를 교체하지 않았고, 실제 연동 단계에서 config-server·order-service만 교체했다. `.env`는 사용자가 설정했으며 에이전트 작업 전후 해시는 같다. 알림은 유실 가능한 부가 기능이며 실패·큐 포화 로그의 주문 ID로 추적한다. 상세 기준은 주문 ADR 0003이다.
- BigDecimal 전환 후 사용자가 정수 원화 범위를 확인하고 원복을 요청했다. 현재 기준은 [주문 ADR 0005](../services/order/decisions/0005-order-money-integer-krw.md)의 long·BIGINT이며 SA 5.5·6.7과 일치한다. 팀 공통 SA와 다른 서비스 코드는 수정하지 않았다.
- 금액 원복 당시에는 V1이 미커밋 초안이었다. 현재 V1은 `216a768`에 커밋됐으므로 이후 스키마 변경은 새 마이그레이션으로 한다.
- 로컬 기동 과정에서 run.sh가 PostgreSQL도 재생성했다. 기존 pgdata 볼륨 재사용·초기화 생략, .env 해시 유지, 나머지 7개 컨테이너 ID·시작 시각 유지와 관련 서비스 health UP을 확인했다(TS-008). 정확한 재생성 판정 원인은 미확인이다.
