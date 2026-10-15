# order 구현 노트

완료한 단계의 처리 과정과 검증 기록이다. 이전 내용은 주제별 파일로 옮겼으며 당시 결과를 유지한다.

- [주문 기반과 금액 결정 이력](notes/foundation-and-money.md)
- [주문 생성·Feign·Controller와 실패 보상](notes/create-and-compensation.md)
- [커밋 후 Slack 알림과 실제 연결](notes/slack-notification.md)

## O-02 주문 검색·O-03 단건 조회 (2026-10-15)

### 처리 과정

1. Slack 실제 연동 기록을 사용자 요청에 따라 `af0ba34`로 커밋했다.
2. OrderQueryService와 검색 조건·저장소 계약, QueryDSL 구현, ORDER_NOT_FOUND를 추가했다.
3. 기존 Controller·OrderResponse에 검색·단건 조회를 연결하고 권한·페이지·기간·삭제 제외를 검증했다.

### 핵심

- 검색은 인증 사용자의 ordererId를 내용·count 쿼리에 함께 넣는다. MASTER만 주문자 제한을 생략한다.
- 조회는 readOnly 트랜잭션에서 저장된 상품·가격 스냅샷을 읽으며 회사·배송·Slack을 호출하지 않는다.
- 종료일 다음 날 0시 미만으로 비교해 마지막 소수 초까지 포함한다. 정렬은 createdAt·quantity만 허용하며 ID로 동률 순서를 고정한다.
- 요청 SearchCondition과 서버가 권한·시간 경계를 정한 SearchCriteria를 나눠 domain이 application DTO에 의존하지 않게 한다.

### 검증

신규 application 10개·MVC 12개·HTTP/PostgreSQL 22개를 추가했고 전체 주문 152개가 통과했다. 전체 build 성공, XML 합계 250개이며 실패·오류·skip은 0이다.
격리 Gateway·JWT·Order·PostgreSQL에서 200·400·401·403·404, 위조 헤더 차단, 본인 범위·count를 확인했다. 임시 프로세스와 DB는 정리했다. 자세한 실행 근거는 [스텝 41](../../learning/junhyun/steps/step-41-order-query-api.md)에 있다.
Swagger size 기본값 불일치는 TS-010, 공유 테스트 DB의 fixture 간섭은 TS-011로 해결했다.

### 확인 질문

- 본인 주문 제한을 조회 후 필터링하면 페이지와 전체 건수에 어떤 문제가 생기는가?
- 종료일 23:59:59와 다음 날 00:00 미만 조건은 어떻게 다른가?

### 메모

테이블·마이그레이션·공통 모듈과 원화 long 계약은 바꾸지 않았다. 조회 코드와 기록은 후속 사용자 요청으로 `9a41925`에 커밋했다. 추가 Slack 메시지는 보내지 않았다.

## O-04 요청사항 수정 (2026-10-15)

### 처리 과정

1. 조회 구현 20개 파일을 `9a41925`에 커밋했다. 다른 작업의 Q&A 학습 노트 3개 변경은 제외했다.
2. `OrderRequestNoteTest`로 수정 규칙을 먼저 작성하고 메서드 누락으로 컴파일이 실패하는 것을 확인했다. `Order.updateRequestNote`에 취소 상태·입력 검증을 구현했다.
3. Repository에 잠금 조회와 flush 계약을 추가했다. OrderService에서 소유자 확인·변경·flush를 하나의 쓰기 트랜잭션으로 묶었다.
4. UpdateOrderRequest → UpdateOrderCommand → OrderResult → OrderResponse를 기존 Controller의 PATCH에 연결했다.
5. 단위·MVC·실제 PostgreSQL·Gateway/JWT로 권한·상태·경계값·잠금·롤백·Audit를 검증했다.

### 핵심

- 필수 요청사항은 SA 6.7·8.5에 따라 null·공백을 거부한다. 생성 때 요청사항이 선택인 것과 수정 때 필수인 것은 서로 다른 계약이다.
- 소유자 검증은 application, 취소 상태와 값의 불변식은 Entity가 맡는다. setter 없이 허용된 필드만 바꾼다.
- 비관적 쓰기 잠금은 같은 주문의 상태 확인과 변경 사이에 경쟁 요청이 끼어드는 것을 막는다. DB 트랜잭션 안에서만 사용한다.
- 관리되는 Entity의 변경 감지로 저장하며 새 save를 호출하지 않는다. 응답 직전 flush는 Audit 갱신용이며 커밋과는 다르다.
- 생성용 OrderWriter와 이벤트를 거치지 않는다. 요청사항 수정은 로컬 주문 데이터만 변경한다.

### 검증

도메인 7개·application 7개·MVC 11개·HTTP/PostgreSQL 14개, 총 39개를 추가했다. `./gradlew build --console=plain` 성공, 주문 191개·전체 XML 289개이며 실패·오류·skip은 0이다.
실제 PostgreSQL이 잠금 대기를 보고한 뒤 취소·삭제 트랜잭션을 커밋했고 대기하던 PATCH의 409·404와 기존 요청사항 유지를 확인했다.
격리 Gateway·JWT·Order·PostgreSQL에서는 200·400·401·403·404·409와 위조 헤더 차단을 확인했다. 자세한 재현 절차와 실행 근거는 [스텝 42](../../learning/junhyun/steps/step-42-order-request-note-update.md)에 있다.

### 확인 질문

- readOnly 조회 후 별도 트랜잭션에서 수정하면, 그 사이 취소된 주문을 어떻게 놓칠 수 있는가?
- save 없이 수정 값이 저장되는 이유와 flush 후에도 롤백 가능한 이유는 무엇인가?
- 응답 DTO를 flush 전에 만들면 updatedAt이 이전 값으로 남을 수 있는 이유는 무엇인가?

### 메모

O-04 변경은 후속 사용자 요청으로 `6ac9b8a`에 커밋했다. 스키마·마이그레이션 변경 없이 기존 request_note 컬럼을 사용한다. 실제 Slack 메시지는 보내지 않았다. 후속 취소·삭제의 로컬 상태 변경도 같은 잠금 계약을 따라야 한다.

## O-05 주문 취소와 실패 흐름 설계 (2026-10-15)

### 처리 과정

1. O-04 작업 22개 파일을 `6ac9b8a`로 커밋하고 취소 도메인 테스트의 미구현 실패를 확인했다.
2. 중복 취소와 보상 간 경쟁을 설명한 ADR 0006을 먼저 제안했다. 사용자가 진행 기록 추가를 승인한 뒤 V2와 OrderCancellation을 구현했다.
3. OrderCancellationWriter는 실행권·주문 완료·결과 확인·종료 기록을 짧은 독립 트랜잭션으로 처리한다. Service는 그 밖에서 I-11·I-12를 호출한다.
4. 실제 Feign을 Controller에 연결했다. I-11은 재시도하지 않고 I-12는 통신 실패에만 최대 3회 시도한다.
5. PostgreSQL 제약·지연 트리거와 HTTP 대역으로 실패를 주입했다. 사용자 추가 요청에 따라 생성·취소의 [실패 처리 설계](failure-handling.md)를 작성했다.

### 핵심

- 공개 주문 상태와 취소 진행 상태를 분리한다. PROCESSING·RECOVERY_REQUIRED가 남아 있으면 서버가 재시작돼도 새 시도를 막는다.
- 집하 시작 여부는 I-11이 최종 판단한다. 사전 GET으로 확인한 상태가 취소 시점에도 같다고 가정하지 않는다.
- 주문 CANCELED와 시도 COMPLETED를 같은 DB 트랜잭션으로 커밋한다. 예외가 나면 새 트랜잭션에서 실제 결과를 재확인한다.
- 확인된 롤백만 배송을 원복한다. 타임아웃·5xx·잘못된 성공 응답은 진행 기록을 보존하고 결과 확인을 기다린다.
- 복구·종료 기록 실패가 최초 HTTP 오류를 덮지 않게 한다. 자동 만료나 수동 확인 없는 재실행은 추가하지 않았다.

### 검증

신규 도메인 12개·application 14개·PostgreSQL 17개·HTTP/Feign 18개, 총 61개를 추가했다. 주문 252개와 전체 build가 통과했고 전체 XML은 350개, 실패·오류·skip은 0이다.
격리 Gateway·JWT·Order·PostgreSQL에서 권한·위조 헤더·실제 커밋·배송 원복·늦은 취소 결과를 확인했다. 팀 user·delivery 응답은 HTTP 대역이다.
POST 401의 테스트 드라이버 본문 누락은 TS-012에 기록했다. 상세 근거는 [스텝 43](../../learning/junhyun/steps/step-43-order-cancellation.md)에 있다.

### 확인 질문

- 타임아웃을 취소 실패로 단정하고 즉시 원복하면 어떤 순서 역전이 생길 수 있는가?
- 커밋 예외와 실제 롤백을 구분하지 않으면 정상 취소를 어떻게 되돌릴 수 있는가?
- 동일 주문에 대한 미해결 시도를 DB에 남기는 것이 메모리 잠금보다 어떤 점에서 다른가?

### 메모

O-05는 미커밋이다. 원본 V1·다른 서비스 코드·공통 설정은 변경하지 않았다. 별도 복구 API, 자동 복구 작업, 생성 멱등키는 설계 제안이며 아직 구현하지 않았다.
