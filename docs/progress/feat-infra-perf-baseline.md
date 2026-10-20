# feat/infra-perf-baseline

## 지금 하는 일
측정 전부 끝남(2026-10-21 00:26). 판정을 측정 기록 README "판정"과 ADR 0019 "후속 결정"(제안)에 적었다. 남은 일은 push·새 PR과 팀 리뷰로 채택 확정

## 한눈에 보기 (2026-10-21 기준)
- 목적: Redis 캐시 후보 A·B·C를 하나씩 적용하며 같은 조건에서 측정해 성능 개선의 객관적 근거를 만든다. D는 2026-10-19 팀 회의에서 기각
- 개발: 끝남(커밋 3개, 테스트·전체 빌드 통과). 캐시 설정 기본값은 모두 지금 동작과 같다
- 측정: 끝남. A0·A2·B1-local·B1-redis·C1을 이 브랜치(046e81b 이후) 이미지로 쟀다. `dev`에는 기준선 뒤 T09 등 서비스 변경이 섞여 있어 `dev`에서 재지 않았다
- 판정(제안): A2 채택, B1-local 채택(redis 기각), C 기각. A 채택 여부가 팀 Compose의 Redis 도입 여부를 정한다
- push·PR: 개발분 PR #26은 2026-10-20 `dev`에 병합됨. 측정 커밋과 판정은 이 브랜치에서 새 PR로 올린다(아직 push 안 함)
- 측정 회차의 커밋 열은 이미지를 만든 코드 기준이다. 046e81b 뒤 측정 커밋은 문서만 바꿔 A0~C1 모두 046e81b 코드로 쟀다
- 측정은 별도 worktree `../base-setting-perf`에서 했다. 원래 작업 트리(`dev`)에 사용자의 미커밋 문서가 있어 건드리지 않으려고 나눴다. 끝나면 `git worktree remove ../base-setting-perf`

| 문서 | 내용 |
|---|---|
| `docs/reviews/performance/README.md` | 범위, 조건표, 측정 절차, 판정 기준, 목표 RPS 근거, 회차 목록(측정할 때마다 자동으로 한 줄 추가) |
| `docs/reviews/performance/<회차>.md` | 회차별 보고서(k6 + Prometheus) |
| `docs/guides/performance-test.md` | 측정 환경 구성과 실행 명령, 캐시 조건 변수 |
| `docs/decisions/0019-redis-cache-measured-adoption.md` | 설계 결정(제안). "후속 결정"에 2026-10-21 판정(제안, 팀 리뷰 대기) |
| `docs/services/{gateway,user,hub,company,common}/README.md` | 협업 로그 GATEWAY-001, USER-003, HUB-002, COMPANY-003, COMMON-001(OPEN, 리뷰 대기) |
| `docs/learning/kmh/qna/2026-10-19-redis-cache-candidates.md`, `...-performance-baseline-plan.md` | 후보 선정과 측정 계획의 배경 |

## 측정 결과

### 개발 전 (기준선, 커밋 a9a6630 이미지)
| 조건 | 시나리오·RPS | p50 / p95 / p99 (ms, 3회) | 실패율 | 항목별 주 지표 |
|---|---|---|---|---|
| A1 Gateway local 60s | S1 150/s | 3.2~3.3 / 4.8~5.3 / 7.3~20.8 | 0% | 요청당 I-01 0.04, 권한 변경 반영 60,180~60,281ms |
| BC0 캐시 없음 | S2 60/s | 11.5~12.0 / 14.9~18.4 / 20.4~73.5 | 0% | 요청당 I-03 0.94~0.95, company 호출 3.00, company 내부 API p95 1.2~1.4ms, hub API p95 1.4~2.0ms |
| BC0 캐시 없음 | S3 200/s | 3.2 / 4.0~4.1 / 18.3~20.4 | 0% | hub API p95 1.5~1.6ms |
| A0 Gateway TTL 0s (046e81b, `5d6f775`) | S1 150/s | 4.4 / 5.2~5.6 / 7.6~82.6 | 0% | 요청당 I-01 1.00, 권한 반영 3~4ms |

### 개발 후 (2026-10-20~21)
| 조건 | 시나리오·RPS | p50 / p95 / p99 (ms, 3회) | 실패율 | 항목별 주 지표 | 커밋 |
|---|---|---|---|---|---|
| A2 Gateway redis 60s | S1 150/s | 3.4 / 5.1~5.6 / 6.6~24.5 | 0% | 요청당 I-01 0.04, 권한 반영 4~6ms | `054b563` |
| B1-local | S2 60/s | 11.0~11.6 / 13.6~14.6 / 15.8~17.4 | 0% | I-03 Feign p95 1.0~1.2ms | `d069507` |
| B1-local | S3 200/s | 2.3 / 2.8~2.9 / 9.9~16.7 | 0% | hub CPU 10.1~10.6%(BC0 20.3~20.6%) | `d069507` |
| B1-redis | S2 60/s | 11.3~11.7 / 14.0~14.7 / 17.1~23.8 | 0% | I-03 Feign p95 1.4~1.7ms | `52eb8fc` |
| B1-redis | S3 200/s | 2.6 / 3.2~3.3 / 8.7~22.5 | 0% | hub CPU 15.1~15.4% | `52eb8fc` |
| C1 hub=local company=redis | S2 60/s | 10.5~11.1 / 13.1~14.2 / 15.0~17.3 | 0% | company CPU 13.2~14.3%(B1-local 15.2~15.4%) | `17d0df4` |

- company·hub 서버 측 API p95는 캐시 조건에서 모두 1.0ms로 나와 비교에 못 쓴다. 1ms 이하 히스토그램 구간 한계로 보인다(미확인). 호출한 쪽 Feign p95와 CPU로 비교했다

### 측정 과정에서 알게 된 것
- 목표 RPS 탐색: S1 한계 200~400(Gateway CPU), S2 한계 80~120(order-service DB 커넥션 대기 최대 186, 120/s 실패율 50%), S3 한계 200~400. 고정 RPS는 S1 150, S2 60, S3 200
- S2 한계가 캐시 대상이 아니라 order DB 커넥션 풀이라 C를 넣어도 최대 처리량은 크게 안 오를 수 있다
- 새로 띄운 직후 첫 회차는 크게 나쁘다(A0 p95 2,340ms → 이후 5.6ms). 그래서 조건마다 예열 3분(버림)을 넣었다. 예열 없는 결과는 `.local/perf/cold-start-protocol-v1/`
- C의 캐시 위치(company-service)는 order → company HTTP 호출 수(주문당 3회)를 줄이지 않는다. C의 주 지표는 company 내부 API 지연·자원이다

## 개발 내용
| 커밋 | 항목 | 바꾼 곳 | 켜는 법(기본) |
|---|---|---|---|
| `f3397e6` | A2 | gateway `RedisUserLookup`·`AuthConfig`·`AuthProperties.userCache`, user `UserService`가 `UserAuthChangedEvent` 발행 → `UserAuthChangedEventListener`(커밋 뒤) → `RedisUserAuthCache`, common-core `UserAuthCacheKeys`(`auth:user:{userId}`) | `AUTH_USER_CACHE=redis`(local). gateway·user 둘 다 같은 값 |
| `3a5af81` | B1 | hub `HubCacheConfig`·`HubCacheProperties`·`HubCacheNames`, `HubQueryService`(H-02, I-02)·`HubRouteQueryService`(H-04, I-03)에 `@Cacheable` | `HUB_CACHE_TYPE=local|redis`(none) |
| `a58e3f1` | C1 | company `CompanyCacheConfig`(트랜잭션 인식 프록시), I-07·I-08 `@Cacheable`, 업체 수정·삭제와 상품 수정·삭제에 `@CacheEvict` | `COMPANY_CACHE_TYPE=local|redis`(none) |

- 설정은 `config-repo/{gateway,user,hub,company}-service.yml`. 각 서비스 `management.health.redis.enabled=false`
- Redis는 팀 `docker-compose.yml`에 넣지 않았다. 측정 환경 `script/perf/compose.perf.yml`에만 있다(`redis:7.4.2`)
- 테스트: common-core 2, gateway 31(`RedisUserLookupTest` 4), user 91(`RedisUserAuthCacheTest` 2, `UserServiceTest` 이벤트 검증), hub 66(`HubCacheIntegrationTest` 2, `HubRedisCacheIntegrationTest` 2), company 54(`CompanyCacheIntegrationTest` 3, `CompanyRedisCacheIntegrationTest` 1). `./gradlew build` 성공(2026-10-20, 2분 49초)
- B·C의 캐시 설정 코드는 두 서비스에 비슷하게 있다. 채택하면 규칙대로 `common` 자동 설정으로 옮긴다(ADR 0019 검토한 대안)

## 진행 상태
- [x] 측정 환경·k6 스크립트·보고서·docs 기록 자동화(`script/perf.sh`, `script/perf/`)
- [x] 목표 RPS 결정, S3 단건 조회로 변경, Redis·캐시 조건 변수 추가, ADR 0019 제안 (`a9a6630`)
- [x] 기준선 A1·BC0 (`a7a92b4`)
- [x] 개발 A2 `f3397e6`, B1 `3a5af81`, C1 `a58e3f1`
- [x] 기준선 A0 3회 (`5d6f775`)
- [x] 개발 후 측정: A2 `054b563`, B1-local `d069507`, B1-redis `52eb8fc`, C1(hub=local) `17d0df4`
- [x] 판정 작성: 측정 기록 README "판정", ADR 0019 "후속 결정"(제안)
- [x] push·PR 생성(2026-10-20, 측정 전 개발분 리뷰용, PR #26 병합됨)
- [ ] 측정 결과 push·새 PR(대상 `dev`)
- [ ] 팀 리뷰로 채택 확정 → ADR 상태 갱신, 채택 항목의 팀 Compose·`.env.example`·기본값 변경, C 코드 제거 여부, 협업 로그 GATEWAY-001 등 정리
- 면접 포인트: 성능 개선을 어떻게 증명했나(기준선, 하나씩 변경, 예열 후 3회 중앙값과 편차), 왜 고정 도착률로 부하를 줬나, Redis 장애를 어떻게 격리했나

## 다음 단계
1. 사용자 확인 후 이 브랜치를 push하고 `dev` 대상 새 PR을 만든다. 본문에 판정표와 "dev가 아닌 이 브랜치 이미지로 잰 이유"를 적는다
2. 팀 리뷰로 A·B·C 채택을 확정하고 ADR 0019 상태를 갱신한다
3. 채택 항목 반영은 별도 작업으로 한다: A2면 팀 `docker-compose.yml`·`.env.example`에 Redis와 `AUTH_USER_CACHE=redis`, B1-local이면 `HUB_CACHE_TYPE` 기본값 local, C 기각이면 company 캐시 코드 제거 여부 결정
4. 측정을 다시 할 때: `.local/perf/batch-measure.sh <a0|a2|b1|c1>`(Git 제외, 이 PC에만 있음. 이번에 `a0)` 묶음을 추가했다). 여유 메모리 8GB 이상에서 시작한다. 이번 측정은 시작 시 10.5GB, 측정 중 3.6GB로 끝까지 돌았다

## 직접 해보기
1. `script/perf.sh down && PERF_HUB_CACHE=redis script/perf.sh up`. 확인: 마지막 줄 `준비 완료: http://127.0.0.1:28080`
2. 데이터가 없으면 `script/perf.sh seed`. 확인: `시드 완료: 허브 17, 경로 272, 수령업체 300, 상품 51 ...`
3. 예열: `RATE=200 DURATION=3m WARMUP=30s PUBLISH=0 PROBE_ROUNDS=0 script/perf.sh run s3 warm-B1-redis-s3`
4. 측정: `RATE=200 script/perf.sh run s3 B1-redis-s3-r1`. 확인: `docs/reviews/performance/B1-redis-s3-r1.md` 생성, README 회차 목록에 캐시 설정 `hub=redis`인 줄 추가
5. Grafana(http://127.0.0.1:23000), Prometheus(http://127.0.0.1:29090)에서 같은 시간대를 본다
6. 끝나면 `script/perf.sh down`

## 메모
- 이 PC에는 Python이 없다(Microsoft Store 별칭만 있음). 그래서 시드·보고서도 k6(Docker)로 만들었다. 기존 `script/ui-test.sh`, `script/api-test.sh`는 Python이 필요하다
- 측정 환경 파일 `.env.perf`(비밀값 자동 생성, Git 제외)와 시드 결과 `.local/perf/data.json`, Postgres 볼륨 `logistics-perf_pgdata`가 남아 있다. `script/perf.sh reset`은 볼륨까지 지운다
- Gateway TTL 덮어쓰기(`AUTH_USERCACHETTL`)는 반영을 확인했다(TTL 0s에서 요청당 I-01 1.00, 권한 반영 6ms)
- Gateway의 I-01 WebClient 호출은 메트릭이 없다. 보고서는 user-service가 받은 I-01 − 다른 서비스의 Feign I-01로 계산한다
- 측정 중 작업 트리의 코드를 바꾸지 않는다. 조건마다 `up --build`로 이미지를 다시 만들어 기준이 섞인다. Gradle도 돌리지 않는다(CPU 간섭)
- TaskStop으로 멈춘 묶음 스크립트가 실제로는 계속 돌아 측정이 겹친 적이 있다(2026-10-19 23:47~00:08). 멈춘 뒤 `ps -ef | grep -E "batch|perf.sh"`와 `docker ps`로 k6 컨테이너가 남았는지 확인한다
- Windows 작업 트리 파일 다수가 CRLF다. perl 한 줄 치환은 줄 끝 `\r` 때문에 안 맞을 수 있어 편집 도구를 쓴다
