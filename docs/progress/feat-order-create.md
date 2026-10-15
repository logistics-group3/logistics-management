# feat/order-create

## 지금 하는 일
주문 기반을 유지하고 금액을 정수 원화 long·BIGINT로 복원했다. 다음은 O-01 application 구현이다.

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
- [ ] 업체·상품 검증, 배송 생성·보상, O-01 API 구현
- [ ] 커밋 후 Slack 알림과 실제 서비스 연동 검증
- 면접 포인트: 주문 시점 가격을 저장하는 이유, 배송 생성 성공 후 주문 DB 저장이 실패할 때 보상하는 방법

## 다음 단계
1. [구현 노트](../services/order/notes.md), [스텝 35](../learning/junhyun/steps/step-35-order-foundation.md), [ADR 0005](../services/order/decisions/0005-order-money-integer-krw.md)에서 기반 구현과 현재 정수 원화 금액 기준을 확인한다.
2. 다음 추천 범위는 `CreateOrderCommand`·`OrderResult`, 외부 서비스 호출 인터페이스, `OrderService`와 Mock 단위 테스트다. 정상 생성·소유자 검증·배송 실패·DB 저장/커밋 실패·보상 실패를 확인한다. 세부 계획은 [구현 순서 노트](../learning/junhyun/qna/2026-10-15-order-implementation-order.md)의 후속 질문에 있다. 아직 구현에 착수하지 않았다.
3. company I-07·I-08, delivery I-09·I-13 계약으로 주문 생성 흐름을 구성한다. 단위 테스트에서는 테스트 대역을 사용하고 실제 연동 결과와 구분한다.
4. orderId를 배송 호출 전에 선발급하고 생성 팩토리에 전달한다. `saveAndFlush` 반환 Entity를 사용하며, flush와 커밋의 차이를 고려해 트랜잭션 종료 실패도 보상한다.
5. API 구현 뒤 실제 환경에서 검증할 때는 대상 Compose 프로젝트와 포트를 먼저 확인한다.

## 직접 해보기
1. 저장소 터미널에서 `git branch --show-current`를 실행한다. `feat/order-create`가 나오면 작업 브랜치에 있다. IntelliJ에서는 Git 브랜치 위젯에서 같은 이름을 확인한다.
2. `git log -1 --oneline`으로 출발 커밋 `f700c0c`를 확인한다. 새 커밋을 만든 뒤에는 `git merge-base HEAD origin/dev`로 공통 기준을 확인한다.
3. IntelliJ에서 `docs/SA/sparta-logistics-sa.md` 5.5를 열고 주문 테이블 필드와 상태 `ORDERED`, `CANCELED`를 확인한다.
4. `./gradlew :order-service:test --tests '*OrderTest'`로 도메인 규칙 24개를 확인한다. IntelliJ에서는 `OrderTest` 클래스 옆 실행 버튼으로 실행한다.
5. Docker가 실행 중인 상태에서 `./gradlew :order-service:test`로 통합 테스트를 포함한 42개를 확인한다. 결과는 `order-service/build/reports/tests/test/index.html`에서 본다.

## 메모
- 작업일: 2026-10-15 (Asia/Seoul). 브랜치 생성·기능 추천 이후 사용자가 주문 기반(Entity·마이그레이션·Repository·테스트) 구현과 설명을 요청했다.
- 팀 브랜치 규칙은 `docs/guides/git-workflow.md`의 `feat/{모듈}-{작업}`이며 통합 대상은 `dev`다. 커밋·푸시는 수행하지 않았다.
- 원격 `dev` 기준 company·delivery 업무 API는 아직 골격과 예정 상태다. 팀원 개인 브랜치의 진행 상황은 확인하지 않았다.
- 서비스 ADR 0001·0002는 `제안` 상태이며 재고 처리와 취소 조건에 SA v2.0과 다른 내용이 있다. 현재 기준은 재고 확인·차감 없음, 집하 시작 전(`PICKUP_WAITING`) 취소다.
- 보관한 stash: `pre-order-branch-2026-10-15: preserve README newline`, 생성 시 객체 `8b7392d5755626822b727ee5cbb5cb0a74752320`. 새 README에는 이미 줄바꿈이 있다. 기존 README 전체를 새 브랜치에 덮어쓰지 않는다.
- 기반 구현은 완료했다. 테스트 컨텍스트에서 Flyway와 JPA를 검증했으며 실제 Gateway 호출, 운영 Compose DB 적용, Slack 발송은 수행하지 않았다.
- BigDecimal 전환 후 사용자가 정수 원화 범위를 확인하고 원복을 요청했다. 현재 기준은 [주문 ADR 0005](../services/order/decisions/0005-order-money-integer-krw.md)의 long·BIGINT이며 SA 5.5·6.7과 일치한다. 팀 공통 SA와 다른 서비스 코드는 수정하지 않았다.
- V1은 미커밋이고 테스트용 임시 DB에만 적용한 상태여서 초안을 수정했다. 유지되는 DB에 적용된 버전은 새 마이그레이션으로 바꾼다.
