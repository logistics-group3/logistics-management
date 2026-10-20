# 주문 생성 장애 재현 도구

현재 서비스 코드에 변경 없이 JDI 중단점으로 처리 스레드를 멈춘 뒤 전용 order JVM을 종료한다.
`--recover`는 실제 운영자 복구 API를 검증한다. 자동 재처리는 구현하지 않는다. [설계](../../docs/reviews/2026-10-20-order-creation-recovery-plan.md), [실험 기록](../../docs/reviews/order-creation-recovery/README.md)을 함께 본다.

## 준비와 실행

저장소 루트에서 실행한다. Docker, Python 3.9 이상, `jdk.jdi` 모듈이 있는 JDK·javac가 필요하다.
셸의 JAVA_HOME은 바꾸지 않는다. 서비스는 Docker 이미지의 Java 17로 실행한다.

```bash
mkdir -p .local/recovery-experiments/jdi
javac --add-modules jdk.jdi -d .local/recovery-experiments/jdi \
  script/recovery-experiment/JdiGate.java
python3 script/recovery-experiment/experiment.py setup
python3 script/recovery-experiment/experiment.py seed

# run_id는 매번 새 값으로 지정한다. 기존 기록 폴더가 있으면 덮어쓰지 않는다.
python3 script/recovery-experiment/scenario.py C0-F1 20261020-C0-F1-02
python3 script/recovery-experiment/scenario.py C0-F2 20261020-C0-F2-01
python3 script/recovery-experiment/scenario.py F1 20261020-F1-01
python3 script/recovery-experiment/scenario.py F2 20261020-F2-01
```

`setup`은 `.env.recovery-test`를 권한 0600으로 생성하고 별도 Compose 프로젝트 `logistics-recovery-test`를 만든다.
환경 파일이나 override가 이미 있으면 덮어쓰지 않고 중단한다. DB·Eureka·Config·Gateway와 서비스 5개·Zipkin을 기동하며 Prometheus·Grafana는 기동하지 않는다.
실제 컨테이너의 Slack 비활성·restart=no·프로젝트 label을 검증한다. 각 명령과 정제한 출력은 `.local/recovery-experiments/<runId>/`에 남는다.

## 대기 지점과 제어

- F1: `OrderService.run`에서 `process(creation)` 호출 전.
- F2: `OrderService.process`에서 `createDelivery` 반환 후, 명령·주문 객체를 만들기 전.
- 소스 문장으로 줄 번호를 찾고 JVM의 디버그 줄 정보와 대조한다. 애매하거나 클래스가 없으면 실행을 중단한다.
- 중단점은 `SUSPEND_EVENT_THREAD`로 생성 처리 스레드만 멈춘다. 202 응답과 조회 요청은 계속 처리할 수 있다.
- `DeliveryClientAdapter.create`·`OrderWriter.save` 입구의 짧은 중단점으로 호출 수를 센다. 전역 MethodEntryRequest는 사용하지 않는다.
- 대조군 C0는 stdin으로 resume을 보내 실제 주문·배송 1건과 동일 키 201 재사용을 확인한다.
- F1·F2는 도달 신호와 DB·I-17 결과를 확인한 뒤 검증된 전용 order 컨테이너만 SIGKILL한다. exit 137·자동 재시작 없음 확인 후 명시적으로 재기동한다.
- 재기동 뒤 동일 키 202와 DB 잔류 상태를 확인하고 10초 추가 관측한다. 수십 초·수분 후의 자동 복구를 검증하는 시험은 아니다.

JDI는 호스트 loopback 25005에서만 연결한다. 실제 서비스 소스·DB 상태를 디버거로 수정하거나 원격 메서드를 호출하지 않는다.
JDI가 연결된 환경은 성능 측정 환경이 아니다. 이 실험의 결과로 일반 처리 지연이나 처리량을 주장하지 않는다.

## 증거와 실패 처리

`commands.jsonl`에는 정확한 argv·작업 디렉터리·시각·종료 코드·stdout/stderr 경로를 기록한다.
HTTP는 Python urllib를 사용하고 요청·정제된 응답·상태·시각을 JSON으로 남긴다. 로그인 비밀번호·토큰·인증 헤더는 기록에서 제거한다.
DB 집계는 실험 도구가 postgres로 두 스키마를 읽어 확인하는 진단이다. 서비스가 다른 서비스 DB를 직접 읽는 구현이 아니다.

중단점·기대 상태·응답 검증이 하나라도 실패하면 `FAILED_OR_INCONCLUSIVE`로 남긴다.
디버거는 종료하지만 실험 데이터는 지우지 않는다. 실패 뒤 원래 작업이 완료될 수 있으므로 같은 run_id를 재사용하지 않는다.
각 시나리오는 새 프로세스로 도구를 실행한다. 기존 debugger를 분리했는지 확인하고 한 번에 한 시나리오만 실행한다.

## 환경 정리

마지막 증거 수집과 프로젝트 label 확인을 마친 뒤 전용 프로젝트만 내린다. 볼륨은 보존한다.
이번 고정 runId 네 건의 최종 검증·정리는 `python3 script/recovery-experiment/finalize.py --stop-dedicated-stack`으로 실행했다.

```bash
docker compose -p logistics-recovery-test --env-file .env.recovery-test \
  -f docker-compose.yml -f .local/recovery-experiments/compose.override.yml down
```

`.env.recovery-test`·원문은 로컬에 보존하고 커밋하지 않는다. 재개할 때는 같은 파일과 override로 `up -d`한 뒤 프로젝트·시드·Slack 비활성을 다시 확인한다.
원문에는 테스트 주문·계정 정보가 있으므로 공유할 자료는 검토 후 정제한다.

## 구현 후 동일 장애·복구 검증

기존 전용 환경 파일·볼륨을 보존한 상태에서 `python3 script/recovery-experiment/experiment.py upgrade`로 새 이미지를 반영한다.
`scenario.py F1 <새-run-id> --recover`, `scenario.py F2 <새-run-id> --recover`는 중단·재기동·잔류 상태 확인 뒤 복구 API를 호출한다.
ABORTED·CLOSED·활성 주문/배송/구간 0, 동일 주문 키 409, 동일 복구 요청의 결과 재사용, 뒤늦은 배송 생성 거절을 함께 검사한다.
새 setup은 일반 JVM heap 128MiB/컨테이너 448MiB, order heap 192MiB/컨테이너 512MiB와 메타스페이스·코드 캐시·CPU 제한을 기본으로 둔다. 공유 VM의 다른 작업 메모리도 고려해야 한다.
`finalize_after.py --stop-dedicated-stack`은 이번 AFTER-*-02 네 건의 최종 상태·Gateway 인가·이력·V7을 재확인하고 전용 스택만 내린다.
