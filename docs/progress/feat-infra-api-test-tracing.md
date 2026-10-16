# feat/infra-api-test-tracing

## 지금 하는 일
Swagger 테스트 보조 도구와 로컬 분산 추적 커밋 완료, 푸시·PR 전

## 진행 상태
- [x] dev `ceb2a4e`에서 브랜치 생성(원격 dev 추적 해제). 사용자 요청: 로그·테스트·분산 추적 관련 변경을 별도 브랜치로 옮긴다
- [x] 사용자 결정: 지금 도구를 테스트 보조 도구로 옮기고(SA Micrometer+Zipkin은 후속), 이전 세션 문서도 같이 커밋한다
- [x] `b2317c0` 이전 세션 질문 노트 3개와 feat-order-create progress 2줄 커밋. README에는 그 3줄만 담았다
- [x] `cf595a8` `script/api-test.sh`, `script/api-test/compose.override.yml`, `script/api-test/seed.py` 커밋. `.local/api-test/`의 옛 도구 파일은 지우고 실행 상태만 남겼다
- [x] 검증: 옮긴 위치에서 `up`(재생성 없음, Gateway 경유 5개 200), `status`, `tokens`, `--help`. 에이전트 내려받기·체크섬·변조 시 재다운로드를 임시 폴더에서 확인
- [x] 추적 확인: SUP_USER `GET /api/v1/companies/{id}`가 Gateway → user `/internal/users/{id}`·SQL → company·SQL로 한 trace ID에 묶여 로그와 Jaeger에서 보임
- [x] 문서: `docs/guides/api-test.md`, ADR 0017(제안), 문서 지도·로컬 실행 가이드 링크, 학습 노트 2개, 스텝 47
- [ ] 푸시·PR(사용자 요청 시). PR 본문에 SA 분산 추적과 다른 점(ADR 0017)을 적는다
- 면접 포인트: trace·span·traceparent 전파, MDC로 로그에 trace ID 넣기, Java 에이전트 계측과 Micrometer Tracing 차이, Eureka 응답 캐시와 준비 판단

## 다음 단계
1. 사용자가 원하면 `git push -u origin feat/infra-api-test-tracing` 후 dev 대상 PR을 만든다(제목 `chore(infra): Swagger 테스트 보조 도구와 로컬 분산 추적 추가`, STAR 본문, AI 표기 없음).
2. Linux·WSL 팀원의 실행 결과를 받으면 가이드 "준비"를 보완한다.
3. 주문 생성(O-01) trace로 Saga 흐름(order → company → delivery → user)을 확인해 가이드 예시를 늘린다.

## 직접 해보기
1. `script/run.sh`로 기본 스택을 띄운다.
2. `script/api-test.sh up` → `status`에서 Slack 꺼짐, 접근 로그·분산 추적 켜짐을 확인한다.
3. `script/api-test.sh seed`, `script/api-test.sh tokens`로 데이터와 토큰을 준비한다.
4. 다른 터미널에서 `script/api-test.sh flow`를 켜고 Swagger로 요청한 뒤, 로그 줄의 `[trace ID]`로 `script/api-test.sh trace <ID>`를 실행하고 Jaeger 링크를 연다.
5. 끝나면 `script/api-test.sh restore`.

## 메모
- 실행 상태는 `.local/api-test/`(Git 제외): data.json, tokens.txt, logs/, otel/opentelemetry-javaagent.jar(2.31.1, SHA-256 스크립트 고정).
- 이 세션 중 로컬 기본 스택은 테스트 설정(`API_TEST_SQL=DEBUG`)으로 떠 있다. Slack Webhook이 꺼져 있으니 테스트 후 `restore`한다.
- 앞서 안내한 `API_TEST_SQL=DEBUG`는 16시 전에는 동작하지 않았다(로거 이름 소문자 변환). `SPRING_APPLICATION_JSON`으로 고친 뒤 확인했다.
- 테스트 시나리오·입력값: Notion 「기능 테스트」, 「junhyun 테스트 일지」. ADR: `docs/decisions/0017-local-api-test-tracing.md`.
