# delivery-service 지침

이 모듈 코드를 고치는 사람과 AI 에이전트를 위한 지침이다. 팀 공통 규칙은 루트 [`AGENTS.md`](../AGENTS.md)가 먼저다. 다른 서비스와의 약속(API 계약, 구현 현황, 요청 기록)은 [`docs/services/delivery/README.md`](../docs/services/delivery/README.md)에 있다.

## 책임과 경계
- 맡는 것: 배송·구간 생성(I-09), 담당자 자동 배정과 재배정(D-04), 구간 상태 관리(D-05), 배송 조회(D-01~D-03), 내부 API(I-09~I-13)
- 맡지 않는 것: 배송담당자 목록(user의 I-14), 경로 정보(hub의 I-03), 주문 상태(order)

## 작업 전에 읽을 것
- [`docs/services/delivery/README.md`](../docs/services/delivery/README.md): 계약, 구현 현황, 처리할 `OPEN` 요청
- SA(`docs/SA/sparta-logistics-sa.md`): 2.5(상태 전이), 4.6·5.6(delivery_schema), 6.8(API 상세), 7장
- 고치는 계층의 규칙: [`docs/agents/rules/`](../docs/agents/rules/README.md)
- 서비스 결정: [`docs/services/delivery/decisions/`](../docs/services/delivery/decisions/README.md)

## 작업 방식
- 작업은 태스크(`T01`, `T02` ...) 단위로 브랜치 `feat/delivery-T{nn}` 하나에서 진행하고, 세부 작업은 커밋으로 나눈다. 규칙은 [`docs/workflow.md`](docs/workflow.md)에 있다.
- 커밋할 때마다 [`docs/tasks/T{nn}.md`](docs/tasks/README.md)의 "작업 기록"에 그 커밋 항목을 같은 커밋에 넣는다.
- 배송 도메인 설명과 API 상세는 [`docs/domain/`](docs/domain/delivery.md)에 있다. 설계를 판단하기 전에 읽는다.
- 대화에서 설계 결정이 나면 그 응답 안에서 바로 [`docs/domain/delivery-decision-log.md`](docs/domain/delivery-decision-log.md)에 추가한다. 컨텍스트가 압축되기 전에 남기기 위해서다. 형식은 [`docs/workflow.md`](docs/workflow.md) 6절을 따른다.

## 모듈 규칙
- 패키지 `com.sparta.logistics.delivery`, 스키마 `delivery_schema`, DB 계정 `delivery_app`.
- 배송은 집하 → 허브 이동 → 업체 배송 3구간이다. 두 업체가 같은 허브 소속이면 허브 이동 구간을 만들지 않는다. 주문 1건 = 배송 1건이다.
- 마이그레이션에서 테이블을 만들거나 컬럼을 추가하면 `COMMENT ON TABLE/COLUMN`으로 설명을 함께 단다(SA 5장 설명 기준, Audit 컬럼 제외). DB 도구와 ERD에서 바로 보이게 하기 위해서다.
- 상태 전이는 SA 2.5를 따르고 Entity 메서드에서 검증한다. 위반은 `INVALID_STATUS_TRANSITION`(공통 코드)이다.
- 구간 담당자는 구간 출발지를 관리하는 허브 소속 배송담당자 중 담당 건수가 가장 적은 사람을 자동 배정한다. 없으면 `DELIVERY_MANAGER_NOT_FOUND`로 실패한다.
- 배송 생성·삭제는 외부 API로 열지 않는다. I-09, I-13은 orderId로 멱등하게 처리한다.

## 다른 모듈이 필요할 때
- 다른 모듈의 코드는 직접 고치지 않는다. 그 서비스의 `docs/services/{service}/README.md` 협업 로그에 `OPEN`으로 요청을 남기고, 답이 필요한 작업이면 멈춘다.
- `common`, `common-core`, `config-repo/application.yml`, 루트 `build.gradle`, `docker-compose.yml` 변경은 모든 서비스에 영향이 가므로 팀 전체 리뷰를 받는다.

## 검증
- `./gradlew :delivery-service:test` (DB 통합 테스트는 Docker가 켜져 있어야 한다)
- API를 바꿨으면 `script/run.sh delivery-service`로 다시 띄워 Gateway(8080)로 호출해 본다.
- 에러 코드를 바꿨으면 `script/error-codes-check.sh`
