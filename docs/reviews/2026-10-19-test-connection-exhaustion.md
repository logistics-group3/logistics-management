# 통합 테스트의 PostgreSQL 연결 고갈 (STAR 기록)

- 날짜: 2026-10-19
- 작성: Junhyun An 작업
- 용도: 기술 블로그 소재. 확인한 사실과 미확인 사항을 구분해 적었다. 요약 기록은 [TS-025](../troubleshooting.md), 정식 해결은 [작업 계획](2026-10-19-test-connection-exhaustion-plan.md)이다.

## 상황 (Situation)

주문 생성을 비동기 접수로 바꾸는 작업(이슈 #16)을 마치고 order-service 전체 테스트를 새로 실행했다. 이 서비스의 통합 테스트는 Testcontainers로 띄운 PostgreSQL 16 한 개를 모든 테스트 클래스가 공유한다(싱글턴 컨테이너 패턴). Spring Boot 테스트는 설정이 같은 테스트끼리 ApplicationContext를 캐시해 재사용한다.

## 과제 (Task)

`./gradlew :order-service:test --rerun-tasks`가 463개 중 18개 실패했다. 실패가 우리가 고친 코드(주문 접수, 진행 조회)가 아니라 주문 저장소 테스트에서 났으므로, 변경이 깨뜨린 것인지 환경 문제인지 가려야 했다. 완료 기준은 원인을 확인하고 전체 테스트가 통과하는 것이다.

## 행동 (Action)

1. **실패의 모양을 봤다.** 테스트 결과 XML을 집계하니 18개 실패가 모두 `OrderRepositoryIntegrationTest` 한 클래스였다. 비동기 접수 코드와 직접 관련이 없고, 그 클래스의 테스트 본문이 아니라 컨텍스트 로드에서 실패했다.
2. **원인 줄까지 내려갔다.** 실패 메시지의 `Caused by` 체인을 따라가면 `entityManagerFactory` → `flywayInitializer` → `FlywaySqlException: Unable to obtain connection from database`, 마지막 줄이 `FATAL: sorry, too many clients already`였다. 테스트 로직이 아니라 DB가 새 연결을 거절한 것이다.
3. **가설을 세웠다.** 컨테이너 하나를 여러 컨텍스트가 공유하고, 컨텍스트는 캐시되어 닫히지 않으며, Hikari 기본 풀은 컨텍스트당 연결 10개를 유지한다. 컨텍스트가 10개를 넘으면 PostgreSQL 기본 `max_connections` 100에 닿는다. 마지막 실행의 로그에서 `HikariPool-12`까지 풀이 만들어진 것을 확인해 한 JVM에 컨텍스트가 12개 있음을 확인했다.
4. **변수를 하나만 바꿔 검증했다.** order-service 테스트 전용 `application.properties`에 풀 상한(`maximum-pool-size=4`, `minimum-idle=1`)만 추가하고 같은 명령을 다시 실행했다. 팀 공용인 `common`의 테스트 부모 클래스는 건드리지 않았다.
5. **혼동할 만한 다른 실패를 구분했다.** 병렬로 일한 에이전트들이 `Could not write XML test results`를 보고했다. 같은 `build` 디렉터리에 Gradle을 동시에 실행해 생긴 별개의 문제(추정)이며, 연결 고갈의 메시지와 다르다. 이번 검증은 모든 에이전트가 끝난 뒤 단독으로 실행했다.

## 결과 (Result)

- 같은 코드에서 설정 파일 하나를 추가한 뒤 `./gradlew :order-service:test --rerun-tasks`가 463개 모두 통과했다(실패 18 → 0, BUILD SUCCESSFUL 34초).
- 변수를 하나만 바꿔 실패가 사라졌으므로 풀 크기가 원인의 핵심이라고 판단한다.
- **미확인**: 컨테이너의 실제 `max_connections` 값(공식 문서 기본값 100을 가정), 변경 전 컨텍스트 수와 이번 변경이 몇 개를 더했는지, 다른 모듈(delivery 4개, company 3개, user 6개, hub 5개의 통합 테스트 클래스)의 여유. 새 비동기 통합 테스트가 한계를 넘긴 계기라는 것은 추정이다.
- 이 해결은 한 서비스의 임시 방어다. 컨텍스트가 계속 늘면 다시 닿을 수 있어 정식 대책을 [작업 계획](2026-10-19-test-connection-exhaustion-plan.md)으로 세웠다.

## 배운 점 (블로그 메모)

- 무관해 보이는 테스트의 실패는 코드가 아니라 **공유 자원**의 신호일 수 있다. 에러 메시지의 맨 아래 `Caused by`가 진짜 원인이다.
- "캐시된 것은 닫히지 않는다": 테스트 컨텍스트 캐시와 커넥션 풀이 결합하면, 테스트를 추가할 때마다 연결 사용량이 눈에 안 보이게 늘어난다.
- 가설은 변수 하나만 바꿔 검증한다. 풀 크기만 바꿨더니 18개가 0개가 됐다.
- 근거 수준을 구분한다. 실제로 본 것(오류 메시지, 풀 12개, 463개 통과)과 가정한 것(`max_connections` 100, 변경 전 컨텍스트 수)을 문서에서 갈라 둔다.
