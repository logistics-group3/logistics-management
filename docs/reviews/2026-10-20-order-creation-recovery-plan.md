# 주문 생성 장애 복구: 장애 지점과 상태별 판단표

- 작성일: 2026-10-20.
- 기준: `feat/docs-learning-evidence`, HEAD `e1ac9cc`와 로컬 문서 변경.
- 상태: 사용자가 설계대로 구현·기록·동일 F1/F2 검증을 승인했고 로컬 구현·전후 검증을 마쳤다. [구현 결과](order-creation-recovery/2026-10-20-implementation-results.md)와 ADR 0019가 현재 계약이며 아래 제안 단계의 설명은 이력이다.
- 실행 기록: [실험 기록 안내](order-creation-recovery/README.md), [F1·F2 개선 전 재현 결과](order-creation-recovery/2026-10-20-baseline-results.md). 실제 서버 종료·재기동으로 두 장애를 재현했으며 복구 기능 구현 전 보고에서 멈췄다.

## 1. 첫 버전의 목적

**접수 후 중단된 주문 생성 시도를 찾아, 배송 결과를 확인하고 안전하게 종료한다.**
첫 버전은 주문을 자동 재생성하거나 반드시 성공으로 끝내는 기능이 아니다. 원래 요청·가격·주소의 전체 스냅샷이 없으므로 추측해 주문을 만들지 않는다.
대상은 주문 생성뿐이다. 취소·삭제 Saga 복구, 영속 큐, 자동 재처리 스케줄러는 다음 범위다.

성공 기준은 두 장애에서 `주문 없음 + 활성 배송 없음 + 배송 생성 종료 표시 있음 + 생성 시도 ABORTED`가 확인되는 것이다.
배송이 이미 진행됐거나 판단 근거가 부족한 경우에는 복구 대기로 보존하고 사유를 보여 주는 것이 올바른 결과다.
논리 삭제된 배송·구간·시도와 복구 이력은 남긴다. 활성 행 0건과 물리 행 0건을 혼동하지 않는다.

## 2. 현재 코드로 확인한 전제

| 사실 | 근거 | 설계에 미치는 영향 |
|---|---|---|
| 진행 기록 커밋 후 메모리 실행기에 작업을 제출하고 202를 반환한다 | `OrderService.accept`, `begin`, `OrderWriter.start` | 접수 기록이 있어도 재시작 후 처리할 작업은 없을 수 있다 |
| I-09 완료 후 주문을 저장한다 | `OrderService.process` | 배송만 저장된 중단 구간이 있다 |
| 주문 INSERT·시도 COMPLETED·응답 스냅샷은 같은 트랜잭션이다 | `OrderWriter.save` | 주문 있음·시도 미완료는 정상 중단 결과로 간주하지 않는다 |
| 생성 기록은 요청 지문을 저장하며 원본 전체 요청은 저장하지 않는다 | `OrderCreation` | 현재 상품·주소를 조회해서 옛 주문을 재생성하지 않는다 |
| I-17은 ACTIVE·CLOSED·NONE과 배송 ID·상태를 제공한다 | delivery `DeliveryOrderResult` | 존재 여부와 업무 진행 상태를 함께 확인해야 한다 |
| 현재 order 어댑터는 I-17을 boolean으로 축약한다 | `DeliveryClientAdapter.isCreationClosed` | 복구에서는 전체 결과와 식별자 일치 검증이 추가로 필요하다 |
| I-13은 배송 상태와 관계없이 논리 삭제한다 | `DeliveryService.deleteByOrderId` | 상태 조회 뒤 I-13 호출만으로 안전한 복구를 만들 수 없다 |
| 기존 abort·requireRecovery·complete는 PROCESSING에서만 가능하다 | `OrderCreation.validateProcessing` | 복구 전용 상태 전이와 경쟁 제어가 필요하다 |

I-17의 `ACTIVE`는 삭제되지 않은 배송이 있다는 뜻이다. `PICKUP_WAITING`뿐 아니라 배송 중·완료·취소도 포함할 수 있다.
`NONE`은 현재 배송과 종료 표시를 찾지 못했다는 뜻이다. 지연된 I-09가 이후 도착할 수 있어 종료의 증거가 아니다.
`CLOSED`는 종료 표시가 있어 같은 orderId의 새 배송 생성을 막는 상태다.

## 3. 장애 지점 F1: 202 접수 후, I-09 호출 전

### 검증할 가설

진행 기록과 202 응답이 있어도 실행할 작업이 메모리에만 있으면 프로세스 종료 후 PROCESSING으로 남는다.

### 정확한 중단 위치

`OrderService.accept`가 실행기에 제출한 작업의 시작 부분, `process`가 I-09를 호출하기 전에 테스트 전용 대기 지점을 둔다.
`OrderWriter.start` 커밋, 대기 지점 도달, 클라이언트 202 수신을 모두 확인한 뒤 전용 order 프로세스를 강제 종료한다.
`start`와 `submit` 사이에서 멈추면 202 수신을 보장하지 못하므로 이번 대표 시나리오와 구분한다.

### 재현 순서와 증거

1. 정상 주문이 가능한 격리 환경과 새 멱등키·실험 ID를 준비한다.
2. 해당 요청 하나에만 F1 대기를 활성화하고 주문을 보낸다. 고정 sleep 대신 도달 신호로 동기화한다.
3. 202 본문 orderId, attemptId, F1 도달 로그, I-09 호출 0회, 주문 0건을 저장한다.
4. order 컨테이너만 SIGKILL로 종료하고 종료 상태·종료 코드를 저장한다. 정상 종료의 30초 대기는 사용하지 않는다.
5. DB·delivery를 유지하고 장애 주입을 해제한 order를 재기동한다. 준비 완료 뒤 O-07과 동일 키 재요청 결과를 수집한다.

| 관찰 항목 | 개선 전 예상 | 개선 후 목표 |
|---|---|---|
| 생성 시도 | PROCESSING 1건 | ABORTED 1건 |
| 활성 주문 | 0건 | 0건 |
| I-17 | NONE | CLOSED |
| 활성 배송 | 0건 | 0건 |
| 같은 키·같은 본문 재요청 | 같은 orderId, 202 | 409 ORDER_CREATION_ABORTED |

위 표는 설계 당시 예상이다. 2026-10-20 실제 서버 종료·재기동에서 개선 전 결과를 확인했다. 상세 명령과 증거는 재현 결과 문서를 따른다.
복구 시 NONE을 그대로 ABORTED로 바꾸지 않는다. 조건부 종료 명령으로 지연 생성까지 차단하고 CLOSED를 다시 확인한다.

## 4. 장애 지점 F2: 배송 커밋 후, 주문 저장 전

### 검증할 가설

배송 생성은 성공했지만 주문 저장 전에 order가 종료되면 주문 없는 활성 배송이 남는다.

### 정확한 중단 위치

`OrderService.process`의 `UUID deliveryId = createDelivery(...)` 반환 직후, `OrderWriter.save` 호출 전에 테스트 전용 대기 지점을 둔다.
delivery의 실제 PostgreSQL 커밋과 I-17 ACTIVE·PICKUP_WAITING을 확인한다. 응답을 받은 order 메모리에는 deliveryId가 있지만 생성 기록에는 아직 없을 수 있다.
응답 유실 실험은 이 대표 시나리오와 별개다. 이번 F2는 배송 성공 응답을 받은 뒤의 종료를 검증한다.

### 재현 순서와 증거

1. 새 키로 주문을 보내 202와 F2 도달을 확인한다. 배송 담당자의 구간 출발은 실행하지 않는다.
2. order DB에서 PROCESSING·주문 0건, I-17에서 배송 1건·PICKUP_WAITING을 확인한다.
3. order만 SIGKILL로 종료한다. delivery와 DB는 유지한다.
4. 장애 주입을 해제한 order를 재기동한 뒤 같은 식별자로 남은 상태를 확인한다.
5. 복구가 구현되면 조건부 정리와 CLOSED 재확인을 거쳐 생성 시도를 닫는다.

| 관찰 항목 | 개선 전 예상 | 개선 후 목표 |
|---|---|---|
| 생성 시도 | PROCESSING 1건 | ABORTED 1건 |
| 활성 주문 | 0건 | 0건 |
| I-17 | ACTIVE, PICKUP_WAITING | CLOSED |
| 활성 배송·구간 | 배송 1건·구간 존재 | 모두 0건, 논리 삭제 이력 보존 |
| 뒤늦은 같은 orderId I-09 | 기존 배송 반환 가능 | DELIVERY_CREATION_CLOSED 거절 |

두 시나리오의 정상 대조군은 대기 지점을 풀면 COMPLETED·주문 1건·배송 1건으로 끝나는 것이다.
서비스 시작 직후의 Eureka 등록 지연을 주문 복구 장애와 섞지 않도록 준비 완료를 확인한 뒤 주문한다.

## 5. 복구 판단의 선행 조건

아래 조건을 충족하기 전에는 배송 변경을 호출하지 않는다.

1. MASTER 권한, 대상 attemptId·orderId·주문자·예상 상태·복구 요청 ID를 검증한다. 오래된 화면의 조회 결과는 실행 근거로 사용하지 않는다.
2. order DB의 같은 생성 기록을 잠그고 논리 삭제 이력까지 포함해 주문 유무·상태를 재확인한다. 일반 조회의 논리 삭제 필터로 사라진 주문을 “없음”으로 취급하지 않는다.
3. 주문 저장과 복구가 같은 생성 기록 잠금을 사용한다. 저장이 먼저 완료되면 복구를 중단한다. 복구가 먼저 실행권을 얻으면 원래 작업이 이후 주문 저장·시도 상태 변경을 할 수 없게 한다.
4. 제안은 PROCESSING을 RECOVERY_REQUIRED로 전환하면서 영속 복구 작업을 함께 기록하는 방식이다. 이미 RECOVERY_REQUIRED인 건은 별도 복구 요청 ID로 중복 실행을 막는다. 상태 전이·복구 사유는 구현 전 확정한다.
5. 원래 작업은 복구로 실행권을 잃으면 원격 보상까지 진행하지 않고 종료하도록 바꿔야 한다. DB 저장 차단만으로 이미 전송된 HTTP 요청을 취소할 수는 없다.
6. 첫 실험은 원래 order 프로세스 종료가 확인된 F1·F2에 한정한다. 원래 무조건 삭제 I-13이 이미 전송돼 결과가 불명확한 사례는 자동 정리 대상에서 제외한다. 실행 중인 일반 작업의 강제 인계는 별도 검증 대상이다.

대기 시간이 길다는 사실은 조회 목록에 올릴 이유일 뿐, 작업 종료 증거가 아니다.
복구 실행권은 원격 호출 동안 DB 트랜잭션을 잡아 두는 방식으로 유지하지 않는다. PostgreSQL 행 잠금은 트랜잭션 종료 시 해제되므로, 실행권과 처리 단계를 DB에 남겨야 한다([공식 문서](https://www.postgresql.org/docs/16/explicit-locking.html)).

## 6. 상태별 복구 판단표

기준: **order의 로컬 상태를 먼저 확정하고, 복구 실행권을 확보한 뒤 배송 결과를 판단한다.**
표의 조치는 복구 기능 구현 후의 제안이다. 현재 운영 명령이 아니다.

| 생성 시도 | 로컬 주문 | I-17·배송 상태 | 판단·허용 조치 | 종료 결과 |
|---|---|---|---|---|
| PROCESSING | 없음 | 원래 작업이 정상 실행 중 | 관찰만 한다. 경과 시간만으로 인계하지 않는다 | PROCESSING 유지 |
| PROCESSING / RECOVERY_REQUIRED | 없음 | NONE | 조건부 배송 종료를 실행해 이후 생성도 막는다. CLOSED 재확인 후 로컬 재검증 | ABORTED |
| PROCESSING / RECOVERY_REQUIRED | 없음 | ACTIVE + PICKUP_WAITING, 모든 구간 WAITING | 배송 내부에서 조건 재검사와 논리 삭제·종료 표시를 원자적으로 실행한다. CLOSED 재확인 | ABORTED |
| PROCESSING / RECOVERY_REQUIRED | 없음 | CLOSED | 원래 작업 차단과 로컬 주문 부재를 재확인하고 복구 이력과 함께 종료한다 | ABORTED |
| PROCESSING / RECOVERY_REQUIRED | 없음 | ACTIVE + 집하 이후 진행 / DELIVERED | 배송 변경 금지. 실제 운송의 후속 업무 판단이 필요하다 | RECOVERY_REQUIRED, 수동 확인 |
| PROCESSING / RECOVERY_REQUIRED | 없음 | ACTIVE + CANCELED 또는 상태·구간 불일치 | 첫 버전에서는 이력 조사 대상으로 남긴다. 취소됐다는 이유로 정리하지 않는다 | RECOVERY_REQUIRED, 수동 확인 |
| PROCESSING / RECOVERY_REQUIRED | 없음 | 타임아웃·5xx·잘못된 식별자·응답 | 완료 판단 금지. 확인 실패를 이력에 남긴다 | RECOVERY_REQUIRED |
| PROCESSING / RECOVERY_REQUIRED | 있음 또는 논리 삭제 이력 있음 | 무엇이든 | 정상 저장 트랜잭션과 맞지 않는 조합이다. 주문·배송 자동 변경 금지 | 불일치 조사, 생성 상태 보존 |
| COMPLETED | 있음 | 연결된 같은 배송 | 생성 복구 불필요. 주문·배송의 후속 상태는 취소·삭제 계약으로 판단한다 | COMPLETED 유지 |
| COMPLETED | 없음 / 논리 삭제 / 배송 불일치 | 무엇이든 | 이후 정상 삭제 이력을 먼저 확인한다. 이력 없으면 이상 상태로 조사한다. 생성 재실행 금지 | COMPLETED 유지, 별도 조사 |
| ABORTED | 없음 | CLOSED 또는 정상 거절로 종료 | 이미 종료된 시도다. 같은 복구 요청 결과를 재사용한다. ABORTED는 정상 4xx 거절 시 종료 표시가 없을 수도 있다 | ABORTED 유지 |
| ABORTED | 있음 또는 활성 배송 발견 | ACTIVE 등 | 종료된 기록과 실제 데이터가 모순된다. 조용히 삭제하지 않는다 | 상태 보존, 수동 확인 |
| 시도 없음 / 논리 삭제 / 식별자 불일치 | 무엇이든 | 무엇이든 | 대상 부재·충돌로 거절한다. 원격 변경 없음 | 변경 없음 |

복구 완료는 첫 조회 결과가 아니라 마지막 로컬 트랜잭션의 상태 재검증으로 결정한다.
RECOVERY_REQUIRED → ABORTED 때 기존 failureReason은 감사 이력에 보존한 후 현재 행에서는 비운다. 현재 DB CHECK는 RECOVERY_REQUIRED 외 상태에서 failure_reason이 NULL이어야 한다.
종료 실패 코드는 기존 SERVICE_UNAVAILABLE 재사용을 우선 검토하고, 운영자 정리 사유는 별도 이력으로 남긴다. O-07·같은 키 재요청의 기존 응답 의미를 유지하는 방향이다.

## 7. 배송의 조건부 종료 계약이 필요한 이유

`I-17 PICKUP_WAITING 조회 → I-13 삭제` 사이에 담당자가 출발시킬 수 있다. 현재 I-13은 이를 재검사하지 않는다.
NONE 조회 후에도 지연된 I-09가 배송을 저장할 수 있다. 따라서 NONE 분기에도 같은 조건부 명령이 필요하다.

제안: 복구용 조건부 종료 내부 계약을 별도로 협의한다. API 번호·URL은 아직 확정하지 않는다. 기존 I-13을 일괄 변경하면 완료 배송 삭제 O-06에 영향을 주므로 그대로 대체하지 않는다.

| delivery의 잠금 안 상태 | 조건부 종료 결과 |
|---|---|
| 배송 없음·종료 표시 없음 | 종료 표시 저장 → CLOSED |
| 이미 종료됨 | 같은 CLOSED 반환 |
| 같은 배송, PICKUP_WAITING, 모든 구간 WAITING | 배송·구간 논리 삭제와 종료 표시를 같은 트랜잭션으로 저장 → CLOSED |
| 진행·완료·취소·구간 불일치·예상 배송 ID 불일치 | 거절. 배송과 종료 표시 모두 변경하지 않음 |

잠금 순서는 기존 I-09·I-13과 맞춰 `orderId advisory lock → 배송 행 잠금`으로 제안한다.
배송 출발 D-05도 같은 배송 행 잠금을 사용하므로, 출발이 먼저면 정리를 거절하고 정리가 먼저면 뒤의 출발을 거절해야 한다.
delivery를 조회할 때 order DB 잠금은 유지하지 않는다. 두 DB를 하나의 트랜잭션으로 묶는 보장을 주장하지 않는다.

이 계약이 합의되기 전에는 F2의 ACTIVE 배송과 F1의 NONE을 일반 운영에서 안전하게 정리할 수 있다고 주장하지 않는다.
현재 I-17 CLOSED를 확인하는 읽기·표시 기능부터 구현하는 것은 별도 작은 범위로 가능하다.

## 8. 중복 복구와 복구 도중의 중단

- `recoveryRequestId`는 운영자가 버튼을 반복 눌러도 유지한다. 같은 ID·같은 내용은 같은 작업을 조회·재개하고, 내용이 다르면 충돌로 거절한다.
- 같은 attemptId에 서로 다른 미완료 복구 작업이 동시에 실행되지 않도록 DB 제약과 원자적 획득으로 막는다. 두 실행기가 재개하려 할 때도 실행 소유권·버전을 확인한다.
- 처리 단계는 조회·실행권 획득·배송 결과 확인·조건부 종료·CLOSED 확인·로컬 종료로 영속 기록한다. 구체 컬럼은 구현 설계에서 확정한다.
- 조건부 종료 응답이 유실되면 I-17로 재확인한다. CLOSED일 때만 종료하고, 조회도 실패하면 복구 대기를 유지한다.
- 배송 정리 후 order DB 저장이 실패하면 배송은 CLOSED·시도는 RECOVERY_REQUIRED로 남을 수 있다. 같은 복구 작업을 다시 실행해 마지막 로컬 종료만 완료한다.
- 실행권 만료 시각만 보고 다른 작업자에게 넘기지 않는다. 첫 버전은 종료가 확인된 작업의 명시적 재개부터 지원하고 자동 lease 회수는 별도 설계한다.
- 복구 성공 뒤 같은 키 재요청은 기존 ABORTED의 409다. 사용자가 실패 종료를 확인한 뒤 새로운 업무 요청으로 주문할 때만 새 키를 쓴다.

## 9. 시험 목록과 완료 기준

| ID | 검증할 것 | 필요한 환경·통제 | 통과 조건 |
|---|---|---|---|
| C0 | 정상 처리 대조군 | 동일 시드, 장애 대기 해제 | COMPLETED·주문 1·배송 1 |
| F1 | 접수 후 실행 작업 유실 | 작업 시작 대기 + 실제 프로세스 종료 | 개선 전 잔류 재현, 개선 후 CLOSED·ABORTED·활성 주문/배송 0 |
| F2 | 배송 커밋 후 주문 저장 전 중단 | 배송 반환 후 대기 + 실제 프로세스 종료 | 개선 전 고아 배송 재현, 개선 후 논리 삭제·CLOSED·ABORTED |
| R1 | 복구 버튼 동시 실행 | 동일/상이 요청 ID 각각 동시 요청 | 복구 작업 하나만 획득, 중복 부작용 없음 |
| R2 | 원래 저장과 복구 경쟁 | 두 순서를 제어하는 barrier | 저장 선행은 복구 거절, 복구 선행은 원래 저장·후속 무조건 보상 차단 |
| R3 | 배송 출발과 정리 경쟁 | 실제 delivery DB, 두 순서 제어 | 출발 선행이면 배송 보존, 정리 선행이면 출발 거절 |
| R4 | NONE 조회 후 늦은 I-09 | 배송 저장 직전 대기 | 종료 선행은 생성 거절, 생성 선행은 잠금 안 조건 판단 |
| R5 | 조건부 종료 응답 유실·I-17 장애 | HTTP 응답 지연/차단 | CLOSED 확인 때만 종료, 확인 불가 시 복구 대기 |
| R6 | 배송 정리 후 로컬 종료 실패 | 로컬 저장 실패 주입 | 같은 복구 요청으로 재개, 이력·키 보존 |
| R7 | 완료·삭제·모순 상태 보호 | 로컬 상태 조합 fixture | 기존 정상 주문 삭제/알림 재발행 없음, 모순은 수동 확인 |
| R8 | 복구 권한 | 미로그인·일반 USER·MASTER | 계약대로 차단/허용, 일반 사용자는 기존 O-07 범위 유지 |

F1·F2의 예외를 던지는 단위 테스트만으로 프로세스 종료 복구를 검증했다고 쓰지 않는다.
기능 단위·PostgreSQL 통합·실제 Gateway/Feign/프로세스 장애 시험·브라우저 E2E 결과를 각각 기록한다.
진행 중 외부 요청을 완전히 통제하지 못한 실행은 해당 불변식 검증에서 제외하고 제한점을 기록한다.

## 10. 다음 설계·실행 순서

1. 이 판단표에서 첫 버전이 안전한 중단 복구라는 점과 delivery 조건부 종료 계약을 검토한다.
2. 선택한 계약을 SA·서비스 ADR·협업 창구에 반영할 준비를 한다. 현재 문서는 제안이며 기존 합의를 바꾸지 않는다.
3. 격리 환경·요청별 장애 대기·증거 수집 도구를 준비하고 정상 대조군과 개선 전 F1·F2를 재현한다.
4. 복구 실행권·조회·조건부 종료·감사 이력을 구현하고 같은 실험을 다시 수행한다.
5. 결과가 확보되면 기존 운영 화면에 연결한다. UI에서 검증되지 않은 상태 변경을 직접 허용하지 않는다.

## 참고

- [OrderService](../../order-service/src/main/java/com/sparta/logistics/order/application/OrderService.java)
- [OrderWriter](../../order-service/src/main/java/com/sparta/logistics/order/application/OrderWriter.java)
- [OrderCreation](../../order-service/src/main/java/com/sparta/logistics/order/domain/OrderCreation.java)
- [비동기 접수 결정](../services/order/decisions/0010-order-creation-async-acceptance.md), [CLOSED 자동 종료 결정](../services/order/decisions/0011-order-creation-auto-abort-on-closure.md)
- [DeliveryService](../../delivery-service/src/main/java/com/sparta/logistics/delivery/application/DeliveryService.java), [DeliveryCreationService](../../delivery-service/src/main/java/com/sparta/logistics/delivery/application/DeliveryCreationService.java)
- [배송 계약](../services/delivery/README.md), [기존 실패 처리](../services/order/failure-handling.md)
