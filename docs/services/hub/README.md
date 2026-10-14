# hub 협업 창구

허브와 허브 간 경로를 맡는 hub-service의 계약과 현황이다. 이 서비스 코드를 고칠 때 지킬 규칙은 [`hub-service/AGENTS.md`](../../../hub-service/AGENTS.md)에 있다. API의 요청·응답 필드 원본은 [SA](../../SA/sparta-logistics-sa.md) 6장이다.

## 1. 서비스 정보

| 항목 | 값 |
|---|---|
| 담당자 | 미정 |
| 포트 | 8082 |
| 스키마·DB 계정 | `hub_schema` / `hub_app` |
| 책임 | 허브·허브 간 경로 조회(초기 데이터 적재). 재고는 고도화 |
| 소유 테이블 | `p_hubs`, `p_hub_routes` (SA 5.3) |

## 2. 제공 API (계약)

| ID | Method·URL | 권한·호출자 | 상태 | SA와 다른 점·비고 |
|---|---|---|---|---|
| H-01~H-04 | `/api/v1/hubs/**`, `/api/v1/hub-routes/**` | 로그인 | 예정 | 허브 17개·경로 시드는 SA 10.1 |
| H-05~H-06 | 재고 검색·입고 | 로그인, MASTER | 고도화 |  |
| I-02 | `GET /internal/hubs/{hubId}` | company, user | 예정 |  |
| I-03 | `GET /internal/hub-routes?departureHubId=&arrivalHubId=` | delivery | 예정 |  |
| I-04~I-06 | 재고 내부 API | company, order | 고도화 |  |

## 3. 호출하는 다른 서비스 API

없다.

## 4. 구현 현황

- [ ] 허브·경로 Entity와 Flyway 마이그레이션, 초기 데이터
- [ ] H-01~H-04 조회·검색
- [ ] I-02, I-03 내부 API

서비스 결정은 [decisions/](decisions/README.md)에 있다. 재고 관련 결정은 SA v2.0에서 고도화 범위다.

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

아직 기록이 없다.
