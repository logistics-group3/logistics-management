# feat/delivery-T06

## 지금 하는 일
배송 테스트 보강. 동시 변경 잠금(완료), E2E 스크립트(작성 완료, 사용자 실행 대기), k6 부하 테스트 도구(작성 완료, 사용자가 기준선 측정 예정). 태스크 문서는 `delivery-service/docs/tasks/T06.md`.

## 진행 상태
- [x] 동시 변경 통합 테스트 4개, 배송 행 `PESSIMISTIC_WRITE` 잠금(결정 로그 19)
- [x] 시드 SQL, `script/delivery-e2e-check.sh`, `delivery-service/docs/e2e.md`
- [x] 트래픽 병목 분석(학습 노트 `docs/learning/haneol/qna/2026-10-15-delivery-high-traffic-bottlenecks.md`)
- [x] k6 스크립트(`load-test/k6/`), 대량 데이터 `infra/postgres/seed/delivery-load.sql`, 테스트 전용 `POST /api/v1/deliveries/load-test`(기본 꺼짐, `docker-compose.loadtest.yml`로 켬), `delivery-service/docs/load-test.md`(결정 로그 24)
- [ ] 사용자가 E2E 실행
- [ ] 사용자가 k6 기준선 측정 → `load-test.md` 7절 결과 표에 기록
- [x] 커밋 (2026-10-16, T06.md 작업 기록의 제목으로 나눔)

검증(2026-10-15, 이 세션에서 직접 실행): `./gradlew :delivery-service:test` 44개 통과, `./gradlew build` 통과(k6 작업 전 기준), k6 스크립트 `node --check` 통과, `bash -n script/delivery-e2e-check.sh` 통과. 시드·대량 데이터 SQL, E2E, k6는 실제 DB·서비스로 실행하지 않았다(미확인). 에이전트는 Docker로 서비스를 띄우지 않기로 사용자와 정했다.

## 다음 단계
1. 사용자가 `load-test.md` 3절 순서로 기준선을 잰다. 결과(k6 요약, 실행계획)를 받으면 병목을 해석한다.
2. 실험 계획(6절) 순서로 하나씩 바꾼다: 담당 건수 부분 인덱스(V2) → I-14·I-01 캐시 → user 장애 재현과 타임아웃·서킷 브레이커 → 목록 복합 인덱스. 성능 개선은 다음 태스크(T07)로 나누는 것을 제안했다.
3. 동시 배정 쏠림(결정 로그 16 가정이 고부하에서 깨짐)은 SA 규칙 해석이 걸려 사용자 결정이 필요하다.

## 추가 작업(2026-10-16): 배송 조회 범위 변경 (T06과 별개, 커밋 분리 필요)
- 사용자 결정으로 D-01·D-02 범위를 바꿨다: MASTER 전체 / 배송담당자 담당 구간 / 업체 관리자 자기 업체 / 그 외 빈 페이지·403. 결정 로그 21, ADR 0004.
- 코드: `ManagedCompanyReader`, `CompanyClient`(+Adapter, `ManagedCompanyResponse`), `DeliveryScope`, `DeliverySearchCondition.scope`, `DeliveryQueryRepositoryImpl.within`, `DeliveryQueryService`(D-01·D-02 트랜잭션 제거).
- 테스트: `DeliveryQueryServiceTest` 신규 8개, `DeliveryIntegrationTest.searchScope`로 교체, E2E 스크립트에 4개 확인 추가.
- 검증(2026-10-16, 사용자 맥에서 실행): `./gradlew :delivery-service:test` 통과. 테스트 결과 파일 기준 54개 전부 통과(통합 테스트 13개 포함, 신규 `DeliveryQueryServiceTest` 8개, `DeliveryIntegrationTest.searchScope`). E2E 새 확인 4개는 company I-17이 없어 아직 실행하지 않았다.
- **차단**: company I-17이 없다(COMPANY-002 OPEN). 그 전까지 MASTER가 아닌 D-01, E2E 새 확인, `delivery-read.js`의 `D-01 담당자`가 실패한다. 장애 격리 실험(MASTER만 사용)은 영향 없다.
- 커밋: 사용자 요청으로 같은 브랜치에 별도 커밋(`feat(delivery): 배송 조회 범위에 업체 관리자 추가 (T06)`)으로 나눴다. PR을 따로 내려면 이 커밋만 새 브랜치로 옮긴다.
- 결정 로그 I-15 항목(원래 19)이 rebase 중 덮어써져 있어 22번으로 복구했다.
- 커밋하지 않은 것: `docs/design/`(이 작업과 무관), `docs/learning/haneol/qna/2026-10-16-zipkin-span-reporting.md`(출처 확인 필요)

## 2026-10-19 장애 격리 실험 진행
- S1을 세 번 돌렸으나 모두 무효였다: 실험 설정 누락(TS-027), 기동 직후 부하로 커넥션 풀 고갈(TS-028).
- 예비 측정으로 JIT 예열이 원인임을 확인했다(GC 아님). 측정 방식을 바꿨다(결정 로그 25): 예열 2회, 측정 사이 `load-test/reset-delivery-data.sh`, 속도 초당 10+10, 추적 100% 고정.
- 도구 추가(커밋 전): `load-test/diagnose.sh`, `load-test/reset-delivery-data.sh`, `fault-isolation.sh` 사전 확인과 `-stats.txt` 수집, `fault-isolation.js` setup 확인.
- S0·S1(개선 전) 측정 완료: 조회 p95 21ms → 4,307ms, 스레드 189/200, 커넥션 대기 0, 복구 120초 넘게 안 됨. 스크립트 버그로 한 번 무효(TS-029, 고침).
- 개선 적용(커밋 전, 결정 로그 26): 타임아웃 2초, 연결 실패만 재시도, 벌크헤드·서킷 브레이커, I-01 부분 저하. 다음: 테스트 실행 → config-server·delivery 재빌드 → 예열 → S1 6분 재측정.
- 사용자가 직접 수정 중인 파일(건드리지 않음): `delivery-service/AGENTS.md`, `DeliveryCreationService.java`(주석).
- 개선 후 측정(13:07~13:35, 예열·Gateway 캐시 1시간): 초당 10+10 S1 조회 p95 15ms·스레드 1·복구 약 20초. 서킷 판단 창을 최근 10초로 바꾼 뒤 초당 40+40 장애 없음 생성 503 0.4%(튜닝 전 19%), 장애 주입 조회 p95 7.7ms. 12:30~12:35 측정은 재시작 직후 예열 없이 80요청이라 무효(TS-028 재발).
- 커밋: `feat(delivery): user 호출에 타임아웃·벌크헤드·서킷 브레이커 적용 (T06)`, `test(delivery): 장애 격리 측정 도구 보강과 개선 전후 결과 기록 (T06)`, `docs(docs): 로컬 부하 측정 트러블슈팅과 학습 노트 추가 (T06)`.
- 다음: 실험 지도 5번 동시 배정 쏠림. 선택: S1 3회 중앙값, 최대 처리량 단계 측정.

## 메모
- `docs/design/`은 이 작업과 무관한 미추적 파일이다. 커밋에 넣지 않는다.
- 부하 테스트 API가 꺼져 있으면 `POST /api/v1/deliveries/load-test`는 405다(`/{deliveryId}`에 POST가 없음). 실행으로 확인하지는 않았다.
