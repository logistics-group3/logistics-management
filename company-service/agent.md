# company-service agent.md

> 이 서비스의 **협업 창구**다. 사람·AI 모두 이 서비스 코드를 수정하기 전에 루트 `CLAUDE.md`, 이 폴더의 `CLAUDE.md`(세부 규칙), 이 파일을 먼저 읽는다.

## 1. 서비스 정보
| 항목 | 값 |
| --- | --- |
| 담당자 | (미정) |
| 포트 / 스키마 / DB 계정 | 8083 / `company_schema` / `company_app` |
| 책임 | 업체 CRUD, **상품 소유**(업체 하위 리소스) CRUD |
| 소유 테이블 | `p_companies`, `p_products` |

## 2. 제공 API (계약)
- 외부: C-01 업체 생성, C-02 검색, C-03 단건, C-04 수정, C-05 삭제(상품 포함 논리 삭제) / P-01 상품 생성, P-02 검색, P-03 단건, P-04 수정, P-05 삭제
- 내부:
  | API | 호출자 | 요약 |
  | --- | --- | --- |
  | I-07 `GET /internal/companies/{companyId}` | order | companyId, name, hubId, address, managerId |
  | I-08 `GET /internal/products/{productId}` | order, hub | productId, companyId, hubId, name, price |
  | I-14 `GET /internal/companies?managerId=` | delivery | 해당 사용자가 관리하는 companyId 목록(없으면 빈 목록) |
  - ⚠️ I-07/I-08은 주문 생성 Saga 첫 단계에서 쓰인다. 필드 변경 시 로그에 `OPEN`으로 올리고 order·hub 담당자와 합의.

## 3. 의존 (이 서비스가 호출하는 내부 API)
| API | 제공 | 용도 |
| --- | --- | --- |
| I-01 `GET /internal/users/{userId}` | user-service | 업체 관리자(managerId) 존재 확인 |
| I-02 `GET /internal/hubs/{hubId}` | hub-service | 업체 소속 허브 존재 확인 |
| I-04 `POST /internal/stocks` | hub-service | 상품 생성 시 소속 허브에 재고 0개 행 생성 |

## 4. 서비스 고유 규칙
- 세부 규칙은 같은 폴더의 `CLAUDE.md`에 있다 (규칙은 그곳 한 곳에만 둔다, COMMON-005).

## 5. 구현 현황
- [ ] C-01~C-05 (I-01, I-02 Feign)
- [ ] P-01~P-05 (I-04 Feign)
- [ ] I-07, I-08, I-14

## 6. 커뮤니케이션 로그
작성 규칙: 맨 아래에 추가 · ID `COMPANY-{번호}` · 상태 `OPEN` → `DECIDED` → `DONE` · 다른 서비스에 요청할 때는 **그 서비스의 agent.md**에 작성

```
### COMPANY-000 제목
- 상태: OPEN
- 작성: 이름 / YYYY-MM-DD
- 관련: (API ID, 서비스, 파일)
- 내용:
- 결정:
```

### COMPANY-001 상품 생성(P-01) 중 재고 초기화(I-04) 실패 처리
- 상태: OPEN
- 작성: 공통 인프라 / 2026-10-13
- 관련: P-01, I-04, hub-service
- 내용: 상품은 저장됐는데 hub의 I-04 호출이 실패하면 재고 행 없는 상품이 생긴다. 제안: 상품 저장과 같은 로컬 트랜잭션 안에서 I-04를 호출하고, 실패하면 예외로 로컬 롤백(503). I-04는 멱등이라 재시도·중복 호출에 안전하다. (반대로 I-04 성공 후 로컬 커밋이 실패하면 빈 재고 행이 남지만 상품이 없으므로 무해)
- 결정:

### COMPANY-002 I-14 신설 (delivery 조회 권한용)
- 상태: DECIDED
- 작성: 공통 인프라 / 2026-10-13
- 관련: I-14, delivery-service D-01·D-02
- 내용: delivery-service가 "업체 관리자는 본인 업체 관련 배송만 조회" 권한을 판단하려면 사용자가 관리하는 업체 목록이 필요하다.
- 결정: `GET /internal/companies?managerId=` → companyId 목록. SA 문서 I-14에 반영됨.
