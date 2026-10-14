# delivery-service agent.md

> 이 서비스의 **협업 창구**다. 사람·AI 모두 이 서비스 코드를 수정하기 전에 루트 `CLAUDE.md`와 이 파일을 먼저 읽는다.

## 1. 서비스 정보
| 항목 | 값 |
| --- | --- |
| 담당자 | (미정) |
| 포트 / 스키마 / DB 계정 | 8085 / `delivery_schema` / `delivery_app` |
| 책임 | 업체 간 배송·허브 이동 구간 생성, 담당자 배정, 상태 관리 |
| 소유 테이블 | `p_deliveries`, `p_delivery_routes` |

## 2. 제공 API (계약)
- 외부: D-01 검색, D-02 단건(허브 이동 구간 포함), D-03 배송지·수령인 수정, D-04 업체 배송 담당자 배정(MASTER, PATCH), D-05 업체 배송 상태 변경, D-06 허브 이동 담당자 배정(MASTER, PATCH), D-07 허브 이동 상태 변경
- 내부 (호출자: 모두 **order-service**):
  | API | 요약 |
  | --- | --- |
  | I-09 `POST /internal/deliveries` | orderId, supplier/receiverCompanyId, departure/destinationHubId, deliveryAddress, recipientName → deliveryId, status. 같은 orderId면 기존 값 반환(멱등) |
  | I-10 `GET /internal/deliveries/{deliveryId}` | deliveryId, orderId, status |
  | I-11 `POST /internal/deliveries/{deliveryId}/cancel` | `CANCELED`. 취소 불가 상태면 409 |
  | I-12 `POST /internal/deliveries/{deliveryId}/cancel-revert` | 취소 전 상태로 원복 |
  | I-13 `DELETE /internal/deliveries/orders/{orderId}` | 배송·구간 논리 삭제, 대상 없어도 204(멱등) |
  - ⚠️ 모두 order-service Saga 계약이다. 변경 시 로그에 `OPEN`으로 올리고 order 담당자와 합의.

## 3. 의존 (이 서비스가 호출하는 내부 API)
| API | 제공 | 용도 |
| --- | --- | --- |
| I-03 `GET /internal/hub-routes` | hub-service | I-09에서 두 허브가 다를 때 예상 거리·시간 (read 2초, I-09보다 짧게) |
| I-01 `GET /internal/users/{userId}` | user-service | D-04·D-06 배정 시 배송담당자 여부·소속 허브 확인 |
| I-14 `GET /internal/companies?managerId=` | company-service | D-01·D-02 업체 관리자 조회 권한 판단 |

## 4. 서비스 고유 규칙 (원본: SA §5 배송 상태 전이, CLAUDE.md §7)
- 흐름: 공급업체 허브 → (허브 이동 0~1구간) → 요청업체 허브 → 요청업체. 같은 허브면 구간 0개, 생성 즉시 `DEST_HUB_ARRIVED`.
- 배송 상태: `HUB_WAITING` → `HUB_MOVING` → `DEST_HUB_ARRIVED` → `OUT_FOR_DELIVERY` → `DELIVERED` / `CANCELED`. 구간 상태(`WAITING`/`MOVING`/`ARRIVED`/`CANCELED`)와 1:1 자동 연동.
- 전제 조건: `MOVING`은 허브 이동 담당자 배정 필수, `OUT_FOR_DELIVERY`는 `DEST_HUB_ARRIVED` + 업체 배송 담당자 필수. 출발·완료 시각 기록.
- 취소는 `HUB_WAITING` 또는 이동 없이 시작한 `DEST_HUB_ARRIVED`만. 허브 이동을 마친 배송은 취소 불가.
- 상태 전이 검증은 Entity 메서드에. 위반 시 409 `INVALID_STATUS_TRANSITION`.
- 배정 규칙: 출발지 허브 소속 배송담당자만 (허브 이동 → 공급업체 허브, 업체 배송 → 요청업체 허브). 없음 404 `DELIVERY_MANAGER_NOT_FOUND`, 불일치 400 `MANAGER_HUB_MISMATCH`.
- 조회 권한: MASTER 전체 / 배송담당자 본인 담당 / 업체 관리자 본인 업체 관련(I-14).

## 5. 구현 현황
- [ ] 엔티티 + 상태 전이 메서드
- [ ] I-09~I-13 (멱등)
- [ ] D-01~D-07
- [ ] FeignClient (hub I-03, user I-01, company I-14)

## 6. 커뮤니케이션 로그
작성 규칙: 맨 아래에 추가 · ID `DELIVERY-{번호}` · 상태 `OPEN` → `DECIDED` → `DONE` · 다른 서비스에 요청할 때는 **그 서비스의 agent.md**에 작성

```
### DELIVERY-000 제목
- 상태: OPEN
- 작성: 이름 / YYYY-MM-DD
- 관련: (API ID, 서비스, 파일)
- 내용:
- 결정:
```

### DELIVERY-001 I-12 취소 원복 시 "취소 전 상태" 보관 방식
- 상태: OPEN
- 작성: 공통 인프라 / 2026-10-13
- 관련: I-11, I-12, order-service ORDER-001
- 내용: 취소 가능한 상태가 `HUB_WAITING`과 `DEST_HUB_ARRIVED` 두 가지라, 원복하려면 취소 전 상태를 알아야 한다. 제안: 구간 존재 여부로 복원(구간 있으면 `HUB_WAITING`·구간 `WAITING`, 없으면 `DEST_HUB_ARRIVED`) — 별도 컬럼 불필요.
- 결정:
