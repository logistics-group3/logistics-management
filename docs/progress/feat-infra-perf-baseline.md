# feat/infra-perf-baseline

## 지금 하는 일
캐시 도입 전 성능 측정 환경(k6, 전용 Compose) 구성과 시험 실행

## 진행 상태
- [x] 측정 범위 확정: A(Gateway I-01), B(허브·경로), C(업체·상품). D(배송담당자 목록)는 2026-10-19 팀 회의에서 기각
- [x] 결정(2026-10-19 kmh): B는 로컬 캐시와 Redis를 모두 비교한다. C는 수정 범위가 가장 작은 company-service 내부 조회 API에 둔다. 결과는 `.local/perf`와 `docs/reviews/performance/`에 함께 남긴다
- [x] 측정 환경: `script/perf.sh`, `script/perf/compose.perf.yml`(프로젝트 `logistics-perf`, `.env.perf`, 컨테이너당 1 CPU·768MB, 추적 0.0, Slack 끔, `PERF_AUTH_CACHE_TTL`)
- [x] k6 스크립트: `seed.js`, `s1-auth.js`(권한 변경 반영 시간 포함), `s2-order.js`, `s3-read.js`, `report.js`. 이미지 `grafana/k6:1.3.0`
- [x] `script/perf.sh up` 성공(2026-10-19, Gateway 28080 로그인 401 응답 확인), `script/perf.sh seed` 성공(허브 17, 가입 요청 354, 배송담당자 지정 34, 업체 317, 상품 51)
- [x] 기록 위치: `docs/reviews/performance/README.md`(범위·조건·판정 기준·회차 목록), `docs/guides/performance-test.md`(절차), `docs/README.md`·`docs/doc-map.tsv` 연결. `script/docs-check.sh` OK
- [x] 시험 실행(2026-10-19, 모두 실패율 0%, dropped 0). 결과 원본은 `.local/perf/results/trial-*`
  - S2 5/s 1m: p95 57.0ms, 요청당 I-01 0.59, I-03 0.96, order→company 2.99, company 내부 API p95 2.1ms
  - S1 10/s 3m, TTL 60s: p95 12.1ms, 요청당 I-01 0.38, 권한 변경 반영 60,361ms(1회)
  - S1 10/s 2m, TTL 0s: p95 13.9ms, 요청당 I-01 1.00. `AUTH_USERCACHETTL` 덮어쓰기 반영 확인
  - S3 10/s 1m: p95 11.4ms
  - publish: `docs/reviews/performance/`에 보고서와 목록 한 줄이 생기는 것을 확인한 뒤 확인용 기록은 지웠다
- [x] 시험 실행 중 고친 것: Gateway `http_client_requests`는 라우트 프록시 지표라 I-01을 user-service 수신 수 − Feign I-01로 계산, Feign URL에 `/internal`이 없는 점 반영, company 내부 API p95 계산 오류 수정, hub API p95 열 추가
- [x] 목표 RPS 결정(2026-10-19): S1 150, S2 60, S3 200. 근거 표는 `docs/reviews/performance/README.md` "목표 RPS". S2 한계(80~120)는 order-service DB 커넥션 대기
- [x] S3를 단건 조회(H-02, H-04)로 변경, 시드에 경로 ID 추가, 보고서·목록에 캐시 설정 열 추가, 측정 환경에 Redis와 캐시 조건 변수 추가
- [x] ADR 0019(Redis 캐시를 기본 꺼짐으로 넣고 측정으로 채택을 정한다) 제안
- [x] 기준선 A1(S1), BC0(S2·S3) 각 3회(예열 회차 추가 절차, 2026-10-20). A1 p95 4.8~5.3ms·권한 반영 약 60.2초, BC0-S2 p95 14.9~18.4ms, BC0-S3 p95 4.0~4.1ms
- [ ] 기준선 A0(S1) 3회: 메모리 부족으로 측정 묶음이 중단돼 r1만 있다(`.local/perf/a0-incomplete/`). 다시 재야 한다
- [x] 개발 커밋(2026-10-20): A2 `f3397e6`(gateway·user·common-core), B1 `3a5af81`(hub), C1 `a58e3f1`(company). 모듈 테스트 common-core 2·gateway 31·user 91·hub 66·company 54 통과, `./gradlew build` 성공(2분 49초)
- [ ] 측정: A0 재측정, A2, B1-local·B1-redis, C1. 메모리 부족(남은 메모리 약 1GB / 15.8GB)으로 측정 묶음이 중단돼 멈췄다. 메모리 압박 중 측정은 스왑이 섞여 비교 자료가 되지 않는다
- [ ] 기준선 측정: A0, A1(S1), BC0(S2, S3) 각 3회
- 면접 포인트: 성능 개선을 어떻게 증명했나(기준선, 하나씩 변경, 3회 중앙값과 편차), 왜 고정 도착률로 부하를 줬나

## 다음 단계
1. 다른 프로그램(IDE, 브라우저, 개발용 `redis-stack-compose` 등)을 끄고 남은 메모리를 확보한다
2. 측정 묶음 스크립트 `.local/perf/batch-measure.sh`(Git 제외)로 남은 조건을 잰다. 조건마다 down→up→예열 3분(버림)→5분×3회
   - A0: `PERF_AUTH_CACHE_TTL=0s`, S1 150/s (`.local/perf/batch-redo-a0.sh`는 앞 묶음 PID를 기다리는 형태라 인자 없이 쓰려면 고쳐야 한다)
   - A2: `.local/perf/batch-measure.sh a2` (PERF_AUTH_CACHE=redis)
   - B1: `.local/perf/batch-measure.sh b1` (local과 redis 각각 S2 60/s, S3 200/s)
   - C1: `B1_CHOICE=<local|redis> .local/perf/batch-measure.sh c1` (B1에서 나은 쪽을 켠 상태)
3. 조건별 측정이 끝날 때마다 `docs/reviews/performance/` 결과를 측정 커밋으로 남긴다
4. ADR 0019 "후속 결정"에 항목별 채택·기각을 적고, 채택 항목은 `docker-compose.yml`·`.env.example` 변경안을 팀 리뷰에 올린다

## 직접 해보기
1. `script/perf.sh up` 실행. 확인: 마지막 줄에 `준비 완료: http://127.0.0.1:28080`
2. `script/perf.sh seed` 실행. 확인: `시드 완료: 허브 17, 수령업체 300, 상품 51 ...`과 `.local/perf/data.json` 생성
3. `script/perf.sh clean` 후 `RATE=5 DURATION=1m WARMUP=20s PUBLISH=0 script/perf.sh run s2 trial-s2` 실행. 확인: k6 요약 뒤에 보고서 표가 출력되고 `.local/perf/results/trial-s2/report.md`가 생긴다
4. Grafana(http://127.0.0.1:23000)에서 측정 시간대의 그래프를 함께 본다
5. 끝나면 `script/perf.sh down`

## 메모
- 이 PC에는 Python이 없다(Microsoft Store 별칭만 있음). 그래서 시드·보고서도 k6로 만들었다. 기존 `script/ui-test.sh`, `script/api-test.sh`는 Python이 필요하다
- Gateway TTL은 환경 변수 `AUTH_USERCACHETTL`로 덮는다. Config Server 값보다 환경 변수가 우선한다(`config-repo/application.yml` 첫 주석). 실제 반영 여부는 S1 시험 실행의 권한 반영 시간으로 확인한다(미확인)
- 관련 노트: `docs/learning/kmh/qna/2026-10-19-redis-cache-candidates.md`, `docs/learning/kmh/qna/2026-10-19-performance-baseline-plan.md`
- Redis 도입(A2, B1-redis, C1)은 구현 시점에 ADR로 기록하고 `docker-compose.yml` 변경은 팀 전체 리뷰를 받는다
