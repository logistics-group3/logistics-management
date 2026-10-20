# 주문 생성 복구 구현과 F1·F2 전후 비교

## 결과

**F1·F2를 동일한 코드 경계에서 다시 재현하고, 운영자 복구 API로 두 시도를 ABORTED·배송 CLOSED로 안전하게 종료했다.**
활성 주문·배송·구간은 모두 0건이며 같은 주문 키는 409, 같은 복구 요청은 같은 결과를 반환했다. 이후 같은 orderId의 배송 생성도 409로 거절됐다.
기존 정상 주문 대조군 두 건은 COMPLETED·주문 1·배송 1·구간 2로 통과했다. 원본 주문을 재생성하는 기능은 아니다.

## 구현한 계약과 보호 장치

| 영역 | 구현 |
|---|---|
| 조사 | O-08 MASTER 전용 미해결 생성 목록 |
| 실행 | O-09 같은 복구 ID·내용 재사용, 예상 상태·attemptId·실행 중단 확인 |
| 이력 | O-10 최초 사유·예상 상태·요청자, 실행 ID, 단계별 관찰·조치·완료/보류 |
| 원래 작업 차단 | 정상 Writer와 같은 생성 기록 행 잠금. 복구 인계 뒤 늦은 저장 거절 |
| 복구 중단·재개 | RUNNING 중복은 관찰만 수행. 이전 실행 중단 확인과 실행 ID 비교 후 명시적 재개, 과거 작업자 저장 차단 |
| 배송 정리 | I-18 orderId advisory lock → 배송 행 잠금. 집하·모든 구간 대기·예상 ID를 잠금 안에서 재검사 |
| 최종 확정 | I-17 CLOSED·로컬 주문 및 논리 삭제 이력 부재를 재확인하고 ABORTED와 완료 이력을 같은 트랜잭션으로 저장 |
| 확인 불가 | RECOVERY_REQUIRED·DEFERRED 유지. 자동 재생성·시간만으로 만료 없음 |

기존 O-06의 완료 배송 삭제 I-13은 유지했다. 생성 실패 보상은 I-18로 전환해 늦은 원래 작업의 무조건 삭제도 제거했다.
복구는 새 Slack 이벤트를 발행하지 않는다. 최초 실행 중단은 운영자가 확인하는 조건이며 서버가 다른 프로세스 종료를 증명하는 기능은 없다.
최초 요청자와 같은 MASTER만 같은 복구 요청을 재개한다. 자동 lease 회수·복구 소유권 이전은 제공하지 않는다.

[ADR 0019](../../decisions/0019-order-creation-operator-recovery.md), [운영 API](../../services/order/recovery.md), [V7 SQL](../../../order-service/src/main/resources/db/migration/V7__add_order_creation_recovery.sql)을 기준으로 읽는다.

## 실제 F1·F2 비교

동일 시드는 서울 허브 업체 2개·상품 1개·수량 2·MASTER다. 실험마다 키·requestNote·orderId는 분리했다.
F1은 `OrderService.run`의 process 호출 전, F2는 `createDelivery` 반환 뒤 주문 객체 생성·저장 전이다. 소스 문장으로 줄 위치를 찾고 JVM 디버그 정보와 대조했다.
처리 스레드만 JDI로 중단하며 실제 order 컨테이너를 SIGKILL한다. DB·delivery는 유지하고 order만 재기동한다.

| 항목 | F1 개선 전 | F1 복구 후 | F2 개선 전 | F2 복구 후 |
|---|---|---|---|---|
| 생성 시도 | PROCESSING | ABORTED | PROCESSING | ABORTED |
| 활성 주문 | 0 | 0 | 0 | 0 |
| 활성 배송 | 0 | 0 | 1, PICKUP_WAITING | 0 |
| 활성 구간 | 0 | 0 | 2 | 0 |
| I-17 | NONE | CLOSED | ACTIVE | CLOSED |
| 생성 종료 표시 | 0 | 1 | 0 | 1 |
| 동일 주문 키 | 202·같은 ID | 409 ORDER_CREATION_ABORTED | 202·같은 ID | 409 ORDER_CREATION_ABORTED |
| 이후 같은 ID 배송 생성 | 차단 근거 없음 | 409 DELIVERY_CREATION_CLOSED | 기존 배송 반환 가능 | 409 DELIVERY_CREATION_CLOSED |

F2의 배송·구간은 물리 삭제하지 않았다. 논리 삭제 이력과 감사 이벤트를 보존했다.
기존 개선 전 데이터 4건도 V7 반영 뒤 O-07 상태가 유지됨을 확인했다. 전역 미해결 시도를 일괄 정리한 실험은 아니다.

| 실행 | KST | 결과 |
|---|---|---|
| [AFTER-C0-F1-02](runs/20261020-AFTER-C0-F1-02.md) | 11:47:41~11:47:44 | 대기 해제 → 정상 완료, 동일 키 201 |
| [AFTER-C0-F2-02](runs/20261020-AFTER-C0-F2-02.md) | 11:47:44~11:47:45 | 대기 해제 → 정상 완료, 동일 키 201 |
| [AFTER-F1-02](runs/20261020-AFTER-F1-02.md) | 11:47:45~11:48:09 | 재현 → 복구 → ABORTED·CLOSED |
| [AFTER-F2-02](runs/20261020-AFTER-F2-02.md) | 11:48:09~11:48:29 | 재현 → 복구 → ABORTED·CLOSED |

F1 orderId는 `47b96d83-e767-4c91-95dc-b17d043d95d5`, F2는 `51619fc7-25d1-4add-8787-6c665da4ea0c`다.
두 의도한 종료는 exit 137·OOMKilled=false였다. 같은 복구 요청 재사용은 실행 ID·이벤트를 포함한 결과가 같았고, 완료한 대상은 조사 목록에서 빠졌다.
이력 조회의 최초 요청 정보 필드를 보강한 뒤에도 최종 이미지에서 두 복구 결과·데이터 상태·사유·예상 상태·요청자를 다시 조회했다.

## 실제 실행 명령

저장소 루트에서 실행했다. 아래는 주요 호출이며 실제 argv·시각·종료 코드·정제된 stdout/stderr와 HTTP 영수증은 실행별 원문에 있다.

```bash
# 기능이 없는 코드에서 새 조건부 종료 API의 404 실패 확인
./gradlew :delivery-service:test --tests '*DeliveryCreationRecoveryIntegrationTest'
# 구현 후 대상 검증과 전체 회귀
./gradlew :order-service:test --tests '*CreationRecovery*'
./gradlew :delivery-service:test --tests '*CreationRecovery*'
./gradlew build
npm --prefix frontend run build
npm --prefix frontend run lint
npm --prefix frontend run format:check
npm --prefix frontend run test
# 보존한 격리 DB에 구현 이미지 반영
python3 script/recovery-experiment/experiment.py upgrade
# 전용 JVM 메모리 제한 적용 뒤 주 비교 실행
python3 script/recovery-experiment/scenario.py C0-F1 20261020-AFTER-C0-F1-02
python3 script/recovery-experiment/scenario.py C0-F2 20261020-AFTER-C0-F2-02
python3 script/recovery-experiment/scenario.py F1 20261020-AFTER-F1-02 --recover
python3 script/recovery-experiment/scenario.py F2 20261020-AFTER-F2-02 --recover
# 실제 Gateway 권한·이력·V7·최종 DB 검증과 전용 스택 종료
python3 script/recovery-experiment/finalize_after.py --stop-dedicated-stack
```

최초 컴파일의 잘못된 import와 테스트 헬퍼 이름 충돌, fixture의 response_snapshot CHECK·테스트 스키마·Mockito 재설정 오류도 해당 실패 명령 출력에 남겼다.
검증을 약화하지 않고 실제 패키지·DB 제약·기존 테스트 설정에 맞춰 수정했다. 최초 실패 XML과 이후 성공 결과를 구분했다.

## 코드·DB·HTTP 검증

| 검사 | 결과와 범위 |
|---|---|
| 최종 `./gradlew build` | 성공, 47초. 58개 작업 중 5개 실행·53개 UP-TO-DATE. order 484개 재실행, 실패·오류·skip 0 |
| 전체 JUnit XML 집계 | 857개, 실패·오류·skip 0. 재사용된 결과 포함. delivery 93개는 앞선 동일 핵심 로직의 전체 빌드에서 재실행 |
| 프런트 build·lint·format:check | 모두 통과. 프런트 소스 변경 없음 |
| 프런트 단위 테스트 | 56개 통과 |
| 실제 프로세스 장애·복구 | F1·F2 각각 재현·복구 통과, 정상 대조군 각각 통과 |
| 실제 Gateway 인가 | 미로그인 401, USER 403, MASTER 200 |
| 실제 V7 | 기존 전용 볼륨에 적용 성공, 기존 4건의 O-07 상태 보존 |

신규 회귀는 중복 실행·과거 실행 ID·늦은 정상 저장·배송 출발 선행/정리 선행·이미 종료·ID 불일치·조회 실패·종료 응답 미확인·로컬 종료 실패 후 재개·역할·중단 확인을 다룬다.
배송 출발 경쟁은 실제 PostgreSQL 두 트랜잭션의 실행 순서를 통제해 양쪽을 검증했다. 그 테스트의 다른 서비스 조회는 Mock이다.
F1·F2 본 실험은 실제 Gateway·JWT·Feign·서비스·PostgreSQL을 사용했다. 브라우저 E2E는 실행하지 않았고 복구 UI는 이번 범위에 없다.

## 제외한 실행과 메모리 설정 변경

초기 구현 후 C0 두 건과 AFTER-F1-01은 통과했다. [AFTER-F2-01](runs/20261020-AFTER-F2-01.md)은 의도한 kill 전에 Docker oom 이벤트·OOMKilled=true로 종료돼 제외했다.
프로젝트·볼륨 분리만으로 공유 VM의 자원 영향을 막지 못한 설정이었다. 기존 UI 컨테이너 일부 재시작도 관찰했으며 원인·영향을 확정하지 못했다.
전용 스택만 내린 뒤 전용 일반 JVM heap 128MiB·컨테이너 448MiB, order heap 192MiB·컨테이너 512MiB로 제한했다. 메타스페이스·코드 캐시·스택·CPU 수도 제한하고 자동 재시작을 껐다.
기본 Compose·기존 UI 설정·업무 시간 예산은 바꾸지 않았다. 상세 조치와 실제 메모리 관찰은 [TS-031](../../troubleshooting.md#ts-031-격리-프로젝트의-주문-장애-실험이-의도한-종료-전에-oom으로-중단됨)을 따른다.
대조군과 F1·F2를 모두 새 runId로 다시 수행했다. 개선 전후 JVM 자원 설정은 다르므로 이번 표는 데이터 정합성과 복구 결과의 비교이며 성능 비교가 아니다.

## 보존·제약·다음 단계

전용 스택은 종료했다. `logistics-recovery-test_pgdata`·전용 환경 파일·전체 원문을 보존했다. 최종 확인 시 기존 컨테이너 12개 ID가 동일하고 모두 실행 중이었다.
이 확인은 실행 중인 컨테이너에 영향이 없었다는 뜻이 아니다. OOM 전후 일부 재시작 관찰을 기록에 포함했다.
자동 주문 재생성·자동 복구 스케줄러·다중 인스턴스 장시간 장애·복구 소유권 이전은 제공하거나 검증하지 않았다.
실제 복구 API는 사용자 확인을 전제로 하며, 운영자 UI 연결은 후속 작업이다. 현재 변경은 로컬 미커밋이며 푸시·PR 변경은 하지 않았다.

다음 검토에서는 새 API·V7·실행권과 조건부 종료 계약을 팀이 리뷰한다. 일반 API 응답으로 COMPLETED가 왔다는 것만으로 판단하지 않고 대상 DB·CLOSED·이력까지 함께 본다.

## 정제한 증거 묶음

[구현·검증 전체 증거 ZIP](implementation-evidence-20261020.zip)에 실제 명령·종료 코드·HTTP·DB·서비스 로그·실패 XML·최종 성공 XML·소스 스냅샷·환경 변경과 최종 도구를 묶었다.
[최종 검증 요약](runs/evidence/implementation-final-verification/summary.json), [정리 상태](runs/evidence/implementation-final-verification/cleanup.json), [JUnit 집계](runs/evidence/implementation-validation/junit-summary.json)도 별도로 읽을 수 있다.
환경 비밀값·JWT/Slack 토큰 패턴을 검사하고, 실제 환경 파일은 묶음에 넣지 않았다. 원문은 `.local/recovery-experiments/`에 계속 보존한다.
