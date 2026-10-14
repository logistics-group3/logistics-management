@agent.md

# company-service 세부 규칙

> 루트 `CLAUDE.md`(대전제·공통 규칙)를 전제로 한다. 서비스 정보·API 계약·커뮤니케이션 로그는 위에서 불러온 `agent.md`에 있다.
> 원본: `Docs/reference/SA문서.md`, 루트 `CLAUDE.md` §3-7

## 1. 업체
- 업체는 허브 1곳 소속, 생산·수령 구분 없음.
- C-01: USER는 본인이 관리자, MASTER는 `managerId` 필수. C-04의 `managerId` 변경은 MASTER만.
- 수정·삭제·상품 관리는 업체 관리자(본인) 또는 MASTER → 소유자 검증은 서비스 계층에서.
- 상품이 등록된 업체의 관리 허브 변경 금지 → 409 `HUB_CHANGE_NOT_ALLOWED`.
- 업체 삭제 시 소속 상품도 논리 삭제.

## 2. 상품
- 상품의 `hub_id` = 업체 소속 허브 (상품은 공급업체 소속 허브에만 존재, 루트 §3-7).
- 상품 생성 시 업체 소속 허브에 재고 0개 레코드 생성(I-04, 멱등). 실패 처리 방식은 COMPANY-001.

## 3. 컬럼 길이
- `p_companies`: `name varchar(100)`
- `p_products`: `name varchar(100)`, `description text`, `price bigint`(≥0)
