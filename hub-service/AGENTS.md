# hub-service 지침

이 모듈 코드를 고치는 사람과 AI 에이전트를 위한 지침이다. 팀 공통 규칙은 루트 [`AGENTS.md`](../AGENTS.md)가 먼저다. 다른 서비스와의 약속(API 계약, 구현 현황, 요청 기록)은 [`docs/services/hub/README.md`](../docs/services/hub/README.md)에 있다.

## 책임과 경계
- 맡는 것: 허브·허브 간 경로 조회(H-01~H-04), 초기 데이터(허브 17개, 경로), 내부 조회(I-02, I-03), 재고 관리 고도화(H-05·H-06, I-04~I-06)
- 맡지 않는 것: 허브·경로 생성·수정·삭제(범위 제외)

## 작업 전에 읽을 것
- [`docs/services/hub/README.md`](../docs/services/hub/README.md): 계약, 구현 현황, 처리할 `OPEN` 요청
- SA(`docs/SA/sparta-logistics-sa.md`): 4.3·5.3(hub_schema), 6.5(API 상세), 10.1(허브 초기 데이터)
- 고치는 계층의 규칙: [`docs/agents/rules/`](../docs/agents/rules/README.md)
- 서비스 결정: [`docs/services/hub/decisions/`](../docs/services/hub/decisions/README.md)

## 모듈 규칙
- 패키지 `com.sparta.logistics.hub`, 스키마 `hub_schema`, DB 계정 `hub_app`.
- 초기 데이터는 Flyway 마이그레이션으로 넣는다. 이미 커밋한 마이그레이션 파일은 고치지 않는다.
- 기본 범위에서 hub는 다른 서비스를 호출하지 않는다. 재고 고도화의 H-06만 company I-08(상품 존재·소속 업체 확인)과 I-07(업체 관리자·소속 허브 확인, 업체 관리자 입고 권한, ADR 0003)을 호출한다. 이 외에 hub가 다른 서비스를 호출하는 코드를 만들지 않는다.
- 재고 구현은 팀 합의 기록인 SA v2.0(5.3, 6.3, 6.5, 7.3, 7.4)을 따른다. 다만 ADR 0003의 두 가지(재고 할당 범위, 업체 관리자 입고 권한)는 SA와 다르게 구현했다. `docs/services/hub/decisions/`의 재고 ADR 0001·0002는 팀 승인 기록이 없는 제안이라 채택하지 않았다.

## 다른 모듈이 필요할 때
- 다른 모듈의 코드는 직접 고치지 않는다. 그 서비스의 `docs/services/{service}/README.md` 협업 로그에 `OPEN`으로 요청을 남기고, 답이 필요한 작업이면 멈춘다.
- `common`, `common-core`, `config-repo/application.yml`, 루트 `build.gradle`, `docker-compose.yml` 변경은 모든 서비스에 영향이 가므로 팀 전체 리뷰를 받는다.

## 검증
- `./gradlew :hub-service:test` (DB 통합 테스트는 Docker가 켜져 있어야 한다)
- API를 바꿨으면 `script/run.sh hub-service`로 다시 띄워 Gateway(8080)로 호출해 본다.
- 에러 코드를 바꿨으면 `script/error-codes-check.sh`
