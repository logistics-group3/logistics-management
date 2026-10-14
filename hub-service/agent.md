# hub-service agent.md

> 이 서비스의 **협업 창구**다. 사람·AI 모두 이 서비스 코드를 수정하기 전에 루트 `CLAUDE.md`, 이 폴더의 `CLAUDE.md`(세부 규칙), 이 파일을 먼저 읽는다.

## 1. 서비스 정보
| 항목 | 값 |
| --- | --- |
| 담당자 | (미정) |
| 포트 / 스키마 / DB 계정 | 8082 / `hub_schema` / `hub_app` |
| 책임 | 허브·허브 간 경로 조회, **상품 재고 소유**(입고·할당·복원) |
| 소유 테이블 | `p_hubs`, `p_hub_routes`, `p_hub_stocks`, `p_stock_allocations` (`p_hub_stock_histories`는 확장 기능으로 제외) |

## 2. 제공 API (계약)
- 외부: H-01 허브 검색, H-02 허브 단건, H-03 경로 검색, H-04 경로 단건, H-06 재고 입고(MASTER). **H-05 허브 재고 검색은 범위 제외**
- 내부:
  | API | 호출자 | 요약 |
  | --- | --- | --- |
  | I-02 `GET /internal/hubs/{hubId}` | company, user | hubId, name, address |
  | I-03 `GET /internal/hub-routes?departureHubId=&arrivalHubId=` | delivery | P2P 단일 경로: hubRouteId, distanceKm, durationMinutes |
  | I-04 `POST /internal/stocks` | company | 상품 생성 시 재고 0개 행 생성. 이미 있으면 기존 값 반환(멱등) |
  | I-05 `POST /internal/stocks/allocate` | order | orderId, productId, quantity → hubId, quantity. 부족 시 409 `STOCK_INSUFFICIENT`. 같은 orderId면 기존 할당 반환(멱등) |
  | I-06 `POST /internal/stocks/restore` | order | orderId → restoredQuantity. 내역 없거나 이미 복원이면 0(멱등) |
  - ⚠️ I-05/I-06은 order-service Saga의 핵심 계약이다. 필드·상태 코드·멱등 동작을 바꾸려면 로그에 `OPEN`으로 올리고 order 담당자와 합의.

## 3. 의존 (이 서비스가 호출하는 내부 API)
| API | 제공 | 용도 |
| --- | --- | --- |
| I-08 `GET /internal/products/{productId}` | company-service | H-06 입고 시 상품 존재·소속 허브 확인 |

## 4. 서비스 고유 규칙
- 세부 규칙은 같은 폴더의 `CLAUDE.md`에 있다 (규칙은 그곳 한 곳에만 둔다, COMMON-005).

## 5. 구현 현황
- [ ] 허브·경로 시드 데이터 (HUB-001)
- [ ] H-01~H-04 조회
- [ ] H-06 입고 (I-08 Feign)
- [ ] I-02, I-03, I-04
- [ ] I-05, I-06 (동시성 테스트 권장)

## 6. 커뮤니케이션 로그
작성 규칙: 맨 아래에 추가 · ID `HUB-{번호}` · 상태 `OPEN` → `DECIDED` → `DONE` · 다른 서비스에 요청할 때는 **그 서비스의 agent.md**에 작성

```
### HUB-000 제목
- 상태: OPEN
- 작성: 이름 / YYYY-MM-DD
- 관련: (API ID, 서비스, 파일)
- 내용:
- 결정:
```

### HUB-001 허브 17개·경로 272개 시드 데이터의 출처와 적재 방식
- 상태: OPEN
- 작성: 공통 인프라 / 2026-10-13
- 관련: `p_hubs`, `p_hub_routes`
- 내용: 허브명·주소·위경도 출처(발제 자료 등), 경로의 거리·소요시간 산출 방식(직선거리 계산 / 고정값), 적재 방식(SQL 스크립트 / 기동 시 코드)이 정해지지 않았다.
- 결정:
