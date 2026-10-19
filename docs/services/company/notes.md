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

## #15 P-01 상품 생성과 C-05 업체 삭제의 동시 실행 보호 (2026-10-19)

### 처리 과정
- domain CompanyRepository에 findByIdForUpdate를 선언하고 infrastructure의 Spring Data 저장소가 PESSIMISTIC_WRITE·ID 조건 JPQL로 구현한다. JPQL에는 Entity의 논리 삭제 조건이 적용된다.
- ProductService.create와 CompanyService.delete가 기존 트랜잭션 안에서 같은 업체 잠금을 얻고, 활성 업체·관리자 권한 확인 뒤 생성 또는 삭제한다. 다른 서비스 호출은 추가하지 않는다.
- 상품 생성이 먼저면 삭제가 생성 커밋을 기다리고 새 상품까지 삭제한다. 업체 삭제가 먼저면 생성이 삭제 커밋을 기다렸다가 COMPANY_NOT_FOUND로 거절된다.

### 핵심
- FK는 업체의 물리 행만 확인한다. 논리 삭제 여부와 상품 생성 사이의 경쟁은 업체 행의 쓰기 순서로 보호한다.
- 삭제 경로만 잠그면 생성 경로가 과거 상태를 읽을 수 있으므로 두 경로가 같은 잠금을 사용한다.
- 잠금은 커밋까지 유지한다. 서비스 메서드 내부만이 아니라 트랜잭션 종료 시점까지 포함하는 테스트가 필요하다.
- 같은 업체의 상품 생성은 직렬화되는 비용이 있다. 다른 업체에는 행 단위로 분리되며 일반 조회에는 잠금을 추가하지 않는다.

### 확인 질문
- 논리 삭제된 업체 행이 남아 있으면 상품 FK는 왜 생성을 허용하는가?
- 삭제만 잠그거나 두 경로의 잠금 대상이 다르면 무엇이 보장되지 않는가?

### 메모
- 사용자 확인 범위의 로컬 수정이며 COMPANY-001이나 다른 서비스의 정책을 바꾸지 않았다.
- 테스트와 최종 PR 상태는 [구현·검증 기록](../../reviews/2026-10-19-issue-15-verification.md)을 따른다.
