# company 구현 노트

## C-01~C-05, P-01~P-05, I-07, I-08 업체·상품 CRUD (2026-10-15)
### 처리 과정
- 마이그레이션·domain
  - `V1__create_p_companies.sql`, `V2__create_p_products.sql`
  - `Company`, `Product`(업체와 `@ManyToOne LAZY`), `CompanyErrorCode`, Repository 인터페이스
  - 가격은 SA대로 `BIGINT`/`Long`이다(결정 0003).
- infrastructure
  - `CompanyJpaRepository`, `ProductJpaRepository`. `search`만 QueryDSL 조각(`*SearchRepositoryImpl`)이 구현한다.
  - `UserClient`(I-01), `HubClient`(I-02)와 Adapter가 `application/UserReader`, `HubReader`를 구현한다.
- application
  - `CompanyService`, `CompanyQueryService`, `ProductService`, `ProductQueryService`
  - Command·Result DTO
- presentation
  - `CompanyController`, `ProductController`, `InternalCompanyController`, `InternalProductController`
  - Request·Response DTO와 Bean Validation
- 요청 흐름(C-01)
  1. Gateway가 사용자 헤더를 넣는다.
  2. `@CurrentUser`로 사용자를 받고, `@Valid`로 요청을 검사한다.
  3. `CompanyService.create`에서 관리자를 결정하고 I-02(허브), MASTER면 I-01(사용자)로 확인한다.
  4. `save`로 저장한다.
  5. `CompanyResponse`를 만들고 201로 응답한다.

### 핵심
- **상품에 허브를 저장하지 않는다.**
  - 상품은 업체의 hubId를 따르고, 검색과 응답에서 업체를 fetch join해 가져온다.
  - 그래서 업체 허브를 바꿔도 상품을 고칠 필요가 없다(SA 5.4).
- **원격 호출은 DB 트랜잭션 밖에서 한다.**
  - C-01은 확인을 다 끝낸 뒤 저장 한 번으로 끝낸다.
  - C-04는 확인 뒤 변경만 `TransactionTemplate`으로 짧게 묶는다. 그사이 업체가 바뀔 수 있어 안에서 다시 조회하고 소유자를 검사한다.
- **소유자 검증은 항상 업체 기준이다.**
  - 상품 수정·삭제도 `product.getCompany().getManagerId()`로 검사한다.
  - 경로의 업체 소속이 아닌 상품은 403이 아니라 404 `PRODUCT_NOT_FOUND`다(SA P-03).
- **C-05는 업체와 소속 상품을 한 트랜잭션에서 논리 삭제한다(SA 7.5).** 하나라도 실패하면 전체가 롤백된다.
- **검색 조건 record는 domain에 둔다.** domain Repository가 받는 타입이라, application에 두면 domain이 바깥 계층을 알게 된다.

### 확인 질문
- 상품 목록은 fetch join과 페이징을 함께 쓰는데, 업체에서 상품 컬렉션을 fetch join하며 페이징하면 왜 문제가 되는가?
- C-04에서 트랜잭션 밖과 안에서 소유자를 두 번 검사한다. 한쪽만 남기면 각각 어떤 문제가 생기는가?
- I-07, I-08은 왜 `ApiResponse`로 감싸지 않는가(ADR 0010)?

### 메모
- hub I-02는 아직 구현되지 않았다(hub 협업 로그 HUB-001). 실제 기동에서 업체 생성·허브 변경은 hub가 구현될 때까지 503이다.
