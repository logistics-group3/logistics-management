# feat/delivery-perf-master

## 지금 하는 일
배송 성능 마스터 측정(T10): V0~V4를 같은 데이터·같은 TPS 계단으로 재는 도구를 보완하고 측정한다. 정본은 `delivery-service/docs/perf-master.md`.

## 진행 상태
- [x] 측정 계획 `delivery-service/docs/perf-master.md` 작성(2026-10-22, Cowork 세션). 목표·합격선, 버전(V0~V4), 고정 환경, 절차, 캡처, 무효 판정, 결과 표, 포트폴리오 반영표, 에이전트 재개 절차
- [x] 측정 전용 시드 `load-test/seed/perf-managers.sql`·`perf-load.sql`·`perf-seed.sh` 작성(2026-10-22). Claude 작업 환경의 임시 PostgreSQL 16(최소 스키마)에서 V0·V2·V3 스키마 모두 오류 없음, 담당자 170명, 구간 담당 허브 불일치 0, V3 미확정 0을 확인했다. 맥 실제 DB에서는 아직 실행하지 않았다
- [x] 기준 커밋 확인: V0 `ef37a49`, V1 `bc3177b`, V2 `fd17db9`, V3 `4ff0b7f`. V0의 부하 테스트 API 요청 본문이 지금 k6 스크립트와 같다(`receiverManagerId` 포함)
- [x] 브랜치 `feat/delivery-perf-master`를 `feat/delivery-T09`에서 만들었다(사용자). 계획·시드·이 파일·`tasks/T10.md`·`tasks/README.md` T10 줄을 첫 커밋으로 남겼다(2026-10-22)
- [ ] 도구 보완(다음 단계 1~3)
- [ ] 준비(다음 단계 4)
- [ ] 측정 V0~V3, V4(다음 단계 5)
- [ ] 결과 기록(다음 단계 6), 포트폴리오 반영은 Cowork 세션에서
- 면접 포인트: 버전끼리 공정하게 비교하려고 무엇을 고정했나(데이터·계단·절차·측정 도구 버전), 합격선을 왜 대용량 기준으로 잡았나, 무효 측정을 어떻게 걸렀나, 판정을 왜 스크립트로 했나

## 다음 단계
1. **도구 보완 A: 판정·중단, 배정 집계 호환** (모두 `load-test/` 안. 바꾸기 전에 사용자 승인)
   - 판정 스크립트: k6 요약 JSON(`--summary-export`)에서 생성(`I-09 직접`) p95·p99, 실패율, 못 보낸 요청(`dropped_iterations` ÷ 목표 요청 수), 조회(`D-01 허브별`) p95를 계산해 perf-master.md 1절 합격선으로 칸마다 통과/실패를 낸다
   - `version-compare.sh`에 `STOP_ON_FAIL=1`: 같은 칸 2회 모두 실패하면 그 버전을 멈춘다
   - `assignment-stats.sh`: `assigned_at`이 없는 스키마(V0·V1)는 담당자별 건수(편차)만 낸다. V3는 배정 지연을 `confirmed_at` 기준으로 잰다
2. **도구 보완 B: 기록·캡처**
   - 칸마다 측정 구간의 Prometheus 시계열을 JSON으로 덤프(서비스별 p95, Hikari active·pending, Tomcat 바쁜 스레드, 프로세스 CPU)
   - 측정 동안 pg_stat_activity 5초 표본(대기 이벤트별 커넥션 수, advisory 대기 수) → `-pgwait.txt`
   - k6 터미널 출력 → `-k6.txt`, 칸마다 `manifest.txt`(커밋, 설정값, 시작·끝 시각, 파일 목록과 sha256)
   - 측정용 덧붙임 `docker-compose.perf.yml`: Grafana 이미지 렌더러, Prometheus 볼륨. 측정용 대시보드는 `load-test/grafana/`. 캡처 PNG는 버전 하나를 다 잰 뒤 칸별 시간 구간으로 한꺼번에 뽑는다(측정 중 렌더러 CPU 간섭을 피한다)
   - 결과 폴더: `load-test/results/perf/<버전>/<TPS>-<회차>/`
3. **실행·재개·검증**
   - `perf-switch.sh <버전>`: 해당 커밋을 worktree로 꺼내 delivery 이미지를 빌드·태그 → delivery 스키마 삭제 → 기동 → E2E 시드 → `perf-seed.sh`
   - `perf-run.sh <버전>`: 예열 → 계단 측정 → 판정 → 기록 → `STATUS.md` 갱신. 검증을 통과한 칸은 건너뛴다(끊겨도 같은 명령으로 이어 간다). `load-test/results/perf/STOP` 파일이 있으면 다음 칸 전에 멈춘다
   - `perf-verify.sh [버전]`: 칸마다 필수 파일, 무효 조건(쉬는 CPU, 예열, 실제 도착률, 커밋 일치), 판정 재계산이 기록과 같은지 확인
4. **준비**: `perf/v0`·`perf/v1`에 `V4__index_deliveries_departure_hub_id_created_at.sql`만 가져와 기동 확인, 100만 건에서 D-01 `EXPLAIN ANALYZE` 인덱스 전후, 캡처 폴더 커밋 여부 결정. `tasks/README.md`의 T10 줄은 첫 커밋에 넣었다
5. **측정**: V0 → V1 → V2 → V3(`CONFIRM=1`), V4(V3 + user 30초 정지, 생성 160·80). `caffeinate`로 잠자기 방지, 다른 무거운 앱 종료. 칸 사이마다 `perf-verify.sh`를 돌려 `STATUS.md`와 다르면 멈추고 보고한다
6. **결과**: perf-master.md 8절 표, T10.md 작업 기록. 결과가 이전 판단과 다르면 결정 로그에 정정 표시

## 직접 해보기
1. 시드를 작게 넣어 본다: `COUNT=100000 load-test/seed/perf-seed.sh`
2. 서울 허브 담당자별 과거 구간 수를 본다: `SELECT r.manager_id, count(*) FROM delivery_schema.p_delivery_routes r JOIN user_schema.p_users u ON u.user_id = r.manager_id WHERE u.hub_id = '6f39d519-4947-4a95-a454-58c25728f388' GROUP BY 1;`
3. 한 칸만 손으로 따라가 본다: `NAME=try RATES="40" REPEAT=1 READ_FIXED=20 CONFIRM=1 load-test/version-compare.sh`
4. 확인: 요약 JSON의 `I-09 직접` p95와 `-assignment.txt`의 담당자별 건수가 합격선 안인지 직접 판정해 본다

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
- 미확인: 구간 294만 건에서 칸마다 `VACUUM ANALYZE`에 걸리는 시간, Grafana 렌더러의 CPU·메모리 영향, k6 `maxVUs` 400(응답이 0.625초를 넘으면 640 TPS에서 VU가 모자라 못 보낸 요청으로 잡힌다. 합격선이 p95 200ms라 판정은 그 전에 실패한다)
- T09 메모에 따르면 Docker 앱 스택 두 개(`logistics-management`, `-sync-lock`)가 stop 상태로 남아 있다. 측정 전에 정리할지 확인한다
- 사용자 규칙: 질문에는 답만 하고, 변경은 무엇을 바꿀지 말한 뒤 "진행할까요?"로 확인한다. 다른 모듈 코드는 승인 없이 바꾸지 않는다. 커밋은 `git -c user.name=jihaneol -c user.email=6531z@naver.com commit`, 제목에 `(T10)`, AI 공동 작성자 줄 금지, 비밀값·`.env` 금지. 문서는 한국어 평서체. 답 끝에 "기록: 경로"
