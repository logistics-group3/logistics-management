# 배송 성능 마스터 측정

고도화 순서(V0 → V4)마다 **같은 데이터, 같은 부하 계단, 같은 절차**로 생성 TPS를 재서 "버틴 최대 TPS"를 비교한다. 지금까지의 숫자는 단계마다 부하가 달라(동시 50건, 160+160, 240, 주문 40·80, 10+10) 버전끼리 비교할 수 없었다. 이 문서가 다시 잴 때의 정본이다.

- 도구 사용법: [load-test.md](load-test.md). 장애 실험 상세: [fault-isolation.md](fault-isolation.md).
- 지난 측정 기록: [T08](tasks/T08.md) 성능 고도화 단계 표, [T09](tasks/T09.md) 단계 4, 결정 로그 24~29.
- 상태: 계획. 태스크는 [T10](tasks/T10.md), 인계는 `docs/progress/feat-delivery-perf-master.md`. 도구 보완과 준비 작업(4절)이 끝나면 측정한다.

## 1. 목표와 합격선

질문은 하나다. **같은 조건에서 버전마다 생성 TPS를 어디까지 버텼고, 무엇이 먼저 막혔나.**

합격선은 대용량 처리 기준으로 정했다(2026-10-22). 같은 계단을 2회 재서 **두 번 모두** 통과해야 통과다. "버틴 최대 TPS"는 마지막으로 통과한 계단이다.

| 지표 | 합격선 | 비고 |
|---|---|---|
| 생성 p95 | 200ms 이하 | |
| 생성 p99 | 500ms 이하 | |
| 생성 실패 + 못 보낸 요청(k6 dropped) | 0.1% 이하 | 확정(I-19) 실패는 넣지 않고 참고로 적는다. 확정이 실패한 배송은 배정되지 않아 미배정에서 잡힌다 |
| 조회 p95 (20 TPS 고정) | 100ms 이하 | 생성이 조회를 막는지 본다 |
| 담당자 편차 | 1건 이하 | 측정에서 만든 구간만, 측정 허브 담당자 10명(0건 포함)의 최대 - 최소 |
| 배정 지연 p95 | 1초 이하 | V2부터. 생성(V3는 확정) → 배정 |
| 측정 뒤 미배정 구간 | 0 | V2부터. 측정 종료 30초 뒤 |

이전 기준(p95 500ms, 실패·못 보냄 1%)보다 엄격하다. 이전 숫자와 섞어 쓰지 않는다.

판정은 `load-test/perf-judge.py`가 회차마다 k6 요약 JSON과 배정 집계로 내고 `-verdict.txt`에 남긴다(종료 코드 0 통과, 1 실패, 2 판정 불가). 합격선 숫자는 이 표와 그 파일 머리의 상수가 같아야 한다.

## 2. 측정 버전

앱 코드만 버전별로 바꾸고, 측정 도구(`load-test/`)는 V3 기준 한 벌로 고정한다.

| 단계 | 버전 | 기준 커밋 | 비고 |
|---|---|---|---|
| V0 | 처음: 생성 중 즉시 지정, 잠금 없음 | `ef37a49` (`feat/delivery-assignment-lock`, 잠금 직전) | `CONFIRM=0`. 편차가 합격선을 넘는지 함께 본다 |
| V1 | 고도화 1: 허브 advisory lock | `bc3177b` (같은 브랜치, 잠금 커밋) | `CONFIRM=0` |
| V2 | 고도화 2: 생성과 배정 분리 | `fd17db9` (`feat/delivery-T08`, 인덱스 포함) | `CONFIRM=0` |
| V3 | 고도화 3: 주문 확정 뒤 배정 | `4ff0b7f` (`feat/delivery-T09`) | `CONFIRM=1`. 확정 요청이 추가된 비용이 TPS로 드러난다 |
| V4 | 장애 격리 | V3와 같은 코드 | 5절 V4 절차. user 30초 정지 |

- **목록 인덱스는 공통 바닥이다.** V0·V1은 인덱스 전 코드라 `V4__index_deliveries_departure_hub_id_created_at.sql`만 가져와 측정 브랜치(`perf/v0`, `perf/v1`)를 만든다. 인덱스 효과(D-01 요청당 33ms → 4.4ms, 30만 건)는 "사전 정비"로 따로 적는다. 100만 건에서는 count와 목록을 합쳐 약 404ms → 7.7ms다(4절).
- V0~V3 모두 user 호출 장애 대응(타임아웃·재시도·벌크헤드·서킷)이 이미 들어 있다(T06이 먼저 들어감). 장애 격리 개선 전후 비교는 fault-isolation.md 8절 기록(S1)을 그대로 쓰고, V4는 "배정 분리 뒤 user가 멈추면 무엇이 영향을 받나"를 잰다.
- V0·V1은 마이그레이션이 V1 하나라 V2~V5 컬럼(`managing_hub_id` 이후, `confirmed_at` 등)이 없다. 시드 호환을 4절에서 확인한다.

## 3. 고정 환경

| 항목 | 값 |
|---|---|
| 장비 | MacBook Air 1대, Docker CPU 8개 · 메모리 7.8GiB, k6도 같은 맥 |
| 실행 | `COMPOSE_FILE=docker-compose.yml:docker-compose.loadtest.yml:docker-compose.fault.yml`, Zipkin 정지 |
| 과거 배송 | **100만 건** (`load-test/seed/perf-seed.sh`, 구간 약 294만), 지난 180일, 허브 17곳에 고르게 |
| 배송담당자 | **허브마다 10명**, 총 170명. 과거 구간도 이 170명에게 고르게 나눈다 |
| 측정 허브 | 서울 허브 하나에 생성을 몰고, 조회도 서울 허브 최신순 목록 |
| 주요 설정 | Hikari 10, Tomcat 200, `delivery.assignment.batch-window-ms` 0 |
| 측정용 덧붙임 | `load-test/docker-compose.perf.yml`: postgres `shm_size` 256MB(구간 294만 건 VACUUM, TS-032), Prometheus 수집 5초·볼륨, Grafana 렌더러. delivery 이미지는 버전마다 `perf-switch.sh`가 바꾼다 |
| k6 VU | 생성 시나리오는 미리 만드는 VU = 최대 = 400(`PRE_VUS`, `version-compare.sh` 기본값), 조회는 50 · 최대 400. 측정 중 VU를 늘리는 동안 못 보낸 요청이 서버 실패로 잡히지 않게 한다. 400개를 미리 만들어도 k6 메모리는 109MB(50개 60MB)이고 쉬는 VU는 CPU를 쓰지 않는다(2026-10-22) |

측정마다 실행한 커밋, 이미지 빌드 시각, 위 설정값을 결과 표에 같이 적는다.

## 4. 준비 작업 (측정 전 한 번)

이 측정은 개인 측정용이라 팀 공유 시드(`infra/postgres/seed/`)를 바꾸지 않고 `load-test/seed/`에 따로 둔다(2026-10-22 결정).

| 파일 | 내용 |
|---|---|
| `load-test/seed/perf-managers.sql` | 허브마다 배송담당자를 10명으로 맞춘다(총 170명). 이미 있는 담당자(dmgr1·2·3)는 세고 모자란 만큼만 넣는다. 다시 적용해도 10명을 넘지 않는다 |
| `load-test/seed/perf-load.sql` | 과거 배송 `:count`건(기본 100만, 구간 약 294만). 구간 담당자는 그 구간 담당 허브의 담당자 10명에게 고르게 나눈다. 기존 `created_by = 'LOAD'` 데이터를 먼저 지운다 |
| `load-test/seed/perf-seed.sh` | 위 두 파일을 차례로 넣는다. `COUNT=1000000 load-test/seed/perf-seed.sh` |

- 버전마다 스키마가 달라 `perf-load.sql`은 있는 컬럼만 채운다. `managing_hub_id`·`assigned_at`은 V2부터, `confirmed_at`은 V3부터 넣는다.
- 시드 스크립트 동작만 확인했다(Claude 작업 환경의 임시 PostgreSQL 16, 최소 스키마, 2026-10-22). V0·V2·V3 스키마에 오류 없이 들어가고, 담당자 170명, 구간 담당 허브 불일치 0, V3 미확정 배송 0이다. 성능 측정이 아니며 실제 맥 DB에서는 아직 실행하지 않았다.
- 기존 시드는 구간 절반을 dmgr1·dmgr2에게 몰아 담당 건수 쿼리 비용을 키웠다. 이 시드는 고르게 나누므로 담당자 1명당 과거 구간이 약 1.7만 건이다. 두 시드의 결과를 섞어 비교하지 않는다.

남은 준비

- [x] `perf/v0`(ef37a49 + 인덱스), `perf/v1`(bc3177b + 인덱스) 로컬 브랜치를 만들었다(2026-10-22). 기동 확인은 `perf-switch.sh`가 한다.
- [x] 100만 건에서 D-01 `EXPLAIN ANALYZE` 인덱스 전후를 다시 쟀다(사전 정비 기록, `load-test/perf/explain-d01.sh`, 2026-10-22, V0 코드, 서울 허브 58,824건, 세 번째 실행).

| D-01 쿼리 | 인덱스 없음 | 인덱스 있음 |
|---|---|---|
| 전체 건수(count) | 181ms, Bitmap Heap Scan으로 힙 33,134블록을 읽는다 | 7.7ms, Index Only Scan 2,149버퍼, Heap Fetches 0 |
| 최신순 10건 | 223ms, 같은 힙 읽기 + top-N 정렬 | 0.015ms, 인덱스 순서로 10건만 읽고 끝난다 |

테이블(259MB)이 `shared_buffers`(128MB)보다 커서 인덱스가 없으면 요청마다 힙을 다시 읽는다. 30만 건 때(요청당 33ms → 4.4ms)보다 차이가 커졌다. 원본은 `load-test/results/perf/explain-d01.txt`.
- [x] 캡처 폴더는 `load-test/results/perf/captures/`이고 커밋하지 않는다(`load-test/.gitignore`의 `results/`). 원본은 로컬에 두고 숫자만 8절에 옮긴다(2026-10-22).

## 5. 표준 절차

### 버전 하나 (V0~V3)

```bash
# 스택은 측정용 덧붙임 조합으로 한 번 띄운다(source load-test/perf/lib.sh가 COMPOSE_FILE을 정한다)
source load-test/perf/lib.sh && docker compose up -d --no-build && docker compose stop zipkin
load-test/perf-switch.sh V1   # 그 커밋으로 delivery 이미지 빌드(커밋 라벨) → delivery 스키마 재생성 → 기동 → 시드(100만)
load-test/perf-run.sh V1      # 예열 2회 → 계단 → 판정 → 기록 → STATUS.md → 버전 끝에 캡처
load-test/perf-verify.sh V1   # 칸 사이마다. STATUS.md와 다르면 멈추고 보고한다
```

1. `perf-switch.sh`가 버전을 바꾼다. 커밋은 `load-test/perf/versions.txt`에 있고, 이미지 라벨 `perf.commit`으로 칸마다 확인한다.
2. `perf-run.sh`가 셸에 남은 측정 변수를 지우고 예열 2회(초당 10+10, 1분)를 한 뒤, 칸 기록으로 다음 칸을 정해 잰다(`load-test/perf/perf.py next`).
3. 계단 한 칸(`load-test/perf/cell.sh`): 60초 쉼 → 측정이 만든 배송만 삭제 → 쉬는 상태 확인(모든 컨테이너 CPU 2% 미만, 최대 180초 기다림) → 1분 측정(pg_stat_activity·docker stats 5초 표본) → 30초 뒤 배정 집계 → Prometheus 덤프 → 판정 → manifest. 2회 반복.
4. 같은 칸에서 두 번 모두 실패하면 그 버전은 거기서 멈춘다. 한 번만 실패한 칸은 통과가 아니지만 다음 계단으로 간다. 1회차의 실패가 k6 지표뿐이고 첫 30초를 빼면 합격선 안이면 3회차를 재서 2·3회차로 판정한다(7절, `perf.py`가 k6 CSV로 판단).
5. 칸이 무효면(7절, `perf.py check-cell`) `<칸>.invalid-<시각>`으로 옮기고 다시 잰다. 3번 연속이면 멈춘다.

계단 하나만 손으로 볼 때는 `version-compare.sh`(같은 판정, `STOP_ON_FAIL=1`)를 쓴다. 측정 기록에는 쓰지 않는다.

### V4 (장애 격리)

V3 코드로 생성 160 + 조회 20, 6분, 시작 90초 뒤 user 30초 정지. 같은 조건으로 생성 80에서도 한 번 더 잰다.

```bash
load-test/perf-switch.sh V4 && load-test/perf-run.sh V4   # 칸 fault-160, fault-80 (FAULT_MODE=pause, DURATION=6m, CONFIRM=1)
```

V4 합격선: 장애 중 생성 실패 0.1% 이하(생성은 user를 부르지 않는다), 장애 중 조회 p95 100ms 이하, user 복구 뒤 30초 안에 미배정 구간 0.
판정은 `perf.py judge-fault`가 한다. 장애 구간 지표는 k6 요약의 `phase:fault` 줄과 CSV(못 보낸 요청), 미배정은 `recovery.txt`(복구 30초 뒤, 복구 전에 확정된 배송의 담당자 없는 구간 수), 10초 단위 흐름은 `timeline.txt`에 남는다.

## 6. 캡처 (칸마다 같은 것)

숫자는 칸마다 `load-test/results/perf/<버전>/<TPS>-<회차>/`에 자동으로 남고, 그림은 버전 하나를 다 잰 뒤 한꺼번에 뽑는다(렌더러가 Chromium을 띄워 측정 중 CPU를 쓰므로). 측정용 덧붙임이 Prometheus에 볼륨을 붙이고 수집을 5초로 줄여, 나중에 다시 뽑아도 같은 지표가 나온다.

| 항목 | 칸 폴더 파일 | 그림(`captures/<버전>-<칸>-<항목>.png`) |
|---|---|---|
| k6 요약 | `k6.txt`, `k6.json`, 요청별 `k6.csv.gz` | |
| 생성·조회 p95 | `prom.json` | `p95`, `rps` |
| Hikari active · pending | `prom.json` | `hikari` |
| Tomcat 바쁜 스레드 | `prom.json` | `tomcat` |
| 프로세스·컨테이너 CPU | `prom.json`, `docker.txt`(약 5초), `k6-stats.txt` | `cpu` |
| 대기 이벤트(advisory 대기 포함) | `pgwait.txt`(pg_stat_activity 5초) | |
| 배정 집계(편차·지연) | `assignment.txt` | |
| 판정 | `verdict.txt`(`perf-judge.py`, V4는 `perf.py judge-fault`) | |
| 실행 조건 | `manifest.txt`(커밋, 설정값, 쉬는 CPU, 예열·시작·끝 시각, 파일 해시) | |

그림은 `load-test/perf/capture.sh <버전>`이 측정용 대시보드(`load-test/grafana/perf-master.json`)를 올리고 칸의 시간 구간(시작 10초 전 ~ 끝 30초 뒤)으로 뽑는다. 원본 기록과 그림은 커밋하지 않는다(`load-test/results/`는 git 제외). 문서에는 8절 표의 숫자를 옮긴다.

## 7. 무효 판정

다음 중 하나면 그 회차를 버리고 다시 잰다. 지난 측정이 세 번 무효가 된 이유다.

- 쉬는 상태 CPU가 높거나, 측정 중 다른 무거운 앱(화상 회의 등)을 썼다.
- 설정을 바꾸고 재시작한 직후 예열 없이 쟀다.
- 셸에 이전 측정의 환경 변수가 남아 있었다.
- 1회차 첫 30초만 튀고 2회차는 통과: 3회차를 추가로 재서 둘 중 나중 두 회차로 판정한다.

## 8. 결과 기록

### 칸별

| 버전 | TPS | 회차 | 생성 p95 | 생성 p99 | 실패+못 보냄 | 조회 p95 | 편차 | 배정 지연 p95 | 미배정 | Hikari 대기 | advisory 대기 | postgres CPU | 판정 | 캡처 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| | | | | | | | | | | | | | | |

### 버전별 요약

| 버전 | 버틴 최대 TPS | 처음 실패한 TPS | 먼저 막힌 곳(증거) |
|---|---|---|---|
| V0 | | | |
| V1 | | | |
| V2 | | | |
| V3 | | | |
| V4 | (생성 160 · 80, user 30초 정지) | | |

## 9. 포트폴리오 반영

측정이 끝나면 포트폴리오 덱의 다음 쪽을 이 표의 값으로 바꾼다.

| 쪽 | 바꿀 것 |
|---|---|
| 05 측정 환경 | 데이터 100만 건 · 담당자 10명, 새 합격선 |
| 06 고도화 1 | V0 · V1의 편차와 버틴 TPS |
| 07 고도화 2 | V1 → V2 버틴 TPS, 막힌 곳 증거 |
| 08 근거 차트 | x축 TPS 계단, 선은 V0~V3 p95 하나의 그래프로 교체 |
| 09 고도화 3 | V2 → V3 확정 단계 비용 |
| 10 장애 격리 | V4 결과 추가 |
| 11 단계별 정리 | 버전별 요약 표로 교체 |

## 10. 에이전트 재개 절차

측정은 몇 시간 걸려 에이전트 컨텍스트가 압축되거나 세션이 바뀔 수 있다. 흐름은 대화가 아니라 아래 파일에 둔다.

| 층 | 파일 | 쓰는 쪽 |
|---|---|---|
| 계획(정본) | 이 문서 | 사람이 결정할 때만 |
| 진행 상태 | `load-test/results/perf/STATUS.md` | `perf-run.sh`가 칸마다 자동 갱신 |
| 칸 기록 | `load-test/results/perf/<버전>/<TPS>-<회차>/manifest.txt` | `perf-run.sh` |
| 판단·이상 | `docs/progress/feat-delivery-perf-master.md` | 에이전트·사람 |

압축이나 새 세션 뒤에는 이 순서로 이어 간다.

1. 인계 파일과 이 문서를 읽는다.
2. `STATUS.md`를 읽고 `load-test/perf-verify.sh`를 실행한다. 아직 파일이 없으면 도구 보완 전 단계다.
3. 검증 결과가 `STATUS.md`와 같으면 다음 칸부터 이어 간다. 다르면(누락·무효·판정 불일치) 측정을 멈추고 보고한다.
4. 판단이 필요했던 일(무효 처리, 재측정)은 인계 파일 "진행 상태"에 이유와 함께 적는다.

Claude Code는 `.claude/hooks/post-compact.sh`(개인 훅, git 제외)가 압축 직후 이 절차를 다시 알린다.

## 11. 감시

- 측정 실행: `perf-run.sh`를 백그라운드로 돌린다. 측정 자체는 스크립트가 끝까지 책임지므로 에이전트 대화와 무관하다.
- 감시: 칸 사이 쉬는 시간에 `perf-verify.sh`로 확인한다. 측정 중에는 무거운 작업을 하지 않는다(측정 CPU 간섭).
- 멈추기: `load-test/results/perf/STOP` 파일을 만들면 다음 칸 전에 멈춘다. 지우고 같은 명령을 다시 실행하면 검증을 통과한 칸을 건너뛰고 이어 간다.
- 준비: `caffeinate`로 맥 잠자기를 막고, 화상 회의 같은 무거운 앱을 끈다.
