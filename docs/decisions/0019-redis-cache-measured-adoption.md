# 0019. Redis 캐시를 기본 꺼짐으로 넣고 측정으로 채택을 정한다

- 상태: 제안 (측정 결과와 팀 리뷰 후 항목별로 채택 여부를 정한다)
- 날짜: 2026-10-19
- 결정자: kmh (D 기각은 2026-10-19 팀 회의)

## 맥락
- SA는 Redis 캐싱을 고도화 선택 항목으로 둔다. 근거 없이 넣으면 운영 요소(컨테이너, 장애 지점)만 늘어난다.
- 후보 4개 중 D(배송담당자 목록)는 팀 회의에서 기각됐다. A(Gateway 권한 재확인), B(허브·경로), C(업체·상품)를 측정으로 비교한다.
- 비교하려면 같은 이미지에서 캐시를 켜고 끌 수 있어야 한다.

## 결정
1. 캐시는 설정으로 켜고 끈다. 기본값은 지금 동작과 같다. 그래서 이 변경만으로는 팀 실행 환경의 동작이 바뀌지 않는다.

   | 항목 | 설정(환경 변수) | 값 | 기본 |
   |---|---|---|---|
   | A | `auth.user-cache`(gateway), `user.auth-cache.type`(user) ← `AUTH_USER_CACHE` | local, redis | local(Caffeine 60초) |
   | B | `hub.cache.type` ← `HUB_CACHE_TYPE` | none, local, redis | none |
   | C | `company.cache.type` ← `COMPANY_CACHE_TYPE` | none, local, redis | none |

2. 캐시는 데이터를 가진 서비스에 둔다. A는 Gateway가 저장하고 user-service가 권한 변경(U-07)·탈퇴(U-08) 커밋 뒤 키를 지운다. B는 hub-service, C는 company-service의 조회 메서드에 둔다. C를 order 쪽에 두면 company의 수정·삭제를 order 캐시에 알릴 방법이 따로 필요해 수정 범위가 커진다(kmh 결정: 수정 범위가 가장 작은 곳).
3. A의 키 규칙(`auth:user:{userId}`)은 저장하는 쪽과 지우는 쪽이 달라 `common-core`의 `UserAuthCacheKeys`에 둔다.
4. Redis 장애는 성능 저하로만 끝나게 한다. A는 Redis 조회·저장이 실패하면 I-01을 직접 부른다. user-service의 키 삭제 실패는 이미 커밋한 변경을 되돌리지 않고 TTL로 반영된다.
5. Redis에는 값 타입을 고정한 JSON 직렬화기를 쓴다. JSON에 클래스 이름을 넣는 기본 타입 지정은 쓰지 않는다.
6. `docker-compose.yml`에는 Redis를 넣지 않는다. 측정 환경(`script/perf/compose.perf.yml`)에만 띄운다. 채택하는 항목이 생기면 그때 팀 Compose와 `.env.example`에 추가한다.

## 검토한 대안
- 캐시 코드를 측정마다 따로 브랜치로 두기: 조건마다 이미지가 달라져 비교 조건이 흔들린다.
- C를 order-service에 두기: HTTP 호출 수까지 줄지만 무효화를 두 서비스에 걸쳐 만들어야 한다.
- B·C 캐시 설정을 `common` 자동 설정으로 두기: 두 서비스가 같은 코드를 쓰므로 규칙상 옮길 대상이다. 채택이 정해지지 않은 실험 코드라 지금은 서비스마다 두고, 채택하면 `common`으로 옮긴다.

## 결과
- 측정 결과와 판정은 [캐시 도입 성능 측정 기록](../reviews/performance/README.md)에 쌓는다. 항목별로 채택·기각을 이 ADR의 후속 결정으로 적는다.
- Redis 의존성(spring-boot-starter-data-redis)이 gateway·user·hub·company에 들어간다. 기본 설정에서는 연결하지 않고, Redis health 검사는 끈다.
- B는 쓰기 API가 없어 TTL(기본 10분)로만 갱신된다. 허브·경로를 마이그레이션으로 바꾸면 TTL이 지나야 Redis 캐시에 반영된다.

## 후속 결정
### 2026-10-21 측정 결과에 따른 판정 (제안, 팀 리뷰 대기)
근거: [캐시 도입 성능 측정 기록 "판정"](../reviews/performance/README.md#판정-2026-10-21). A1·BC0은 a9a6630 이미지, 나머지는 046e81b 이후 이미지로 측정했다. 캐시 기본 설정은 개발 전 동작과 같다.

| 항목 | 판정 | 근거 요약 | 채택하면 바꿀 것 |
|---|---|---|---|
| A | A2(redis) 채택 제안 | I-01 감소는 A1과 같고 권한 변경 반영이 60초 → 4~6ms. p50 +0.1~0.2ms | 팀 Compose·`.env.example`에 Redis와 `AUTH_USER_CACHE=redis` 추가 |
| B | B1-local 채택 제안, redis 기각 | S3 p50 3.2 → 2.3ms, hub CPU 20% → 10%. redis는 local보다 0.3ms 느림 | `HUB_CACHE_TYPE` 기본값 local |
| C | 기각 제안 | S2 끝단 차이가 3회 편차 안. company 쪽 지연·CPU만 소폭 감소 | 없음. 설정은 none 유지, 코드 제거 여부는 리뷰에서 정한다 |

- A를 기각하면 Redis가 필요한 항목이 없어 팀 Compose에 Redis를 넣지 않는다. A의 채택 여부가 Redis 도입 여부를 정한다.
- C를 제거하면 B의 캐시 설정 코드는 hub 한 곳에만 남아 `common` 이동 대상이 아니다. C 코드 제거 여부와 함께 정한다.
- 로컬 Docker의 상대 비교다. 운영 규모의 부하에서는 C의 이득이 커질 수 있다(미측정).
