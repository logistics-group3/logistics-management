# feat/company-crud

## 지금 하는 일
company-service 1단계(마이그레이션·domain) 완료, 2단계 infrastructure 시작 전

## 진행 상태
- [x] 결정 확정: SA v2.0 기준, company 결정 0001·0002는 이번 범위 제외, 가격은 BIGINT/Long(company 결정 0003)
- [x] 마이그레이션 `V1__create_p_companies.sql`, `V2__create_p_products.sql`
- [x] domain: `Company`, `Product`(업체와 `@ManyToOne LAZY`), `CompanyErrorCode`, `CompanyRepository`, `ProductRepository`
- [x] 도메인 단위 테스트 4개 통과(`./gradlew :company-service:test`), `script/error-codes-check.sh` PASS, `script/docs-check.sh` OK
- [x] `CompanySchemaIntegrationTest`(Flyway + validate, 가격 CHECK, FK) 3개 통과. Docker 29.8.2, 전체 7개 통과
- [ ] 2단계 infrastructure: JpaRepository, QueryDSL 검색, UserClient(I-01), HubClient(I-02)
- [ ] 3단계 application, 4단계 presentation, 5단계 문서 동기화
- 면접 포인트: 상품에 hub_id를 두지 않고 업체를 따르게 한 이유, 서비스 간 참조는 FK 없이 인덱스만 두는 이유

## 다음 단계
1. infrastructure 단계를 시작한다. `docs/agents/rules/repository.md`, `feign.md`를 따른다.
   - 검색 조건 타입(`CompanySearchCondition`)을 domain Repository가 받도록 할지 위치를 정한다. application DTO를 domain에서 import하면 계층 방향이 어긋난다.
2. hub I-02가 아직 없다. `docs/services/hub/README.md` 협업 로그에 `OPEN` 항목으로 응답 형식(hubId, name, address)을 요청한다.

## 직접 해보기
1. `company-service/src/main/resources/db/migration/`의 SQL과 `domain/Company.java`의 `@Column`을 나란히 놓고 이름, 길이, nullable이 같은지 확인한다.
2. Docker를 켜고 `./gradlew :company-service:test --tests '*CompanySchemaIntegrationTest'`를 실행한다.
3. `V2`의 `CHECK (price >= 0)`를 지우면 어떤 테스트가 실패하는지 확인한다. 확인 뒤에는 되돌린다.

## 메모
- 설계 기준: `docs/SA/sparta-logistics-sa.md` v2.0. `docs/SA.md`(v1.2)는 구버전이다.
- 가격 타입이 규칙 문서(domain.md, migration.md)와 다르다: `docs/services/company/decisions/0003-price-bigint.md`
- 커밋 규칙: `docs/guides/git-workflow.md`. AI 공동 작성자 표기는 넣지 않는다.
