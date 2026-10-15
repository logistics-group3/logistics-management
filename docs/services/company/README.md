# company 협업 창구

업체와 상품을 맡는 company-service의 계약과 현황이다. 이 서비스 코드를 고칠 때 지킬 규칙은 [`company-service/AGENTS.md`](../../../company-service/AGENTS.md)에 있다. API의 요청·응답 필드 원본은 [SA](../../SA/sparta-logistics-sa.md) 6장이다.

## 1. 서비스 정보

| 항목 | 값 |
|---|---|
| 담당자 | 미정 |
| 포트 | 8083 |
| 스키마·DB 계정 | `company_schema` / `company_app` |
| 책임 | 업체 CRUD, 상품(업체 하위 리소스) CRUD |
| 소유 테이블 | `p_companies`, `p_products` (SA 5.4) |

## 2. 제공 API (계약)

| ID | Method·URL | 권한·호출자 | 상태 | SA와 다른 점·비고 |
|---|---|---|---|---|
| C-01~C-05 | `/api/v1/companies/**` | 로그인, 업체 관리자, MASTER | 예정 |  |
| P-01~P-05 | `/api/v1/products`, `/api/v1/companies/{companyId}/products/**` | 로그인, 업체 관리자, MASTER | 예정 |  |
| I-07 | `GET /internal/companies/{companyId}` | order | 예정 |  |
| I-08 | `GET /internal/products/{productId}` | order | 예정 |  |

I-07·I-08 구현 전 [주문 연동 실패 계약](../order/integration-contract.md#company-담당자)을 확인한다. 현재 주문은 내부 DTO 직접 응답, 정수 원화 가격, 공통 4xx 오류 코드와 통신 재시도 정책을 사용한다. 실제 팀 서비스 연동은 아래 COMPANY-001에서 확인한다.

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-01 `GET /internal/users/{userId}` | user | 업체 관리자 지정 시 사용자 확인 | GET이라 가능 |
| I-02 `GET /internal/hubs/{hubId}` | hub | 소속 허브 확인 | GET이라 가능 |

## 4. 구현 현황

- [ ] 업체·상품 Entity와 Flyway 마이그레이션
- [ ] C-01~C-05, P-01~P-05
- [ ] I-07, I-08 내부 API

서비스 결정은 [decisions/](decisions/README.md)에 있다.

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

### COMPANY-001 주문 연동용 조회 응답과 오류 계약 확인

- 상태: OPEN
- 작성: order / Junhyun An 작업 / 2026-10-15 (사용자의 타 서비스 인계 기록 요청)
- 관련: I-07·I-08, O-01 생성 전 업체·상품·권한 검증
- 내용: [연동 계약](../order/integration-contract.md)의 응답 필드·가격 타입·4xx 코드·헤더/Audit 전제를 구현 시 확인한다. 주문은 GET 통신 실패에 최대 3회 호출하며 조회 실패 시 배송을 만들지 않는다. 현재 검증은 HTTP 대역이고 실제 구현·업무 데이터 연동은 미확인이다.
- 요청: 제공 API가 준비되면 구현 위치와 실제 연동 결과를 남긴다. 기존 SA 계약의 구현 확인 요청이며 새 응답 필드를 임의로 추가하라는 요청은 아니다.
- 결정: 담당자 확인 대기. order 작성만으로 DECIDED·DONE 처리하지 않는다.
