# delivery 협업 창구

배송을 맡는 delivery-service의 계약과 현황이다. 이 서비스 코드를 고칠 때 지킬 규칙은 [`delivery-service/AGENTS.md`](../../../delivery-service/AGENTS.md)에 있다. API의 요청·응답 필드 원본은 [SA](../../SA/sparta-logistics-sa.md) 6장이다.

## 1. 서비스 정보

| 항목 | 값 |
|---|---|
| 담당자 | 미정 |
| 포트 | 8085 |
| 스키마·DB 계정 | `delivery_schema` / `delivery_app` |
| 책임 | 배송·배송 구간(집하·허브 이동·업체 배송) 생성, 담당자 자동 배정·재배정, 상태 관리 |
| 소유 테이블 | `p_deliveries`, `p_delivery_routes` (SA 5.6) |

## 2. 제공 API (계약)

| ID | Method·URL | 권한·호출자 | 상태 | SA와 다른 점·비고 |
|---|---|---|---|---|
| D-01~D-05 | `/api/v1/deliveries/**` | 로그인, 구간 담당자, MASTER | 예정 |  |
| I-09~I-13 | `/internal/deliveries/**` | order | 예정 | 배송 생성·삭제는 내부 API로만 제공(SA 6.2) |

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-01 `GET /internal/users/{userId}` | user | 재배정 대상 확인 | GET이라 가능 |
| I-03 `GET /internal/hub-routes` | hub | 허브 이동 구간 정보 | GET이라 가능 |
| I-14 `GET /internal/users/delivery-managers?hubId=` | user | 담당자 자동 배정 후보 | GET이라 가능 |

## 4. 구현 현황

- [x] 배송·구간 Entity와 Flyway 마이그레이션(V1, 2026-10-15). 상태 전이 메서드는 진행 중
- [ ] I-09 배송 생성과 담당자 자동 배정
- [ ] D-01~D-05, I-10~I-13

서비스 결정은 [decisions/](decisions/README.md)에 있다.

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

아직 기록이 없다.
