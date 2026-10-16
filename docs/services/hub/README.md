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
| H-01~H-04 | `/api/v1/hubs/**`, `/api/v1/hub-routes/**` | 로그인 | 구현 | 허브 17개(V2)·경로 272건(V3)은 SQL 시드. 경로 시드는 SA 10.1의 기동 시 러너 대신 값을 고정한 마이그레이션이다. H-03·H-04는 Gateway 경유 호출로 확인했고(2026-10-16) H-01·H-02는 미확인 |
| H-05~H-06 | 재고 검색·입고 | 로그인, MASTER | 고도화 |  |
| I-02 | `GET /internal/hubs/{hubId}` | company, user | 구현 | 응답 `hubId, name, address`(DTO 그대로), 없으면 404 `HUB_NOT_FOUND`. `/internal/**`은 Gateway에 라우팅이 없어 외부 호출이 404다. 서비스 간 호출은 미확인 |
| I-03 | `GET /internal/hub-routes?departureHubId=&arrivalHubId=` | delivery | 예정 |  |
| I-04~I-06 | 재고 내부 API | company, order | 고도화 |  |

## 3. 호출하는 다른 서비스 API

없다.

## 4. 구현 현황

- [x] 허브·경로 Entity와 Flyway 마이그레이션, 초기 데이터(V1~V3)
- [x] H-01~H-04 조회·검색
- [x] I-02 내부 API
- [ ] I-03 내부 API

서비스 결정은 [decisions/](decisions/README.md)에 있다. 재고 관련 결정은 SA v2.0에서 고도화 범위다.

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

### HUB-001 I-02 허브 조회 구현 일정과 404 코드 확인
- 상태: OPEN
- 작성: company / kej-2356 / 2026-10-15
- 관련: I-02, company C-01·C-04(업체 생성·수정 시 허브 존재 확인)
- 내용: company-service가 `HubClient`로 `GET /internal/hubs/{hubId}`를 호출하도록 만들었다. SA 6.3대로 응답은 감싸지 않은 `hubId, name, address`이고, 없는 허브는 404 `HUB_NOT_FOUND`라고 가정했다. 계약이 다르면 알려 주고, 구현 일정을 공유해 주면 그때 Gateway로 통합 확인한다.

### HUB-001 I-03 허브 간 경로 조회 구현과 응답 확인 요청
- 상태: OPEN
- 작성: delivery / 한얼 / 2026-10-15
- 관련: I-03, SA 6.3, delivery I-09 배송 생성
- 내용: delivery는 배송 생성(I-09) 때 출발 허브와 목적지 허브가 다르면 I-03을 호출해 허브 이동 구간의 예상 거리·시간을 채운다. delivery 쪽 Feign은 `GET /internal/hub-routes?departureHubId=&arrivalHubId=` → `{ hubRouteId, distanceKm, durationMinutes }`(DTO 그대로, ADR 0010)로 구현했다. I-03이 아직 없어 I-09가 실제 연동에서는 실패한다. (1) 구현 일정 (2) 경로가 없을 때 에러 코드 이름(delivery는 받은 에러를 그대로 order에 전달한다)을 알려 달라.
- 결정:
