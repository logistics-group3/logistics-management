# 47. Swagger 테스트 보조 도구와 로컬 분산 추적

- 날짜: 2026-10-16
- 브랜치: `feat/infra-api-test-tracing`
- 이전 작업 커밋: `b2317c0 docs(docs): 주문 작업 중 질문 노트와 인계 기록 추가`
- 이번 커밋 제목: `chore(infra): Swagger 테스트 보조 도구와 로컬 분산 추적 추가`, `docs(docs): Swagger 테스트·로컬 분산 추적 가이드와 결정 기록`
- 범위: 로컬 테스트 도구(`script/api-test.sh`, `script/api-test/`)와 문서. 서비스 코드·공통 설정은 바꾸지 않았다.

## 바뀐 동작

`script/api-test.sh up`을 하면 기본 개발 스택이 테스트 모드로 바뀐다.
- 요청마다 Gateway와 각 서비스에 접근 로그가 한 줄씩 남는다.
- 모든 로그 줄에 trace ID가 붙고, 같은 요청이 거친 서비스는 같은 ID를 가진다.
- Jaeger(`http://localhost:16686`)에서 요청 하나의 서비스별 구간과 시간을 본다.
- Slack Webhook·DM이 꺼진다.

`restore`로 원래 설정에 돌아간다. 시드·토큰·로그·trace 조회 명령은 [가이드](../../../guides/api-test.md)에 있다.

## 순서와 구성

| 파일 | 역할 |
|---|---|
| `script/api-test.sh` | 진입점. 오버라이드를 `-f`로 덧씌워 up·restore, 에이전트 내려받기와 체크섬, 준비 확인, logs·flow·trace |
| `script/api-test/compose.override.yml` | 접근 로그·OTel 에이전트·로그 형식·Slack 끔 환경 변수, Jaeger 서비스 |
| `script/api-test/seed.py` | 공개 API로 테스트 데이터 준비, 토큰 재발급, Gateway 경유 준비 확인(`--probe`) |
| `.local/api-test/` (Git 제외) | data.json, tokens.txt, logs/, otel/ 에이전트 jar |

## 왜 이렇게 만들었는가

- 공통 예외 처리기가 4xx를 남기지 않아 접근 로그가 필요했다. 웹 서버 설정만으로 켜서 코드를 바꾸지 않는다.
- 요청 흐름을 묶으려면 trace ID가 필요하다. SA 방식(Micrometer Tracing + Zipkin)은 모든 서비스의 의존성·공통 설정 변경이라, 사용자 결정에 따라 테스트 도구는 에이전트 방식으로 만들었다([ADR 0017](../../../decisions/0017-local-api-test-tracing.md)).
- 오버라이드 파일을 `docker-compose.override.yml`로 두지 않았다. 이 이름은 모든 compose 명령에 자동 적용돼 `script/run.sh`에도 끼어든다.
- 준비 확인을 Eureka UP 개수에서 Gateway 경유 API 호출로 바꿨다. Eureka 응답 캐시(최대 30초) 때문에 재생성 직후 옛 인스턴스를 세어 2.5초 만에 "준비"로 판단한 일이 있었다.
- 로그 레벨을 `LOGGING_LEVEL_*`로 넣으면 로거 이름이 소문자가 돼 `org.hibernate.SQL`에 걸리지 않았다. `SPRING_APPLICATION_JSON`으로 바꿨다.

## 검증 결과

- `up`: 컨테이너 재생성 후 Gateway 경유 users·hubs·companies·orders·deliveries 모두 200. 오버라이드를 `script/`로 옮긴 뒤 같은 설정이라 재생성 없이 통과
- `status`: Slack Webhook 꺼짐, 접근 로그·분산 추적·SQL 로그 켜짐
- `seed`: 처음 실행 시 가입 7건 201, 배송담당자 지정 3건 200, 업체 2건·상품 1건 201. 다시 실행 시 모두 재사용
- 에이전트: 임시 폴더에서 내려받기·SHA-256 일치, 두 번째 실행 건너뜀, 파일 변조 시 다시 받기 확인
- 추적: SUP_USER `GET /api/v1/companies/{id}`가 Gateway → user-service `/internal/users/{id}`(권한 재확인)·SQL → company-service·SQL로 한 trace ID에 묶여 로그와 Jaeger 양쪽에서 보임
- 미확인: Linux·Windows(WSL)에서 실행, 주문 생성(O-01)처럼 여러 서비스를 거치는 쓰기 요청의 trace. 서비스 단위 테스트는 코드 변경이 없어 다시 돌리지 않았다

## 직접 해보기

1. `script/run.sh`로 기본 스택을 띄운 뒤 `script/api-test.sh up`을 실행한다.
2. `script/api-test.sh seed`, `script/api-test.sh tokens`로 데이터와 토큰을 준비한다.
3. 다른 터미널에서 `script/api-test.sh flow`를 켜고 Swagger에서 요청을 보낸다.
4. 로그 줄의 `[trace ID]`로 `script/api-test.sh trace <ID>`를 실행하고, 출력된 Jaeger 링크를 연다.
5. 끝나면 `script/api-test.sh restore`로 되돌리고 `status`를 확인한다.

## 확인 질문

- trace ID와 span ID는 어떻게 다르고, 서비스 사이에서는 무엇으로 전달되나
- 로그 줄에 trace ID가 찍히려면 무엇이 필요한가(MDC)
- 에이전트 방식과 Micrometer Tracing 방식은 무엇이 다르고, 운영에는 어느 쪽이 맞나
- Eureka에 UP으로 보이는데 Gateway가 503을 줄 수 있는 이유는 무엇인가
