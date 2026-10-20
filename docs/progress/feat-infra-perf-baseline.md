# feat/infra-perf-baseline

## 지금 하는 일
캐시 A2·B1·C1 개발 완료, 개발 후 측정(A0 재측정 포함) 대기

## 한눈에 보기 (2026-10-20 기준)
- 목적: Redis 캐시 후보 A·B·C를 하나씩 적용하며 같은 조건에서 측정해 성능 개선의 객관적 근거를 만든다. D는 2026-10-19 팀 회의에서 기각
- 개발: 끝남(커밋 3개, 테스트·전체 빌드 통과). 캐시 설정 기본값은 모두 지금 동작과 같다
- 측정: 개발 전 기준선 A1·BC0만 끝남. A0과 개발 후 A2·B1·C1은 PC 메모리 부족으로 멈춤
- push·PR: 하지 않음. 사용자가 "개발과 측정이 끝난 뒤" 하기로 했다

| 문서 | 내용 |
|---|---|
| `docs/reviews/performance/README.md` | 범위, 조건표, 측정 절차, 판정 기준, 목표 RPS 근거, 회차 목록(측정할 때마다 자동으로 한 줄 추가) |
| `docs/reviews/performance/<회차>.md` | 회차별 보고서(k6 + Prometheus) |
| `docs/guides/performance-test.md` | 측정 환경 구성과 실행 명령, 캐시 조건 변수 |
| `docs/decisions/0019-redis-cache-measured-adoption.md` | 설계 결정(제안). "후속 결정"은 측정 후 작성 |
| `docs/services/{gateway,user,hub,company,common}/README.md` | 협업 로그 GATEWAY-001, USER-003, HUB-002, COMPANY-003, COMMON-001(OPEN, 리뷰 대기) |
| `docs/learning/kmh/qna/2026-10-19-redis-cache-candidates.md`, `...-performance-baseline-plan.md` | 후보 선정과 측정 계획의 배경 |

## 측정 결과

### 개발 전 (기준선, 커밋 a9a6630 이미지)
| 조건 | 시나리오·RPS | p50 / p95 / p99 (ms, 3회) | 실패율 | 항목별 주 지표 |
|---|---|---|---|---|
| A1 Gateway local 60s | S1 150/s | 3.2~3.3 / 4.8~5.3 / 7.3~20.8 | 0% | 요청당 I-01 0.04, 권한 변경 반영 60,180~60,281ms |
| BC0 캐시 없음 | S2 60/s | 11.5~12.0 / 14.9~18.4 / 20.4~73.5 | 0% | 요청당 I-03 0.94~0.95, company 호출 3.00, company 내부 API p95 1.2~1.4ms, hub API p95 1.4~2.0ms |
| BC0 캐시 없음 | S3 200/s | 3.2 / 4.0~4.1 / 18.3~20.4 | 0% | hub API p95 1.5~1.6ms |
| A0 Gateway TTL 0s | S1 150/s | 미완료(r1만: 4.6 / 6.1 / 87.2, 요청당 I-01 1.00, 권한 반영 6ms) | - | `.local/perf/a0-incomplete/` |

### 개발 후
아직 없다. 남은 조건: A0(기준선), A2, B1-local, B1-redis, C1.

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
- [ ] 기준선 A0 3회
- [ ] 개발 후 측정: A2(S1), B1-local·B1-redis(S2, S3), C1(S2, B1에서 나은 쪽을 켠 상태)
- [ ] ADR 0019 후속 결정, 채택 항목의 팀 Compose·`.env.example` 변경안, 협업 로그 리뷰
- [ ] push·PR(사용자가 측정 완료 후 진행하기로 함)
- 면접 포인트: 성능 개선을 어떻게 증명했나(기준선, 하나씩 변경, 예열 후 3회 중앙값과 편차), 왜 고정 도착률로 부하를 줬나, Redis 장애를 어떻게 격리했나

## 다음 단계
1. 메모리를 확보한다. IDE·브라우저·개발용 `redis-stack-compose` 컨테이너 등을 끈다. 측정 환경은 컨테이너 합계 약 3GB를 쓴다
2. `.local/perf/batch-measure.sh`(Git 제외, 이 PC에만 있음)로 조건별 측정을 돌린다. 조건마다 down → up → 30초 대기 → 예열 3분(버림) → 5분×3회. 결과는 `.local/perf/batch-<묶음>.txt`에 시간순으로 남는다
   - A0: 스크립트에 `a0)` 묶음이 없다. `baseline` 묶음의 A0 부분을 떼어 `a0)` 묶음을 추가하거나 `.local/perf/batch-redo-a0.sh`의 PID 대기 줄을 지우고 쓴다
   - A2: `.local/perf/batch-measure.sh a2`
   - B1: `.local/perf/batch-measure.sh b1`
   - C1: `B1_CHOICE=<local|redis> .local/perf/batch-measure.sh c1`
   - 묶음 하나가 1~2시간 걸린다. 백그라운드로 돌리면 세션이 오래 쉬는 동안 Claude Code가 메모리 부족 시 작업을 멈출 수 있다
3. 조건별로 끝날 때마다 `docs/reviews/performance/`의 새 보고서와 목록을 측정 커밋(`test(infra): ...`)으로 남긴다. 개발 커밋과 측정 커밋은 나눈다(사용자 요청)
4. 회차 목록으로 판정한다(개선 폭이 같은 조건 3회 편차보다 작으면 차이 없음). ADR 0019 "후속 결정"에 항목별 채택·기각을 적는다

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
