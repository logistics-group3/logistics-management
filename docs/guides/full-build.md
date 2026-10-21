# 백엔드와 프런트 전체 빌드·검증

`./gradlew build`는 Java 백엔드만 검증한다. `frontend/`는 별도 npm 프로젝트이므로 전체 빌드 완료에는 프런트 빌드·검증 결과도 필요하다.
Codex·Claude Code 등 각 AI는 루트 AGENTS.md를 통해 이 문서를 읽는다. Claude Code의 CLAUDE.md도 같은 기준을 참조한다.

## 준비

- 저장소 루트에서 실행한다. Docker Desktop이 켜져 있어야 PostgreSQL Testcontainers 통합 테스트가 실행된다.
- 백엔드는 저장소 Gradle Wrapper·Java 17 toolchain을 쓴다. 셸 JDK가 21이라는 이유로 JAVA_HOME을 바꾸지 않는다.
- 프런트는 Node.js 24·npm을 사용한다. 최초 설치 또는 package-lock.json 변경 뒤에는 `npm --prefix frontend ci`를 실행한다. 임의로 최신 버전을 설치하거나 lockfile을 다시 만들지 않는다.
- 이미 실행 중인 기본 개발 DB·서버를 초기화하지 않는다. `script/run.sh --reset`은 이 검증 절차에 포함하지 않는다.

## 전체 빌드 명령

```bash
./gradlew build
npm --prefix frontend ci
npm --prefix frontend run build
npm --prefix frontend run lint
npm --prefix frontend run format:check
npm --prefix frontend run test
script/docs-check.sh
script/error-codes-check.sh
```

| 명령 | 확인하는 것 |
|---|---|
| Gradle build | Java 컴파일·모듈 단위/DB/HTTP 통합 테스트·jar |
| npm build | TypeScript 타입 검사(`tsc -b`)와 Vite 배포 빌드(`frontend/dist`) |
| npm lint·format:check | 정적 검사·코드 포맷. 검사 과정에서 파일을 자동 수정하지 않음 |
| npm test | 프런트 계약·권한·멱등 요청·금액·폼 단위 테스트 |
| docs-check·error-codes-check | 코드와 문서 동기화·에러 코드 중복/누락 |

의존성이 현재 lockfile로 설치돼 있고 변경이 없다면 반복 검증에서 ci는 생략할 수 있다. 명령 실패를 숨기거나 테스트 실패 후 빌드만 성공했다고 전체 통과로 보고하지 않는다.
프런트 코드·패키지, 프런트가 쓰는 API 요청/응답·권한을 바꿀 때 위 프런트 검증을 적용한다. 백엔드 내부만 바꾼 경우에는 관련 백엔드 검증과 API 계약 영향 여부를 보고한다.

## 브라우저 E2E는 별도로 실행

실제 Gateway·DB로 쓰기 요청을 보내는 검증이다. 기존 개발 서버 대신 고정된 `logistics-ui-test` 프로젝트를 사용한다.

1. `.env.ui-test`에서 `SLACK_DM_ENABLED=false`를 확인한다. 토큰·Webhook URL·이메일 허용 목록의 실제 값을 출력하지 않는다.
2. `script/ui-test.sh up`으로 테스트 스택을 기동한다. Webhook은 스크립트가 항상 비운다. 환경 파일만 수정하고 컨테이너를 재생성하지 않으면 실행 설정은 바뀌지 않는다.
3. `script/ui-test.sh seed`로 전용 계정·참조 데이터를 준비한다. 기존 시드 주문 상태는 유지한다.
4. 별도 터미널에서 `script/ui-test.sh frontend`를 실행한다. UI 5174·Gateway 18080이 이미 사용 중이면 프로젝트를 확인하고 기존 프로세스를 임의 종료하지 않는다.
5. `npm --prefix frontend run test:e2e`를 실행한다. `frontend/playwright-report/index.html`에 보고서가 저장된다.

E2E globalSetup은 실제 테스트 order 컨테이너의 DM/Webhook 설정을 검사한다. UI 프록시의 MASTER ID가 `.local/ui-test-data.json`의 전용 DB와 일치하는지도 검사한다. 이 보호 검사를 우회하지 않는다.
실제 Slack 발송은 자동 테스트에 포함하지 않는다. 수신자·횟수를 승인받은 별도 검증만 [Slack DM 안내](slack-dm.md)에 따라 수행한다. 사용자가 켜 둔 수동 테스트 설정을 잠시 껐다면 테스트가 끝난 뒤 원래 설정으로 복구한다.

## 결과 보고

- 백엔드 테스트 수·실패/오류/skip, 프런트 build·lint·format·단위 테스트, 실제 API E2E를 각각 적는다.
- Gradle이 UP-TO-DATE를 사용했다면 새 실행과 캐시 결과를 구분한다. 이전 E2E 결과를 새 API 변경의 검증으로 표시하지 않는다.
- 실행하지 못한 검증은 환경·이유를 적는다. 기존 GitHub `docs-guard`는 문서·에러 코드 검사이며 전체 백엔드/프런트 빌드를 대신하지 않는다.
- 실행법은 [frontend README](../../frontend/README.md), 현재 UI·DM 결과는 [검증 기록](../reviews/slack-dm.md)을 참고한다.
