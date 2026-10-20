# 주문 장애 복구 실험 기록

## 목적과 현재 단계

2026-10-20 사용자 요청: 검증할 내용, 환경 설정, 실제 실행 명령과 결과를 체계적으로 기록하며 진행한다.
현재는 [복구 구현과 F1·F2 전후 검증](2026-10-20-implementation-results.md)을 마쳤다. 아래 환경 계획·초기 조사·개선 전 재현은 과정 이력으로 보존한다.

## 기록 규칙

1. 실행 전에 가설·시나리오 ID·기대 결과·실패 판정·대조군을 적는다.
2. 실행마다 KST 시작 시각, runId, Git SHA와 미커밋 코드 diff, 도구 버전, 이미지 ID, 설정·시드 버전을 남긴다. 비밀값은 제외한다.
3. 실제 명령, 작업 디렉터리, 시작·종료 시각, 종료 코드, 출력 파일 경로를 적는다. 파이프를 쓰면 원래 명령의 종료 코드를 보존한다.
4. 기대와 실제를 분리한다. 실패·재현 실패·수집 실패도 기록하고, 조건을 바꿔 재실행하면 새 runId를 쓴다.
5. HTTP 상태·익명화된 본문, DB 검증, 서비스 로그, JUnit XML·화면 등 근거를 연결한다. 캡처 한 장으로 전체 흐름을 통과 처리하지 않는다.
6. 결과를 관찰 사실 → 해석 → 남은 불확실성 → 다음 행동 순서로 정리한다.
7. 원문은 `.local/recovery-experiments/<runId>/`에 두고, 검토한 요약·필요한 정제 자료만 이 폴더에 추가한다. `.env.recovery-test`와 원문 경로는 현재 gitignore 적용을 확인했다.
8. 비밀번호·JWT·Authorization·쿠키·Slack 토큰은 콘솔 로그와 공유 문서에서 제외한다. 비밀값이 들어간 `docker compose config` 전체 출력은 보관하지 않는다.

## 기록 위치

| 자료 | 위치 |
|---|---|
| 무엇을 검증할지·상태 판단 | [설계 정본](../2026-10-20-order-creation-recovery-plan.md) |
| 실행별 요약 | `runs/<KST시각>-<시나리오>-<순번>.md` |
| 실행 양식 | [runs/TEMPLATE.md](runs/TEMPLATE.md) |
| 명령·로그·응답·DB 집계 원문 | `.local/recovery-experiments/<runId>/` |
| 다음 에이전트 인계 | 현재 브랜치의 `docs/progress/<branch>.md` |

## 환경 구성의 초기 계획 — 실제 구성은 결과 보고서 참조

- Compose 프로젝트는 `logistics-recovery-test`로 분리한다. 개발·기존 UI 테스트 프로젝트와 DB 볼륨을 공유하지 않는다.
- 기존 `docker-compose.yml`을 기반으로 전용 override를 준비한다. order의 `restart: on-failure`는 실험에서 `restart: "no"`로 바꿔 중단 직후 상태를 관찰한다.
- 전용 `.env.recovery-test`는 권한 0600, 임의 생성한 비밀값을 사용한다. 기존 `.env`·`.env.ui-test`를 수정하거나 값 전체를 출력하지 않는다.
- Slack Webhook·Bot 토큰은 비우고 DM은 false로 강제한다. 파일뿐 아니라 최종 컨테이너 설정의 해당 항목이 비활성인지 값 노출 없이 확인한다.
- 후보 호스트 포트는 DB 25432, Config 28888, Eureka 28761, Gateway 28080, Zipkin 29411, Prometheus 39090, Grafana 33000이다. 사용 가능 여부는 아직 확인하지 않았다. UI가 필요하면 25174를 후보로 쓴다.
- 모든 호스트 공개 포트는 loopback에 바인딩한다. 기존 Compose에서 Gateway 포트 변수는 주소를 포함할 수 있고 다른 관리 포트는 기본 파일이 loopback을 붙인다.
- 테스트 시드는 정상 주문 가능한 업체 2개·상품 1개·담당자가 있는 허브와 MASTER 계정으로 고정한다. seed 도구가 프로젝트·Gateway 주소를 명시적으로 받도록 준비한다.
- `script/ui-test.sh`는 프로젝트와 `.env.ui-test`를 고정하고, seed도 기존 환경을 사용하므로 이 실험에서 그대로 호출하지 않는다.
- `docker-compose.fault.yml`은 기존 지연·부하 시험용이며 요청별 중단 지점 F1/F2를 제공하지 않는다. 그대로 사용했다고 기록하지 않는다.
- F1/F2 대기 지점은 테스트 전용 설정·내부 제어로만 활성화한다. 요청 식별자 하나에 한정하고 재기동 시 해제한다. 도달 신호 수집에 실패하면 종료 실험을 진행하지 않는다.
- 원문 수집 후 전용 프로젝트만 정상 종료하고 볼륨은 보존한다. `down -v`, `prune`, 기존 스택 재시작은 실험 절차에 포함하지 않는다.

## 초기 예정 명령 — 현재 실제 명령은 결과 보고서 참조

전용 환경 파일·override·시드·장애 제어 도구는 아직 없다. 아래는 실행 순서의 초안이며 지금 실행 가능한 완성된 런북이 아니다.
환경 준비 단계에서 실제 파일과 변수 우선순위를 확정하고, 셸에 남은 개발 환경 변수가 전용 값을 덮지 않도록 실행기를 만든다.

```bash
# 시작 상태와 도구 버전
git rev-parse HEAD
git status --short
docker version
docker compose version
./gradlew --version

# 전용 파일 생성·설정 검토 후 사용할 Compose 인자 예시
docker compose -p logistics-recovery-test --env-file .env.recovery-test \
  -f docker-compose.yml -f .local/recovery-experiments/compose.override.yml config --quiet
docker compose -p logistics-recovery-test --env-file .env.recovery-test \
  -f docker-compose.yml -f .local/recovery-experiments/compose.override.yml up -d --build
```

이후의 요청·도달 대기·스냅샷·종료·재기동 명령은 장애 제어 도구가 생길 때 정확한 명령으로 채운다. 존재하지 않는 API나 스크립트를 실행했다고 쓰지 않는다.
종료 대상은 Compose label과 프로젝트·service가 일치하는 컨테이너 ID로 검증한다. F1/F2에서 order 한 개만 종료하고 delivery·DB를 유지한다.

```bash
# 구현 후 회귀 검증 후보. 아직 이번 작업에서는 실행하지 않았다.
./gradlew :order-service:test
./gradlew :delivery-service:test
./gradlew build
script/docs-check.sh
script/error-codes-check.sh
git diff --check
```

프런트·소비 API 계약 변경이 생기면 프로젝트 full-build 규칙의 프런트 검사와 전용 E2E를 추가한다.
전체 build 성공·Mock 기반 테스트·실제 프로세스 재기동 검증을 각각 보고한다.

## 2026-10-20 실제 수행 기록: 설계 조사

작업 디렉터리: 저장소 루트. 실행 목적: 장애 지점과 현재 계약 확인. 서비스 실행·중단 명령은 없음.
아래는 실제 사용한 핵심 명령이며, 성공은 파일 읽기·정적 검사의 성공을 뜻한다.

| 실제 명령 | 관찰 결과 |
|---|---|
| `git branch --show-current` / `git rev-parse --short HEAD` | feat/docs-learning-evidence / e1ac9cc |
| `git status --short --untracked-files=no` | 앞선 상담 문서 3개 미커밋. 해당 변경 보존 |
| `sed -n '1,200p' order-service/src/main/java/com/sparta/logistics/order/application/OrderService.java` | 진행 기록 → 작업 제출 → I-09 → 주문 저장 순서 확인 |
| `cat order-service/src/main/java/com/sparta/logistics/order/application/OrderWriter.java` | 생성 기록 잠금·주문/COMPLETED 동일 트랜잭션 확인 |
| `cat order-service/src/main/java/com/sparta/logistics/order/domain/OrderCreation.java` | 요청 전체 미보존, PROCESSING에서만 일반 상태 전이 가능 |
| `sed -n '1,105p' delivery-service/src/main/java/com/sparta/logistics/delivery/application/DeliveryService.java` | 현재 I-13의 무조건 논리 삭제 확인 |
| `sed -n '55,105p' delivery-service/src/main/java/com/sparta/logistics/delivery/application/DeliveryCreationService.java` | I-09 저장 시 orderId 잠금·종료 표시 재확인 |
| `sed -n '1,200p' script/ui-test.sh` | 기존 프로젝트·env 파일 고정, 재사용 전 수정 필요 |
| `cat docker-compose.fault.yml` / `cat docker-compose.loadtest.yml` | 기존 설정에 F1/F2 중단 제어 없음 |
| `git check-ignore .local/recovery-experiments/probe .env.recovery-test` | 두 경로 ignore 적용 |

경로 탐색 실패도 남긴다. `.../domain/model/OrderCreation.java`는 없었고 실제 파일은 `.../domain/OrderCreation.java`다.
`docker-compose.ui-test.yml`, `.env.ui-test.example`도 없었다. 실제 UI 테스트는 기본 Compose와 `script/ui-test.sh`가 생성하는 환경 파일을 사용한다. 이 조회 오류 때문에 환경을 생성하거나 기존 설정을 바꾸지 않았다.

설계 조사 당시에는 F1/F2와 R1~R8 모두 계획 상태였다. 이후 F1/F2를 실제 실행한 결과는 아래 완료 기록과 결과 보고서를 따른다. R1~R8은 아직 실행하지 않았다.

## 설계 문서 검증 결과

2026-10-20 실제 실행: `git diff --check`와 `script/docs-check.sh` 모두 종료 코드 0이다. docs-check 출력은 `OK (매핑된 코드 변경 없음)`이다.
Python으로 변경 Markdown 6개의 상대 링크 경로를 검사했고 누락 0건이었다. 설계 파일 202줄·기록 안내 100줄·양식 55줄을 확인했다(이 검증 결과 단락 추가 전).
이는 문서 검사 결과다. 백엔드 빌드·테스트·Docker 기동·장애 주입·복구 기능 검증은 이번 설계 작업에서 실행하지 않았다.

## 2026-10-20 실제 장애 재현 완료

[개선 전 결과](2026-10-20-baseline-results.md)에 C0 두 건 정상 완료, F1·F2 실제 종료·재기동 재현과 첫 대조군 503 판정 제외를 기록했다.
서비스 코드 변경 없이 JDI EVENT_THREAD 중단점을 사용했다. 도구는 [실행 안내](../../../script/recovery-experiment/README.md)에 있다.
정확한 실행 명령·종료 코드·HTTP·DB 집계는 `runs/`의 개별 기록과 evidence를 따른다. 전체 원문은 `.local/recovery-experiments/`에 보존했다.
전용 프로젝트는 종료했고 전용 DB 볼륨과 시작 전 실행 컨테이너 12개 ID는 보존했다. 복구 기능은 구현하지 않았으며 사용자 요청의 보고 지점에서 멈춘다.

## 2026-10-20 구현 후 검증

O-08~O-10·I-18·실행권·이력을 구현하고 같은 F1/F2에서 ABORTED·CLOSED·활성 배송 0을 확인했다.
실행별 기록은 AFTER-* 폴더, 코드·DB·실제 API의 구분과 제외한 OOM 실행은 [구현 결과](2026-10-20-implementation-results.md)를 따른다.
JVM·컨테이너 자원 제한은 신규 setup의 기본 override에도 반영했다. 업무 기본 설정은 바꾸지 않는다.
