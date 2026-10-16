# feat/infra-api-test-tracing

## 지금 하는 일
Micrometer Tracing + Zipkin 분산 추적 도입과 Swagger 테스트 도구 정리 커밋 완료, 푸시·PR 전

## 진행 상태
- [x] dev `ceb2a4e`에서 브랜치 생성(원격 dev 추적 해제). 사용자 요청: 로그·테스트·분산 추적 관련 변경을 별도 브랜치로 옮긴다
- [x] `b2317c0` 이전 세션 질문 노트 3개와 feat-order-create progress 2줄(사용자 결정으로 함께 커밋)
- [x] `cf595a8` Swagger 테스트 보조 도구(`script/api-test.sh`, `script/api-test/`) 추가. 처음에는 OpenTelemetry 에이전트 + Jaeger로 추적
- [x] `16084db` 가이드·ADR·학습 노트·스텝 47 문서
- [x] 사용자 요청 "원래 도입하기로 한 도구를 도입": SA 3.3대로 Micrometer Tracing + Zipkin 도입. 루트 `build.gradle`(6개 서비스 brave·zipkin reporter, 5개 서비스 feign-micrometer), `config-repo/application.yml`(샘플링·Zipkin 주소), `config-repo/gateway-service.yml`(reactor context-propagation), `docker-compose.yml`(zipkin 3.6.1, ZIPKIN_ENDPOINT·TRACING_SAMPLING_PROBABILITY), `.env.example`, `script/run.sh`, `script/ui-test.sh`(19411)
- [x] 테스트 도구에서 에이전트·Jaeger를 빼고 Zipkin 링크로 바꿈. 로컬의 옛 Jaeger 컨테이너 삭제
- [x] 검증: `./gradlew build` 성공(테스트 729개, 실패·오류·skip 0). 이미지 재빌드 후 실제 실행에서 WebClient·Feign 전파 확인(업체 조회, 없는 허브 지정 404, 없는 상품 주문 404). Zipkin 구간 나무, 로그 `[traceId-spanId]`, 샘플링 0에서 Zipkin 미전송·traceparent 플래그 00 확인 후 1.0 복구
- [x] 문서: ADR 0017을 Micrometer + Zipkin 결정으로 다시 씀(파일명 `0017-distributed-tracing-micrometer-zipkin.md`), 매핑 문서(local-run, config-security, frontend README, 루트 README, gateway 창구), 가이드·학습 노트, 스텝 48
- [ ] 푸시·PR(사용자 요청 시). 공통 영역 변경이라 팀원 전체 리뷰 요청
- 면접 포인트: trace·span·traceparent와 sampled 플래그, Feign 계측(feign-micrometer), WebFlux MDC와 context-propagation, 샘플링 비율과 운영 비용, 에이전트 방식과 Micrometer 방식 차이

## 다음 단계
1. 사용자가 원하면 `git push -u origin feat/infra-api-test-tracing` 후 dev 대상 PR. 제목 후보 `feat(infra): Micrometer Tracing과 Zipkin 분산 추적 도입`, STAR 본문, AI 표기 없음. 리뷰어는 팀원 전체(git-workflow 1장 공통 영역)
2. PR 설명에 UI 테스트 스택은 다음 `script/ui-test.sh up`부터 Zipkin 19411이 함께 뜬다는 점을 적는다(이번에 실제로 띄우지 않음)
3. 정상 주문(O-01) trace로 order → company → delivery → user 전체 흐름을 확인해 가이드 예시를 보강한다

## 직접 해보기
1. `script/run.sh`로 이미지를 다시 빌드해 띄운다. 출력의 `Zipkin http://localhost:9411`을 연다.
2. `script/api-test.sh up` 후 다른 터미널에서 `script/api-test.sh flow`를 켠다.
3. Swagger에서 RCV_USER로 없는 상품 주문을 보내고(404), 로그 줄 `[traceId-spanId]`의 앞 32자로 `script/api-test.sh trace <traceId>`를 실행한다.
4. 출력된 Zipkin 링크에서 Gateway → user → order → company 구간 나무를 본다.
5. 끝나면 `script/api-test.sh restore`.

## 메모
- 로컬 기본 스택은 지금 테스트 설정(`API_TEST_SQL=DEBUG`, Slack 꺼짐)이다. 테스트 후 `restore`한다.
- 실행 상태는 `.local/api-test/`(Git 제외). `otel/` 폴더의 에이전트 jar는 더 쓰지 않으므로 지워도 된다.
- 범위 밖: SQL 구간(datasource 계측), `@Async` Slack 스레드 전달, 운영용 Zipkin 저장소(ADR 0017 결과).
- 테스트 시나리오·입력값: Notion 「기능 테스트」, 「junhyun 테스트 일지」. ADR: `docs/decisions/0017-distributed-tracing-micrometer-zipkin.md`.
