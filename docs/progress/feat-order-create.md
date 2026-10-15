# feat/order-create

## 지금 하는 일
O-01 application 생성·권한·보상을 구현하고 실제 DB 커밋 실패까지 검증했다. 다음은 실제 Feign·Controller 연결이다.

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
- [ ] 실제 Feign 어댑터·OrderService 빈 등록·O-01 Controller·Gateway 검증
- [ ] 커밋 후 Slack 알림과 실제 서비스 연동 검증
- 면접 포인트: 타임아웃과 저장 전 거절의 차이, flush 성공 후 커밋 실패를 별도 빈의 트랜잭션으로 처리하는 이유

## 다음 단계
1. [구현 노트](../services/order/notes.md)와 [스텝 37](../learning/junhyun/steps/step-37-order-creation-application.md)을 읽고 현재 범위를 확인한다. 금액은 ADR 0005의 long·BIGINT다.
2. company I-07·I-08, delivery I-09·I-13의 실제 Feign 어댑터를 연결한다. 상대 계약 변경이 필요하면 해당 서비스 협업 로그에 요청한다.
3. I-13은 OrderService가 총 3회 시도하므로 Feign 재시도를 중첩하지 않는다. GET·I-09에는 공통 멱등 재시도 설정을 적용한다.
4. 실제 어댑터가 준비되면 OrderService를 Spring 빈으로 등록하고 테스트의 명시적 @Import와 중복되지 않게 조정한다. Controller·Request·Response를 연결한다.
5. 대상 Compose 프로젝트와 포트를 확인한 뒤 실제 Gateway로 정상·오류 요청을 검증한다. Slack 알림은 커밋 후 비동기로 연결한다.

## 직접 해보기
1. 저장소 터미널에서 `git branch --show-current`를 실행한다. `feat/order-create`가 나오면 작업 브랜치에 있다. IntelliJ에서는 Git 브랜치 위젯에서 같은 이름을 확인한다.
2. `git log -1 --oneline`으로 기반 커밋 `216a768`을 확인한다. `git merge-base HEAD origin/dev`로 출발 기준을 확인한다.
3. IntelliJ에서 `docs/SA/sparta-logistics-sa.md` 5.5를 열고 주문 테이블 필드와 상태 `ORDERED`, `CANCELED`를 확인한다.
4. `./gradlew :order-service:test --tests '*OrderTest'`로 도메인 규칙 24개를 확인한다. IntelliJ에서는 `OrderTest` 클래스 옆 실행 버튼으로 실행한다.
5. `./gradlew :order-service:test --tests 'com.sparta.logistics.order.application.*'`로 새 단위 테스트 23개를 확인한다.
6. Docker 실행 상태에서 `./gradlew :order-service:test`로 통합 테스트를 포함한 69개를 확인한다. 결과는 `order-service/build/reports/tests/test/index.html`에서 본다.

## 메모
- 작업일: 2026-10-15 (Asia/Seoul). 브랜치 생성·기능 추천 이후 사용자가 주문 기반(Entity·마이그레이션·Repository·테스트) 구현과 설명을 요청했다.
- 팀 브랜치 규칙은 `docs/guides/git-workflow.md`의 `feat/{모듈}-{작업}`이며 통합 대상은 `dev`다. 기반 커밋은 `216a768`이며 푸시는 수행하지 않았다.
- 원격 `dev` 기준 company·delivery 업무 API는 아직 골격과 예정 상태다. 팀원 개인 브랜치의 진행 상황은 확인하지 않았다.
- 서비스 ADR 0001·0002는 `제안` 상태이며 재고 처리와 취소 조건에 SA v2.0과 다른 내용이 있다. 현재 기준은 재고 확인·차감 없음, 집하 시작 전(`PICKUP_WAITING`) 취소다.
- 보관한 stash: `pre-order-branch-2026-10-15: preserve README newline`, 생성 시 객체 `8b7392d5755626822b727ee5cbb5cb0a74752320`. 새 README에는 이미 줄바꿈이 있다. 기존 README 전체를 새 브랜치에 덮어쓰지 않는다.
- 기반과 application 구현은 완료했다. 생성 서비스는 테스트에서 명시적으로 조립했으며 실제 Feign 어댑터·HTTP API·Gateway·운영 Compose DB 적용·Slack 발송은 미구현 또는 미검증이다.
- 기반은 `216a768`에 커밋했고, 이번 application 코드·테스트·문서는 아직 미커밋이다. 푸시는 수행하지 않았다.
- BigDecimal 전환 후 사용자가 정수 원화 범위를 확인하고 원복을 요청했다. 현재 기준은 [주문 ADR 0005](../services/order/decisions/0005-order-money-integer-krw.md)의 long·BIGINT이며 SA 5.5·6.7과 일치한다. 팀 공통 SA와 다른 서비스 코드는 수정하지 않았다.
- 금액 원복 당시에는 V1이 미커밋 초안이었다. 현재 V1은 `216a768`에 커밋됐으므로 이후 스키마 변경은 새 마이그레이션으로 한다.
