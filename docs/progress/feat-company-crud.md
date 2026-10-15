# feat/company-crud

## 지금 하는 일
company-service 3단계(application) 완료, 4단계 presentation 시작 전

## 진행 상태
- [x] 결정 확정: SA v2.0 기준, company 결정 0001·0002는 이번 범위 제외, 가격은 BIGINT/Long(company 결정 0003)
- [x] 마이그레이션 `V1__create_p_companies.sql`, `V2__create_p_products.sql`
- [x] domain: `Company`, `Product`(업체와 `@ManyToOne LAZY`), `CompanyErrorCode`, `CompanyRepository`, `ProductRepository`
- [x] 도메인 단위 테스트 4개 통과(`./gradlew :company-service:test`), `script/error-codes-check.sh` PASS, `script/docs-check.sh` OK
- [x] `CompanySchemaIntegrationTest`(Flyway + validate, 가격 CHECK, FK) 3개 통과. Docker 29.8.2, 전체 7개 통과
- [x] 1단계 커밋 `2902545`
- [x] 2단계 infrastructure
  - persistence: `CompanyJpaRepository`·`ProductJpaRepository` + QueryDSL 검색 조각(`*SearchRepositoryImpl`)
  - 검색 조건 `CompanySearchCondition`·`ProductSearchCondition`은 domain에 둠(domain은 다른 계층을 모른다)
  - client: `UserClient`(I-01)·`HubClient`(I-02) + Adapter, `application/UserReader`·`HubReader`
  - `CompanySearchIntegrationTest` 5개 포함 전체 12개 통과, docs-check OK, error-codes-check PASS
  - hub 협업 로그에 HUB-001(I-02 계약·일정 확인) OPEN
- [x] 2단계 커밋 `700bc06`
- [x] 3단계 application
  - `CompanyService`(C-01·C-04·C-05), `CompanyQueryService`(C-02·C-03·I-07), `ProductService`(P-01·P-04·P-05), `ProductQueryService`(P-02·P-03·I-08)
  - Command·Result DTO 8개(`application/dto`)
  - C-01·C-04는 원격 호출(I-01·I-02)을 트랜잭션 밖에서 하고, C-04 변경은 `TransactionTemplate`로 짧게 묶고 안에서 다시 조회·소유자 검사
  - MASTER가 C-01에서 managerId를 비우면 400 `VALIDATION_ERROR`(메시지 지정), USER가 남을 지정하면 403
  - 서비스 테스트 25개 추가, 전체 37개 통과
- [ ] 4단계 presentation, 5단계 문서 동기화(notes.md, README 상태 표)
- 면접 포인트: 상품에 hub_id를 두지 않고 업체를 따르게 한 이유, 서비스 간 참조는 FK 없이 인덱스만 두는 이유

## 다음 단계
1. presentation 단계(`docs/agents/rules/controller.md`): `CompanyController`, `ProductController`, `InternalCompanyController`(I-07), `InternalProductController`(I-08), Request·Response DTO와 `@Valid` 검증(SA 8.5), 컨트롤러 테스트.
   - 생성은 201과 "업체가 생성되었습니다." / "상품이 생성되었습니다.", 삭제는 200과 `data: null`.
   - 내부 API는 `ApiResponse` 없이 DTO를 그대로 반환(ADR 0010).
2. HUB-001 답을 확인한다. hub I-02가 없으면 Gateway 통합 확인 시 업체 생성이 503이 된다.

## 직접 해보기
1. `company-service/src/main/resources/db/migration/`의 SQL과 `domain/Company.java`의 `@Column`을 나란히 놓고 이름, 길이, nullable이 같은지 확인한다.
2. Docker를 켜고 `./gradlew :company-service:test --tests '*CompanySchemaIntegrationTest'`를 실행한다.
3. `V2`의 `CHECK (price >= 0)`를 지우면 어떤 테스트가 실패하는지 확인한다. 확인 뒤에는 되돌린다.

## 메모
- 설계 기준: `docs/SA/sparta-logistics-sa.md` v2.0.
- 가격 타입이 규칙 문서(domain.md, migration.md)와 다르다: `docs/services/company/decisions/0003-price-bigint.md`
- 커밋 규칙: `docs/guides/git-workflow.md`. AI 공동 작성자 표기는 넣지 않는다.
