# feat/delivery-perf-master

## 지금 하는 일
배송 성능 마스터 측정(T10): 도구 보완, 측정 V0~V4, 결과 기록까지 끝났다(2026-10-22). 남은 일은 포트폴리오 반영(Cowork). 정본은 `delivery-service/docs/perf-master.md`.

## 진행 상태
- [x] 계획(perf-master.md)·측정 전용 시드·기준 커밋 확인·브랜치(2026-10-22). 첫 커밋 `docs(delivery): 배송 성능 마스터 측정 계획과 측정 전용 시드 (T10)`
- [x] 도구 보완 A: 판정(`perf-judge.py`)·계단 자동 중단·배정 집계 버전 호환
- [x] 도구 보완 B·실행 스크립트: `perf-switch.sh`·`perf-run.sh`·`perf-verify.sh`, `load-test/perf/`(cell.sh, perf.py, capture.sh, explain-d01.sh), 측정용 덧붙임 `load-test/docker-compose.perf.yml`(postgres shm 256MB, Prometheus 5초·볼륨, Grafana 렌더러), 측정 대시보드
- [x] 준비: 로컬 브랜치 `perf/v0`(5a6c79f)·`perf/v1`(c7fb578) = 기준 커밋 + 목록 인덱스, 100만 건 D-01 EXPLAIN(perf-master.md 4절), 공유 메모리 부족 해결(TS-032)
- [x] 측정 V0~V4(2026-10-22 13:30~16:26). 칸 37개 모두 `perf-verify.sh` 통과, 캡처 185장
  - 버틴 최대 TPS: V0 80, V1 80, V2 320, V3 80. V4는 생성 80에서 user 30초 정지에도 영향 없음(통과), 160은 실패. 근거는 perf-master.md 8절, 결정 로그 30
  - 무효 처리(에이전트 판단, 7절): V2 80-1은 측정 40초째부터 부하 없는 서비스까지 CPU가 함께 튀어 Docker VM 정체로 보고 다시 쟀다(같은 시간 에이전트가 분석 명령을 돌려 11절을 어겼다). V4 첫 fault-160은 판정 출력에 칸 폴더 이름이 들어간 도구 버그로 무효가 돼 `e27634c`로 고치고 다시 쟀다. 이유는 각 `*.invalid-*/invalid-reason.txt`(V2)와 T10.md
  - 원본: `load-test/results/perf/`(STATUS.md, 버전별 칸 폴더, `V*-run.log`, captures, explain-d01.txt). git 제외
- [x] 결과 기록: perf-master.md 8절(표는 `python3 load-test/perf/perf.py table`), T10.md, 결정 로그 30(28에 → 30 표시)
- [ ] 포트폴리오 반영(perf-master.md 9절)은 Cowork 세션에서 한다
- 면접 포인트: 버전끼리 공정하게 비교하려고 무엇을 고정했나(데이터·계단·절차·측정 도구 커밋), 합격선을 왜 대용량 기준으로 잡았나, 무효 측정을 어떻게 걸렀나(쉬는 CPU, 예열, 도착률, 커밋 라벨, 부하 없는 서비스의 CPU), 판정을 왜 스크립트로 했나(다시 계산해 비교), V0·V1이 왜 160에서 막혔나(측정 중 쌓이는 진행 중 구간과 담당 건수 쿼리, 허브 잠금 직렬화)

## 다음 단계
1. **포트폴리오 반영**(Cowork 세션): perf-master.md 9절 표의 쪽을 8절 값으로 바꾼다. V2 240의 1회 실패, V3 확정 요청의 Gateway 경유, V4 160의 상태 수집 영향은 함께 적는다
2. **선택(사용자 판단)**: V0·V1의 실제 한계를 좁히려면 120 계단을 더한다(모든 버전 다시 측정). V4 160을 상태 수집 없이 다시 재려면 `fault-isolation.sh`에 수집을 끄는 옵션이 필요하다. 담당 건수 부분 인덱스(결정 로그 13)는 앱 변경이라 T10 범위 밖이다
3. **정리**: 측정 스택은 떠 있다(`source load-test/perf/lib.sh && docker compose ps`). 끄려면 `docker compose stop`. Prometheus 볼륨(`perf-prometheus`)에 지표가 남아 캡처를 다시 뽑을 수 있다(`load-test/perf/capture.sh <버전>`). delivery 이미지는 `perf-current` 태그가 V3(4ff0b7f)다

## 직접 해보기
1. 시드를 작게 넣어 본다: `COUNT=100000 load-test/seed/perf-seed.sh`
2. 서울 허브 담당자별 과거 구간 수를 본다: `SELECT r.manager_id, count(*) FROM delivery_schema.p_delivery_routes r JOIN user_schema.p_users u ON u.user_id = r.manager_id WHERE u.hub_id = '6f39d519-4947-4a95-a454-58c25728f388' GROUP BY 1;`
3. 한 칸만 손으로 따라가 본다: `NAME=try RATES="40" REPEAT=1 READ_FIXED=20 CONFIRM=1 load-test/version-compare.sh`
4. 확인: 요약 JSON의 `I-09 직접` p95와 `-assignment.txt`의 담당자별 건수로 직접 판정해 보고 `-verdict.txt`와 같은지 비교한다
5. 결과를 직접 본다: `python3 load-test/perf/perf.py table`로 8절 표를 다시 만들고, `load-test/results/perf/V1/160-1/pgwait.txt`의 `advisory_wait` 열과 `V0/240-1/docker.txt`의 postgres CPU가 1분 동안 어떻게 변하는지 본다

## 메모
- 결정(사용자, 2026-10-22)
  1. 순서는 포트폴리오 2쪽과 같다: V0 처음(잠금 없음) → V1 허브 잠금 → V2 생성과 분리 → V3 확정 뒤 배정 → V4 장애 격리
  2. 생성 TPS 계단 40·80·160·240·320·480·640, 조회 20 고정, 칸마다 1분 × 2회
  3. 과거 배송 100만 건, 허브마다 배송담당자 10명
  4. 목록 인덱스는 모든 버전의 공통 바닥
  5. 합격선은 대용량 처리 기준(perf-master.md 1절: 생성 p95 200ms·p99 500ms, 실패+못 보냄 0.1%, 조회 p95 100ms, 편차 1건, 배정 지연 p95 1초, 미배정 0)
  6. 개인 측정용이다. 팀 공유 파일(`infra/`, `docker-compose.yml`, `config-repo/`)은 바꾸지 않고 `load-test/`와 측정용 덧붙임 파일에 둔다
  7. 측정 도구(`load-test/`)는 T09 기준 한 벌로 고정하고 앱만 버전별로 바꾼다
  8. 결과 숫자와 캡처를 모두 자동으로 남긴다
  9. 측정·감시는 Claude Code에서 한다. 흐름은 대화가 아니라 파일(perf-master.md, 이 파일, STATUS.md, manifest)에 둔다
  10. 포트폴리오 덱(claude.ai 아티팩트 "물류 MSA 포트폴리오 — 배송 서비스") 반영은 Cowork 세션에서 한다(perf-master.md 9절)
  11. 확정(I-19) 실패는 생성 실패에 넣지 않고 참고로 적는다. 확정이 실패한 배송은 미배정에서 잡힌다(perf-master.md 1절)
  12. k6 생성 시나리오 VU는 미리 400개(= 최대)를 만든다. TPS 개선이 핵심이라 k6가 먼저 막히면 안 된다. 30개면 640 TPS에서 요청당 47ms를 넘는 순간 못 보낸 요청이 생겨 버전 차이가 묻힌다(perf-master.md 3절)
  13. 한 번만 실패한 칸은 다음 계단으로 가고, 판정 불가는 바로 멈춘다. 배정 집계는 측정 종료 30초 뒤(perf-master.md 5절)
- 미확인: 구간 294만 건에서 칸마다 `VACUUM ANALYZE`에 걸리는 시간, Grafana 렌더러의 CPU·메모리 영향, 실제 HTTP를 보내는 VU 400개의 k6 메모리(빈 반복으로는 109MB)
- T09 메모에 따르면 Docker 앱 스택 두 개(`logistics-management`, `-sync-lock`)가 stop 상태로 남아 있다. 측정 전에 정리할지 확인한다
- 사용자 규칙: 질문에는 답만 하고, 변경은 무엇을 바꿀지 말한 뒤 "진행할까요?"로 확인한다. 다른 모듈 코드는 승인 없이 바꾸지 않는다. 커밋은 `git -c user.name=jihaneol -c user.email=6531z@naver.com commit`, 제목에 `(T10)`, AI 공동 작성자 줄 금지, 비밀값·`.env` 금지. 문서는 한국어 평서체. 답 끝에 "기록: 경로"
