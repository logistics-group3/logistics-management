# company-service 지침

이 모듈 코드를 고치는 사람과 AI 에이전트를 위한 지침이다. 팀 공통 규칙은 루트 [`AGENTS.md`](../AGENTS.md)가 먼저다. 다른 서비스와의 약속(API 계약, 구현 현황, 요청 기록)은 [`docs/services/company/README.md`](../docs/services/company/README.md)에 있다.

## 책임과 경계
- 맡는 것: 업체 CRUD(C-01~C-05), 상품 CRUD(P-01~P-05), 내부 조회(I-07, I-08)
- 맡지 않는 것: 재고(고도화), 허브 정보(hub의 I-02를 호출)

## 작업 전에 읽을 것
- [`docs/services/company/README.md`](../docs/services/company/README.md): 계약, 구현 현황, 처리할 `OPEN` 요청
- SA(`docs/SA/sparta-logistics-sa.md`): 4.4·5.4(company_schema), 6.6(API 상세), 7.5(논리 삭제)
- 고치는 계층의 규칙: [`docs/agents/rules/`](../docs/agents/rules/README.md)
- 서비스 결정: [`docs/services/company/decisions/`](../docs/services/company/decisions/README.md)

## 모듈 규칙
- 패키지 `com.sparta.logistics.company`, 스키마 `company_schema`, DB 계정 `company_app`.
- 상품은 업체의 하위 리소스다. URL과 소유자 검증 모두 업체를 기준으로 한다.
- 수정·삭제는 업체 관리자(`manager_id`) 본인 또는 MASTER만 한다. 서비스 계층에서 `user.validateSelfOrMaster(company.getManagerId())`로 검사한다.
- 업체를 삭제하면 그 업체의 상품도 같은 트랜잭션에서 논리 삭제한다(SA 7.5).

## 다른 모듈이 필요할 때
- 다른 모듈의 코드는 직접 고치지 않는다. 그 서비스의 `docs/services/{service}/README.md` 협업 로그에 `OPEN`으로 요청을 남기고, 답이 필요한 작업이면 멈춘다.
- `common`, `common-core`, `config-repo/application.yml`, 루트 `build.gradle`, `docker-compose.yml` 변경은 모든 서비스에 영향이 가므로 팀 전체 리뷰를 받는다.

## 검증
- `./gradlew :company-service:test` (DB 통합 테스트는 Docker가 켜져 있어야 한다)
- API를 바꿨으면 `script/run.sh company-service`로 다시 띄워 Gateway(8080)로 호출해 본다.
- 에러 코드를 바꿨으면 `script/error-codes-check.sh`
