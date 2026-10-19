# feat/test-connection-limits

## 지금 하는 일
통합 테스트 DB 연결 고갈의 근본 대책 D(캐시 컨텍스트 상한) 적용·검증 완료, push·PR 전 사용자 확인 대기

## 진행 상태
- [x] 배경: PR #18(dev `d4b767a`)의 비동기 통합 테스트가 order 컨텍스트를 11 → 12개로 늘려 `too many clients already`가 났다. 기록은 [TS-025](../troubleshooting.md), [STAR 기록](../reviews/2026-10-19-test-connection-exhaustion.md), [작업 계획](../reviews/2026-10-19-test-connection-exhaustion-plan.md).
- [x] 1단계 측정: `max_connections` 100(예약 3), 컨텍스트 수 order 12·user 4·delivery 3·hub 2·company 1, 풀 상한 없이 order 2회 연속 실패(18개, 36개).
- [x] 2단계 선택(사용자 2026-10-19): D만 `common`에 적용. B(풀 상한 공통)·C(max_connections 상향)·E는 적용하지 않았다.
- [x] 구현: `common/src/testFixtures/resources/spring.properties`(`spring.test.context.cache.maxSize=8`) 추가, order 로컬 풀 상한 파일 제거, 측정 도구 `script/test-db-connections.sh` 추가, 테스트 규칙·AGENTS 검증 명령·TS-025 갱신.
- [x] 검증: 같은 명령으로 측정. 캐시 상한 없음 최대 91 연결 BUILD FAILED, 상한 8 최대 72 연결 BUILD SUCCESSFUL. `./gradlew build --rerun-tasks` 3회 연속 성공, 매번 815개 통과·실패 0, 연결 고갈 메시지 없음. `script/docs-check.sh` 통과.
- [ ] push·PR은 하지 않았다. `common` 변경은 모든 서비스에 영향이 있어 팀 전체 리뷰가 필요하다(`docs/guides/git-workflow.md` 1절).
- 면접 포인트: 공유 자원의 한도와 캐시 수명의 결합, 풀 크기 대신 컨텍스트 수에 상한을 둔 이유, 측정 도구를 먼저 만들어 전후를 같은 조건으로 비교한 방법.

## 다음 단계
1. 사용자 확인 뒤 push하고 PR을 연다(제목 예: `test(common): 통합 테스트 캐시 컨텍스트 상한으로 DB 연결 고갈 방지`). `common` 변경이므로 팀원 전체에게 리뷰를 요청한다.
2. 병합 뒤 계획 문서 4단계(재발 방지 규칙)가 이미 `docs/agents/rules/test.md`에 들어갔는지 확인하고 계획 상태를 `완료`로 바꾼다.

## 직접 해보기
1. `common/src/testFixtures/resources/spring.properties`를 잠시 다른 이름으로 바꾼다.
2. `script/test-db-connections.sh :order-service:test --rerun-tasks`를 실행해 연결이 10개씩 늘어 90개 안팎에서 실패하는 것을 본다(Docker 필요).
3. 파일을 되돌리고 다시 실행해 최대 70개대에서 통과하는지 확인한다.

## 메모
- 측정 도구는 로컬 도구다. 한 번에 한 모듈만 잰다. 측정 연결 자체도 DB 연결 1개를 쓴다.
- Hikari 풀 로그(DEBUG)와 Grafana `hikaricp_connections_*` 패널은 이번에 쓰지 않았다(런타임 서비스용).
- 풀 크기를 줄이는 B는 실험하지 않았다. 한 연결을 쥔 채 다른 연결을 기다리는 테스트가 막힐 수 있는 위험이 있다(미확인).
