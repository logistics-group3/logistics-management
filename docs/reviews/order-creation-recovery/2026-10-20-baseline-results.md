# 주문 생성 장애 F1·F2의 개선 전 재현 결과

## 결론

**현재 서비스 코드에서 F1·F2를 모두 재현했다.** order를 강제 종료한 뒤 재기동해도 생성 시도는 PROCESSING으로 남고, 같은 키 재요청은 같은 orderId의 202를 반환했다.
F1은 주문·배송 0건, F2는 주문 0건·집하 대기 배송 1건·활성 구간 2건이었다. 복구 기능은 구현하지 않았다.
대기 지점을 풀어 정상 완료하는 대조군도 각각 통과했다. 첫 대조군의 접수 503은 판정에서 제외하고 기록을 보존했다.

## 기준과 환경

- 실행일: 2026-10-20 KST, 환경 빌드·기동 11:16:04~11:16:34, 마지막 데이터 확인 11:21:21, 정리 확인 11:21:27.
- 브랜치·HEAD: `feat/docs-learning-evidence`, `e1ac9cc`. 시작·종료 시 서비스 소스·common·config-repo·기본 Compose의 diff가 없음을 확인했다.
- Docker Engine 29.8.2 / Desktop 4.95.0, Compose v5.6.0, Linux arm64 이미지.
- 서비스 Java 17.0.20.1, PostgreSQL 16.15. 호스트 디버거는 Java 21.0.12. Gradle 8.14.3의 Java 17 toolchain으로 Docker bootJar 빌드.
- 새 Compose 프로젝트 `logistics-recovery-test`와 전용 `logistics-recovery-test_pgdata` 볼륨을 사용했다. 기존 UI 테스트·개인 DB와 분리했다.
- DB 25432·Config 28888·Eureka 28761·Gateway 28080·Zipkin 29411·JDI 25005는 loopback에 바인딩했다.
- Config·Eureka·Gateway·업무 서비스 5개·DB·Zipkin을 띄웠다. 이번 증거 수집은 API·DB·로그·JDI로 수행했고 Prometheus·Grafana는 띄우지 않았다.
- 전용 환경 파일은 0600으로 새로 생성했다. 실제 order 컨테이너의 Webhook·Bot 토큰이 비고 DM=false인 것을 검사했다.
- 공개 API로 서울 허브의 테스트 업체 2개·상품 1개·배송담당자 1명과 MASTER 계정을 준비했다. 두 구간의 같은 허브 배송이다.

[환경·기동 명령](runs/evidence/setup/commands.jsonl), [정제한 override](runs/evidence/setup/compose.override.yml), [시드 명령·HTTP 기록 목록](runs/evidence/seed/commands.jsonl), [시드 식별자](runs/evidence/seed/manifest.json)를 보존했다.

## 중단 방법과 코드 변경 범위

서비스 코드에 대기 API·예외·sleep을 추가하지 않았다. JDI를 연결해 실제 코드의 다음 줄에서 생성 처리 스레드만 중단했다.

| 지점 | 실제 위치 | 중단 때 확인 |
|---|---|---|
| F1 | OrderService.java:144, run → process 호출 전 | 202 수신, 배송 어댑터 진입 0회, 주문 저장 진입 0회 |
| F2 | OrderService.java:156, createDelivery 반환 후 | 202 수신, 배송 어댑터 진입 1회, 주문 저장 진입 0회, I-17 ACTIVE·PICKUP_WAITING |

사용한 정책은 `SUSPEND_EVENT_THREAD`다. 이 정책은 이벤트를 발생시킨 스레드만 정지시킨다([JDI 공식 문서](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jdi/com/sun/jdi/request/EventRequest.html)).
호스트 제어 도구는 target 메서드나 값을 수정하지 않았다. 원격 메서드를 디버거로 호출하지도 않았다.
메서드 진입 횟수는 order 어댑터·Writer 기준이다. 이를 Feign 내부의 물리 HTTP 재시도 횟수 측정으로 해석하지 않는다.

## 실제 실행 명령

아래 순서로 저장소 루트에서 실행했다. 각 시나리오의 상세 Docker·SQL argv, HTTP 요청·응답, 시작·종료 시각은 개별 기록에 있다.

```bash
python3 -m py_compile script/recovery-experiment/experiment.py
mkdir -p .local/recovery-experiments/jdi
javac --add-modules jdk.jdi -d .local/recovery-experiments/jdi script/recovery-experiment/JdiGate.java
python3 script/recovery-experiment/experiment.py setup
python3 script/recovery-experiment/experiment.py seed
python3 script/recovery-experiment/scenario.py C0-F1 20261020-C0-F1-01
# 첫 대조군 실패 기록 보존 후 JDI 도구의 MethodEntryRequest를 특정 입구 중단점으로 변경했다.
javac --add-modules jdk.jdi -d .local/recovery-experiments/jdi script/recovery-experiment/JdiGate.java
python3 script/recovery-experiment/scenario.py C0-F1 20261020-C0-F1-02
python3 script/recovery-experiment/scenario.py C0-F2 20261020-C0-F2-01
python3 script/recovery-experiment/scenario.py F1 20261020-F1-01
python3 script/recovery-experiment/scenario.py F2 20261020-F2-01
python3 script/recovery-experiment/finalize.py --stop-dedicated-stack
```

F1·F2에서는 project/service label·알림 비활성·restart=no를 확인한 container ID에 `docker kill --signal=KILL <ID>`를 실행했다.
둘 다 exited·137·OOMKilled=false를 확인하고 `docker compose ... start order-service`로 같은 컨테이너의 JVM을 다시 띄웠다.
종료 후 자동 재시작에 기대지 않았다. DB·delivery는 중단하지 않았다.

## 시나리오별 관찰

| 실행 기록 | KST 시각 | 조작 | 이후 생성 시도 | 활성 주문 | 활성 배송·구간 | 동일 키 응답 |
|---|---|---|---|---:|---|---|
| [C0-F1-01](runs/20261020-C0-F1-01.md) | 11:17:34~11:17:58 | 초기 도구, 접수 503 | 기록 0건, 판정 제외 | — | — | 미실행 |
| [C0-F1-02](runs/20261020-C0-F1-02.md) | 11:18:32~11:18:35 | F1 대기 해제 | COMPLETED | 1 | 1건·2구간 | 201 |
| [C0-F2-01](runs/20261020-C0-F2-01.md) | 11:18:58~11:18:59 | F2 대기 해제 | COMPLETED | 1 | 1건·2구간 | 201 |
| [F1-01](runs/20261020-F1-01.md) | 11:19:14~11:19:37 | SIGKILL → 재기동 | PROCESSING | 0 | 0건·0구간 | 202 |
| [F2-01](runs/20261020-F2-01.md) | 11:20:06~11:20:30 | SIGKILL → 재기동 | PROCESSING | 0 | 1건·2구간 | 202 |

### F1

- orderId: `f7f1245e-b3f0-4baa-9498-414d9245bb33`.
- attemptId: `7f339019-ee6e-4525-8062-eb3dc6fd8760`.
- 중단 전·재기동 후·동일 키 재요청 후 모두 주문 0건, 배송 0건, I-17 NONE, 종료 표시 0건이었다.
- 생성 시도의 PROCESSING과 created_at·updated_at이 재기동 후에도 유지됐다. 같은 키 재전송은 새 작업을 만들지 않았다.

### F2

- orderId: `8cdebc87-7eb6-4b80-a631-928f6e719d0c`.
- attemptId: `7f1db3b3-8258-4eb4-904b-492a89cec526`.
- 주문은 물리 행까지 0건이고 배송 1건·활성 구간 2건이 남았다. I-17은 ACTIVE·PICKUP_WAITING이었다.
- 진행 기록의 delivery_id는 NULL이었다. 배송 ID가 order의 메모리에만 남은 구간이라 orderId 기반 확인이 필요함을 보여 준다.
- 종료 표시 0건·PROCESSING이 재기동과 같은 키 재요청 뒤에도 유지됐다.

두 장애 모두 재요청 뒤 10초 추가 관측에서 같은 상태였다. 11:21 최종 검증에서 O-07·DB·I-17을 다시 대조해 같은 상태를 확인했다.
[최종 상태 집계](runs/evidence/final-verification/summary.json)에 네 실행의 식별자와 결과를 남겼다.

## 첫 대조군 실패의 처리

첫 대조군은 202와 F1 도달 조건을 충족하지 못해 장애 재현으로 세지 않는다. 생성 기록도 0건이었다.
당시 도구는 MethodEntryRequest를 사용했고, 이후 특정 두 메서드 입구 중단점으로 바꿨다. 전역 진입 이벤트의 실행 영향 가능성은 있으나 원인으로 확정하지 않았다.
도구 변경과 JVM·클라이언트 준비 효과를 분리해 측정하지 않았으므로, 이 사례로 기동 직후 503의 원인을 해결했다고 주장하지 않는다.
처음 실패한 실행의 접수 응답·명령·로그·오류를 보존하고 새 runId로 대조군을 다시 실행했다.

## 결과의 의미와 한계

현재 비동기 접수는 시도 기록을 남기지만, 메모리 실행기의 작업을 재기동 후 재개하지 않는다. 같은 키 재요청도 기존 접수 결과를 돌려준다.
F1은 미처리 시도가 남는 사례이고 F2는 주문 없는 배송이 남는 사례다. 두 경우의 실제 상태를 확인했으며 안전한 정리 기능은 아직 없다.

- 실제 Gateway·JWT·Feign·서비스 5개·PostgreSQL·프로세스 종료를 사용했다. 외부 서비스 Mock으로 대신한 실험이 아니다.
- HTTP 응답 유실·배송 출발 경쟁·복구 중단·여러 order 인스턴스는 시험하지 않았다. 같은 허브 주문만 사용했다.
- 제어된 장애는 각각 한 번 재현했다. 모든 타이밍에서의 빈도나 발생 확률을 측정하지 않았다.
- JDI 사용 환경의 응답시간을 성능 개선 수치로 쓰지 않는다.
- Docker bootJar 빌드는 테스트 제외로 성공했다. 이번 단계에서 전체 Gradle 테스트·프런트 검사·브라우저 E2E는 실행하지 않았다. 과거 테스트 결과를 새 결과로 대체하지 않는다.

## 환경 정리와 다음 단계

전용 Compose만 down했다. 볼륨 `logistics-recovery-test_pgdata`는 보존했다. 시작 전 실행 중이던 UI 테스트·개인 DB 컨테이너 12개의 ID가 종료 후에도 동일함을 확인했다.
[정리 기록](runs/evidence/final-verification/cleanup.json), [실제 정리 명령](runs/evidence/final-verification/commands.jsonl), [보존 볼륨](runs/evidence/final-verification/preserved-volume.stdout)을 남겼다.
전용 `.env.recovery-test`·override·이미지와 전체 원문은 로컬에 보존했다. 생성 비밀값이 증거 파일에 없는지 검사했다.

현재 단계는 재현 결과 보고까지다. 사용자 요청대로 복구 기능 구현 전에 멈춘다.
다음 구현에서는 이 두 시도를 실제로 안전하게 닫되, 원래 작업 저장 차단과 delivery 조건부 종료 계약부터 다룬다.

## 증거 묶음과 도구 검사

[정제한 전체 증거 ZIP](baseline-evidence-20261020.zip)에 환경·실행별 stdout/stderr·HTTP·SQL·도구 버전·최상위 명령 종료 코드·검증 기록을 묶었다. 환경 파일의 비밀값은 포함하지 않았다.
로컬 원문도 유지한다. 일부 JSON 영수증은 `runs/evidence/`에서 바로 읽을 수 있다.
도구 Python 3개 구문 검사·JDI Java 컴파일·`git diff --check`·`script/docs-check.sh`·에러 코드 31개 검사 모두 통과했다. 변경 Markdown 13개 상대 링크 누락 0건을 확인했다.
