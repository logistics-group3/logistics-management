# 스텝 34. 전수검사와 병합 전 수정

- 날짜: 2026-10-15
- 근거: 사용자 요청 "잘 구현됐는지 전수검사", 이어서 "병합 전 7건만 고치고 PR까지"
- 결과: 검사 결과와 후속 작업 목록은 [전수검사 기록](../../../reviews/base-setting-integration-audit.md)에 있다. 병합 전 7건을 커밋 6개로 고쳤다.

## 커밋
| 커밋 | 내용 |
|---|---|
| `fix(agents)` setup-agents가 직접 만든 항목만 지우도록 수정 | 링크는 대상 경로로, 복사본은 목록 파일로 알아본다. 이름이 겹치면 건너뛴다. 폴더가 링크면 중단 |
| `fix(config)` DB 포트 자리표시자 추가와 설정 우선순위 설명 정정 | `${DB_PORT:5432}`, 환경 변수 > Config Server > 모듈 application.yml |
| `docs(agents)` Feign 실패 종류별 예외와 보상 처리 정정 | 4xx·5xx는 `BusinessException`, 응답 없음은 `FeignException` |
| `docs(decisions)` 통합 ADR에 팀 검토 후속 결정 반영 | 0008 "후속 결정" 절, 선별 검토 문서 "→ 이후 변경" |
| `docs(agents)` 하루 정리는 제안 후 기록으로 통일 | `AGENTS.md`, `learning-note` |
| `fix(script)` DB 비밀번호 실제 인증 검사와 에러 코드 검사 대상 보정 | 컨테이너 IP 접속, 들여쓰기·대상 파일 보정, `${변수}` 표기 |

## 리뷰 포인트
1. **우선순위를 실행으로 확인했다**: 처음 저장소에는 "Config Server 값이 환경 변수보다 우선한다"는 주석이 있었고 나도 가이드에 그대로 옮겼다. Spring Boot 소스(`ConfigDataEnvironment`가 설정 데이터를 `addLast`)를 보고, Gateway를 `SERVER_PORT=18080`으로 띄워 확인했다. 설정 서버가 8080을 줘도 18080으로 떴다. 옛 bootstrap 방식(`spring-cloud-starter-bootstrap`)에서는 원격 값이 우선이라 그 설명이 섞인 것으로 보인다(미확인).
2. **postgres 이미지의 trust 설정**: `initdb` 기본 pg_hba는 소켓과 127.0.0.1을 trust로 두고, 이미지가 마지막 줄에 `host all all all scram-sha-256`을 붙인다. 그래서 같은 컨테이너 안에서도 IP로 접속해야 비밀번호를 검사한다.
3. **bash 3.2와 한글**: `"$dir가"`처럼 변수 바로 뒤에 한글이 오면 bash 3.2가 한글 바이트를 변수 이름으로 읽어 `unbound variable`로 멈춘다. `${dir}`로 감싼다. 이번 테스트 중 발견했다.
4. **나머지는 후속 작업**: 리뷰 범위를 키우지 않으려고 병합 전 7건만 고쳤다. 같은 파일을 고치는 김에 한 작은 수정 3개는 기록 문서 1장 아래에 적었다.

## 검증
| 검사 | 결과 |
|---|---|
| `setup-agents.sh` 8가지 경우(`/bin/bash` 3.2, 사본) | 개인 항목 보존, 이름 겹침 건너뜀, 원본 삭제 시 정리, 복사→링크 전환, 폴더 링크 중단, 다른 폴더에서 실행 |
| Gateway `SERVER_PORT=18080` + Config Server `server.port: 8080` | 18080으로 기동(환경 변수 우선) |
| user-service `DB_PORT=1`(비밀번호도 일부러 틀리게) | `Connection to localhost:1 refused`(자리표시자 적용) |
| `script/run.sh --reset --check` (별도 Compose 프로젝트) | E2E PASS 15 / FAIL 0 |
| `script/db-check.sh` 정상 / `USER_DB_PASSWORD` 틀리게 | PASS 5개 / `password authentication failed`, exit 2. 예전 방식(127.0.0.1)은 같은 틀린 비밀번호로 접속됐다 |
| `script/error-codes-check.sh` | 저장소 PASS 15개. 사본에서 탭 들여쓰기 중복·문서 누락은 FAIL, `.claude/worktrees` 사본은 무시 |
| `./gradlew clean build` | 성공, 테스트 98개 실패 0 |
| `script/docs-check.sh origin/main` | OK |

검증용 프로세스와 Compose 프로젝트는 모두 내렸다. 기존 `logistics-management` 컨테이너 9개는 건드리지 않았다.
