# feat/order-create

## 지금 하는 일
사용자 요청으로 feat/order-create 푸시와 dev 대상 PR을 준비한다. 최신 dev 1384a5d와의 공유 문서 충돌을 격리 worktree에서 해결하고 합친 상태의 빌드·연동 계약을 확인한다.

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
- [x] Swagger 기본 페이지 크기 불일치(TS-011)와 공유 테스트 DB의 fixture 간섭(TS-012)을 수정하고 회귀 테스트를 통과했다.
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
- [x] 사용자 요청으로 O-04 코드·테스트·문서 22개 파일을 `6ac9b8a feat(order): 주문 요청사항 수정 API 구현`으로 커밋했다. 별도 Q&A 노트 3개 변경은 제외했다.
- [x] ADR 0006으로 취소 진행 기록 추가를 제안하고 사용자의 "진행 기록 추가 적용" 승인을 받았다. p_order_cancellations·V2·부분 유니크·복구 사유를 추가하고 SA 주문 영역을 갱신했다.
- [x] O-05: 주문자·MASTER 권한, I-11 → 주문 CANCELED/시도 COMPLETED 커밋, 결과 재확인·I-12 보상, 미확정 결과 복구 대기·중복 실행 차단
- [x] 신규 도메인 12개·application 14개·PostgreSQL 17개·HTTP/Feign 18개, 총 61개 추가. 전체 build 성공, 주문 252개·전체 XML 350개, 실패·오류·skip 0
- [x] 실제 DB flush·커밋 실패·동시 취소·다른 주문 독립 진행·요청사항 보존, 실제 HTTP I-11 1회·I-12 통신 실패 3회·5xx 1회·지연 완료를 검증했다.
- [x] 별도 Gateway·JWT·Order·PostgreSQL에서 200·400·401·403·404·409·500·503을 확인했다. 인증·delivery 응답은 HTTP 대역이고 실제 Slack 메시지는 보내지 않았다. 임시 프로세스·DB는 정리했다.
- [x] 로컬 주문 이미지만 교체하고 Flyway V2·같은 스키마 FK·health UP·Gateway O-05 문서·미인증 401을 확인했다. 기존 주문 데이터 해시와 다른 8개 컨테이너·PostgreSQL 볼륨·.env·Webhook 값은 유지됐다.
- [x] 주문 생성·취소의 예외·보상·재시도·수동 복구 흐름을 [설계 문서](../services/order/failure-handling.md)에 정리했다. O-01 커밋 결과 확인·생성 멱등키·복구 API는 후속 제안으로 구분했다.
- [x] 최종 전체 build 재확인, 문서·에러 코드 16개·공백·변경 범위 42개 파일의 로컬 링크 검사 통과. 별도 Q&A 변경 3개와 .env는 보존했다.
- [x] 사용자 요청으로 O-05 변경을 `2e5fcc1 feat(order): 주문 취소와 실패 복구 기록 구현`으로 커밋했다. Git은 파일 이름 변경을 포함해 41개 변경으로 집계했다.
- [x] 사용자 요청에 따라 ADR 0007·OrderCreation·V3·시작/완료/종료/복구 기록과 커밋 결과 재확인을 구현했다. 기존 V1·V2는 유지했다.
- [x] 실제 커밋 뒤 반환 경계 예외·재확인 실패·원래 저장 트랜잭션 잠금 대기, 주문·완료 기록 원자성·알림 재발행 없음 확인
- [x] I-09 결과가 미확정이면 기존 I-13 보상을 시도해도 RECOVERY_REQUIRED를 유지한다. 확인된 미저장만 삭제 결과에 따라 종료하거나 복구 대상으로 남긴다.
- [x] 신규 도메인 11개·application 11개·PostgreSQL 15개·HTTP 3개, 총 40개 추가. 전체 build 성공, 주문 292개·전체 XML 390개, 실패·오류·skip 0
- [x] 격리 Gateway·JWT·실제 Feign·PostgreSQL에서 201·400·401·500·503, 진행 기록 Audit, 시작 기록 실패 시 원격 호출 없음 확인. 임시 프로세스·DB를 정리하고 실제 Slack 메시지는 보내지 않았다.
- [x] 로컬 주문 이미지만 반영해 Flyway V3·생성 기록 FK 없음·health UP·Gateway O-01/201 문서·미인증 401을 확인했다. 기존 주문·취소 기록 해시, 다른 8개 컨테이너·PostgreSQL 볼륨·.env·Webhook 값을 유지했다.
- [x] 중앙 서비스 문서와 company·delivery README에 연동 계약을 연결했다. COMPANY-001·DELIVERY-001·DELIVERY-002는 제공 담당자의 확인을 기다리는 OPEN으로 기록했다.
- [x] 문서 동기화·에러 코드 16개·공백·변경 범위 32개 파일의 로컬 링크와 협업 앵커 검사를 통과했다. 실제 Webhook 값이 변경 파일·최근 주문 로그에 없고 별도 Q&A 변경 3개는 보존했다.
- [x] 사용자 요청으로 생성 보강·협업 기록 32개 파일을 `39f704a feat(order): 생성 진행 기록과 커밋 결과 재확인 구현`으로 커밋했다. 별도 Q&A 변경 3개는 제외했다.
- [x] 필수 Idempotency-Key·ADR 0008·V4, 사용자·키 UNIQUE·요청 지문·최초 응답 JSONB, 재요청 상태별 409 구현
- [x] 지문 12개·도메인 11개·PostgreSQL 16개·MVC 7개·HTTP 7개·application 2개, 총 55개 추가. 전체 build 성공, 주문 347개·전체 XML 445개, 실패·오류·skip 0
- [x] 실제 DB 동시 INSERT 경쟁에서 한 주문·배송·이벤트만 생성, 실패 트랜잭션 종료 후 기존 키 재조회 확인. 다른 사용자·키 대소문자·논리 삭제 이력·V3→V4 데이터 보존 검증
- [x] 격리 Gateway·JWT·실제 Feign·PostgreSQL에서 201·400·401·409·500·503, 필수 헤더 전달·최초 응답 재사용·진행/실패/복구 상태 차단 확인. 임시 JVM·DB를 정리했고 실제 Slack 메시지는 보내지 않았다.
- [x] 로컬 주문 이미지만 반영해 Flyway V4·사용자/키 UNIQUE·JSONB·health UP·Gateway 필수 헤더 문서·미인증 401을 확인했다. 기존 3개 테이블·다른 8개 컨테이너·PostgreSQL 볼륨·.env·Webhook 값을 유지했다.
- [x] SA·에러 코드·호출자 연동 계약·실패 처리·스텝 45를 갱신했다. COMPANY-001·DELIVERY-001·DELIVERY-002는 기존 OPEN을 유지한다.
- [x] 문서 동기화·에러 코드 20개·git diff 공백·변경 범위 36개 파일의 로컬 링크 141개·신규 파일 공백 검사를 통과했다. SA 주문 ERD·생성 시퀀스 렌더와 DBML 파싱을 확인했다. 실제 Webhook 값은 변경 파일·최근 주문 로그에 없으며 별도 학습 파일 4개와 progress의 GitHub 기록은 보존했다.
- 면접 포인트: 키 존재 조회만으로 동시 생성을 막을 수 없는 이유, 유니크 충돌 뒤 새 트랜잭션이 필요한 이유, 최초 응답과 현재 주문 조회의 차이
- [x] 사용자 요청으로 남은 분량을 확인했다. 기본 명세는 O-06 하나이며 생성 진행 조회·운영 복구·Outbox는 추가 보강이다. 전체 build를 재확인했고 기존 성공 결과를 재사용했다.
- [x] 사용자가 "삭제 기록 포함, 끝까지 진행"을 선택했다. ADR 0009를 채택하고 멱등키 변경 36개 파일을 ed9f8d0에 커밋했다. 별도 학습 파일과 progress의 GitHub 기록은 제외했다.
- [x] O-06 MASTER 삭제·I-10·V5·삭제 허가 근거와 진행 기록·커밋 재확인·같은 DELETE 재개·미해결 취소 상호 차단 구현
- [x] 신규 도메인 7개·application 10개·MVC 8개·PostgreSQL 21개·HTTP/Feign 28개, 총 74개. 전체 build 성공, 주문 421개·전체 XML 519개, 실패·오류·skip 0
- [x] 실제 PostgreSQL flush·완료 기록·커밋 실패, 실제 커밋 뒤 반환 예외, 동일 주문 삭제 잠금 대기·I-13 1회, 삭제 후 생성 멱등키 보존 확인
- [x] I-13의 200·202를 완료로 오인하던 void Feign 반환을 회귀 테스트로 재현하고 204 검사로 보강했다(TS-017).
- [x] 최종 Gateway·JWT·Feign·격리 PostgreSQL에서 생성→취소→삭제, 배송 완료 삭제, 로컬 롤백 뒤 같은 DELETE 재개, 내부 202 거부, 삭제 후 생성 멱등키 재응답을 확인했다. 200·201·400·401·403·404·409·500·503을 검증했고 임시 JVM·DB를 정리했다.
- [x] 로컬 주문 서비스만 최종 이미지로 반영했다. V5·주문 ID UNIQUE·같은 스키마 FK·health UP·Gateway O-06/200·기존 O-01 필수 키·미인증 DELETE 401을 확인했다. 기존 3개 테이블·나머지 8개 컨테이너·PostgreSQL 볼륨·.env·Webhook은 유지됐고 새 삭제 기록도 0건이다.
- [x] 문서 동기화·에러 코드 21개·공백·작업 범위 42개 파일의 링크와 신규 파일 검사를 통과했다. SA 주문 ERD 렌더·DBML 파싱·삭제 FK 방향을 확인했다. 실제 Webhook 값은 변경 파일·최근 주문 로그에 없으며 별도 학습 파일 4개와 GitHub progress 기록을 보존했다.
- 면접 포인트: 배송 삭제 후 로컬 롤백에도 허가 근거가 필요한 이유, 삭제 재개와 미확정 생성 재실행의 차이, 202와 204의 완료 의미

- [x] 사용자 요청으로 최신 dev를 확인했다. 1384a5d의 25개 커밋을 격리 worktree에서 통합하며 공유 문서 2개 충돌을 해결했다. ORDER-001을 보존하고 현재 PICKUP_WAITING 기준·ADR 0002 미채택을 답변했다. dev의 TS-006은 유지하고 주문 TS-006~016을 TS-007~017로 옮겨 참조를 맞췄다.
- [x] 최신 company·delivery DTO와 주문 계약을 정적으로 대조했다. 실제 서비스 통합, 배송의 집하/취소 동시성, 지연 생성 계약은 협업 확인 항목으로 남겼다.

- [x] 최신 dev 통합 상태에서 `./gradlew build --console=plain` 성공. 55개 Gradle 작업을 새로 실행했고 주문 421개·전체 XML 659개, 실패·오류·skip 0이다. 에러 코드 27개 검사도 통과했다. 타 서비스 실제 HTTP 연동·새 배포를 수행한 결과는 아니다.

## 다음 단계
1. PR 준비 중이다. 최신 dev와의 빌드 확인 후 현재 브랜치를 푸시하고 구현·검증·협업 확인 사항을 STAR 형식 본문으로 게시한다.
2. 기본 주문 API O-01~O-06 구현은 완료했다. [스텝 46](../learning/junhyun/steps/step-46-order-deletion.md)과 [연동 계약](../services/order/integration-contract.md)으로 현재 범위와 재시도 규칙을 확인한다.
3. 팀 company·delivery의 실제 업무 구현·데이터가 준비되면 생성·취소·삭제·실패 보상을 통합 검증한다. COMPANY-001·DELIVERY-001·DELIVERY-002는 OPEN이며 대역 테스트를 실제 제공 서비스 검증으로 표시하지 않는다.
4. 생성 진행 조회·생성/취소 운영 복구·시간 예산·관측·Outbox는 선택 후속이다. 이번 기본 기능 완료 조건에 추가하지 않는다. 지연 생성 자동 복구는 DELIVERY-002의 추가 계약이 필요하다.
5. Slack URL은 .env와 실행 중인 주문 컨테이너에 있다. 실제 URL을 출력·커밋하지 않는다. 승인받은 실제 테스트 1건은 이미 전송했으므로 자동 반복하지 않는다.
6. 로컬 주문만 반영할 때는 이미지 빌드 후 `docker compose up -d --no-deps --no-build order-service`를 사용한다. 실제 업무 데이터 변경은 별도 요청 범위에서 수행한다.
7. 별도 학습 노트 4개와 progress 맨 앞의 GitHub 학습 기록은 이 주문 구현의 커밋에서 제외한다. 기존 stash도 유지한다.

## 직접 해보기
1. 저장소 터미널에서 `git branch --show-current`를 실행한다. `feat/order-create`가 나오면 작업 브랜치에 있다. IntelliJ에서는 Git 브랜치 위젯에서 같은 이름을 확인한다.
2. `git log --oneline -15`로 삭제 `0d2aa46`, 멱등키 `ed9f8d0`, 생성 기록 `39f704a`를 확인한다. 취소는 `2e5fcc1`, 수정은 `6ac9b8a`, 조회는 `9a41925`, Slack 기록은 `af0ba34`, Slack 코드는 `7a3f93f`, Feign·Controller는 `9b48adc`, application은 `3b5a39c`, 기반은 `216a768`이다.
3. IntelliJ에서 `docs/SA/sparta-logistics-sa.md` 5.5를 열고 주문 테이블 필드와 상태 `ORDERED`, `CANCELED`를 확인한다.
4. `./gradlew :order-service:test --tests '*OrderTest'`로 도메인 규칙 24개를 확인한다. IntelliJ에서는 `OrderTest` 클래스 옆 실행 버튼으로 실행한다.
5. `./gradlew :order-service:test --tests 'com.sparta.logistics.order.application.*'`로 생성·조회·수정·취소 application 테스트 89개를 확인한다.
6. `./gradlew :order-service:test --tests '*OrderControllerWebMvcTest'`로 생성 Controller 18개를 확인한다.
7. `./gradlew :order-service:test --tests '*OrderNotificationIntegrationTest'`로 Slack 대역과 실제 비동기 연결 6개를 확인한다. IntelliJ에서는 클래스 옆 실행 버튼을 사용한다.
8. Docker 실행 상태에서 `./gradlew :order-service:test`로 HTTP·DB 통합 테스트를 포함한 421개를 확인한다. 지문·멱등성 39개만 보려면 `--tests '*OrderRequestIdentityTest' --tests '*OrderIdempotency*'`를 추가한다. IntelliJ에서는 해당 테스트 클래스의 실행 버튼을 사용하고 결과는 `order-service/build/reports/tests/test/index.html`에서 본다.
9. `./gradlew :order-service:test --tests '*OrderDeletion*'`로 삭제 관련 74개를 확인한다. IntelliJ에서는 해당 5개 테스트 클래스 옆 실행 버튼을 사용한다.
10. `docker compose exec -T order-service curl -fsS http://localhost:8084/actuator/health`에서 `UP`을 확인한다. 실제 Webhook URL을 출력하는 명령은 쓰지 않는다.

## 메모
- 작업일: 2026-10-15 (Asia/Seoul). 브랜치 생성·기능 추천 이후 사용자가 주문 기반(Entity·마이그레이션·Repository·테스트) 구현과 설명을 요청했다.
- 팀 브랜치 규칙은 `docs/guides/git-workflow.md`의 `feat/{모듈}-{작업}`이며 통합 대상은 `dev`다. 기반 커밋은 `216a768`이며 푸시는 수행하지 않았다.
- 브랜치 생성 당시 dev의 company·delivery는 골격이었다. PR 준비에서 확인한 dev 1384a5d에는 user·company·delivery 구현이 반영됐으며, 다른 팀원의 미병합 브랜치를 가져오지는 않았다.
- 서비스 ADR 0001·0002는 `제안` 상태이며 재고 처리와 취소 조건에 SA v2.0과 다른 내용이 있다. 현재 기준은 재고 확인·차감 없음, 집하 시작 전(`PICKUP_WAITING`) 취소다.
- 보관한 stash: `pre-order-branch-2026-10-15: preserve README newline`, 생성 시 객체 `8b7392d5755626822b727ee5cbb5cb0a74752320`. 새 README에는 이미 줄바꿈이 있다. 기존 README 전체를 새 브랜치에 덮어쓰지 않는다.
- 기반·application·Feign·Controller·Slack은 구현했다. 격리 Gateway·비동기·HTTP 대역 검증과 실제 Webhook 테스트 1건의 200·ok 확인까지 마쳤다. 팀 서비스 실제 데이터로 주문 전체 흐름을 검증하는 단계는 남아 있다.
- 기반은 `216a768`, application은 `3b5a39c`, Feign·Controller는 `9b48adc`, Slack은 `7a3f93f`에 커밋했다. 푸시는 수행하지 않았다.
- Slack 연동 기록은 `af0ba34`, O-02·O-03은 `9a41925`, O-04는 `6ac9b8a`, O-05는 `2e5fcc1`, 생성 기록·V3는 `39f704a`에 커밋했다. 멱등키·V4는 ed9f8d0에 커밋했다. O-06의 최종 커밋 제목은 `feat(order): 주문 삭제와 재요청 복구 구현`이다. 공통·다른 서비스 소스는 유지했다.
- `docs/learning/junhyun/README.md`와 qna의 common-core-split-easy·downstream-error-propagation·github-ai-review-auto-approval 변경은 별도 작업이므로 보존한다. 이 progress 맨 앞의 GitHub 학습 기록도 해당 작업의 변경이다.
- O-04는 상태 검증이 있는 짧은 로컬 쓰기 트랜잭션이다. 응답 Audit를 위해 flush하지만 생성용 OrderWriter·이벤트를 거치지 않는다. 격리 Gateway 검증의 user 인증 응답은 HTTP 대역이며 실제 Slack 메시지는 추가로 보내지 않았다.
- O-05의 PROCESSING·RECOVERY_REQUIRED를 자동 만료시키거나 SQL로 임의 해제하지 않는다. 복구 API는 아직 없으며 failure-handling.md의 확인 절차와 후속 설계를 따른다. 실제 커밋 응답 패킷 유실·프로세스 강제 종료·다중 JVM 시험은 미실행이다.
- O-01 생성 기록·멱등키는 자동 만료·재실행하지 않는다. 같은 사용자·키·본문의 완료 요청은 최초 201, 다른 본문·미완료·실패는 전용 409다. 다른 키로 우회한 중복까지 자동 판별하지는 않는다. 실제 DB 커밋 뒤 반환 경계 예외는 재현했지만 커밋 응답 패킷 유실 자체를 주입한 것은 아니다.
- 코드 구현 단계에서는 기존 Compose를 교체하지 않았고, 실제 연동 단계에서 config-server·order-service만 교체했다. `.env`는 사용자가 설정했으며 에이전트 작업 전후 해시는 같다. 알림은 유실 가능한 부가 기능이며 실패·큐 포화 로그의 주문 ID로 추적한다. 상세 기준은 주문 ADR 0003이다.
- BigDecimal 전환 후 사용자가 정수 원화 범위를 확인하고 원복을 요청했다. 현재 기준은 [주문 ADR 0005](../services/order/decisions/0005-order-money-integer-krw.md)의 long·BIGINT이며 SA 5.5·6.7과 일치한다. 팀 공통 SA와 다른 서비스 코드는 수정하지 않았다.
- 금액 원복 당시에는 V1이 미커밋 초안이었다. 현재 V1은 `216a768`에 커밋됐으므로 이후 스키마 변경은 새 마이그레이션으로 한다.
- 로컬 기동 과정에서 run.sh가 PostgreSQL도 재생성했다. 기존 pgdata 볼륨 재사용·초기화 생략, .env 해시 유지, 나머지 7개 컨테이너 ID·시작 시각 유지와 관련 서비스 health UP을 확인했다(TS-009). 정확한 재생성 판정 원인은 미확인이다.
- O-06의 V5는 주문 소유 스키마만 보강한다. 삭제 허가 근거를 별도 커밋하고 SA 7.5대로 I-13 중 주문 잠금을 유지한다. 원격 삭제는 정확한 204만 완료로 확인하며 O-06 요청당 1회다. 동일 DELETE의 재개는 생성·취소의 미확정 새 시도와 다르다.
