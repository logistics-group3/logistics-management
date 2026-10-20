# feat/delivery-T09

## 지금 하는 일
주문 확정 뒤 배정과 배정 결과 알림(I-19·I-20·I-21)을 delivery·order에 구현하고 커밋했다. 정본은 `delivery-service/docs/tasks/T09.md`, 근거는 결정 로그 29, 계층별 진행은 `docs/services/delivery/notes.md` "T09". 다음은 부하 측정 단계 4 → dev PR.

## 진행 상태
- [x] 브랜치: `origin/dev`(0b4df55, hub #28 포함) 위로 옮김(2026-10-20). hub가 ORDER-002를 먼저 써서 T09 리뷰 요청은 ORDER-003. T08은 #25로 dev에 merge. 임시 커밋은 풀어서 아래 다섯 커밋으로 나눴다(push 안 함). 옮기기 전 상태는 `backup/T09-before-rebase`
- [x] 흐름 결정(사용자): 주문 확정 → 배정 → 후보 0명이 확정 뒤 60초 넘으면 수동 지정 전환(자동 배정 중단) → MASTER 채널 Webhook → 배정 완료 I-20 → order DM. Slack은 order만. 기본값(60초, 채널 Webhook, 업체명 재조회) 승인
- [x] delivery 구현·테스트 115개 통과, order 구현·테스트 471개 통과, `./gradlew build` 통과, `script/docs-check.sh` OK, `script/error-codes-check.sh` 통과(2026-10-20)
- [x] 문서: 결정 로그 29(구현하며 정한 것 포함), T09.md, delivery·order README 계약, ORDER-003(OPEN, Junhyun 리뷰 요청), ADR 0016 갱신, `delivery-api.md`·`delivery.md`, Slack DM 가이드, `load-test.md`, 구현 노트
- [x] 커밋 나누기(2026-10-20): ① docs(delivery) 설계 ② feat(delivery) 확정 뒤 배정·수동 지정·알림·안전망(시드·k6 확정 호출 포함) ③ feat(order) I-19 호출·I-20·I-21·DM 시점 ④ docs 계약·ADR·가이드·progress ⑤ test(load-test) 주문 → DM 끝단 측정 도구. ②까지만 있는 상태도 컴파일된다(order와 모듈이 나뉨)
- [ ] 프런트 검증: 프런트 코드·외부 API 응답을 바꾸지 않아 미실행. 필요하면 실행
- [ ] 실제 기동 확인: 주문 → DM, 후보 0명 → MASTER 알림 → D-04 → DM, `script/delivery-e2e-check.sh`(미실행)
- [ ] 측정 단계 4(확정 비용, T08 조건)와 주문 경로(O-01, `order-creation` 4개 포화)
- [x] 주문 → DM 끝단 처리량 측정(2026-10-20, T09.md 작업 기록, 방법은 `load-test.md` 8절): 10·20·40 통과, 80 실패. 병목은 order 알림 실행기(2스레드·대기열 100, Webhook과 공유)로 DM 약 60% 누락, O-01은 주문 생성 실행기 포화로 4~5% 503. 측정 도구(⑤ 커밋): `load-test/slack-stub/`, `docker-compose.order-dm.yml`, `load-test/k6/order-dm.js`, `load-test/order-dm.sh`, `load-test/order-dm-stats.sh`, `load-test/order-dm-seed.sql`. 스택은 덧붙임 4개로 실행 중
- [ ] 끝단 실패 기준 확정(임시: O-01 실패·DM 누락 1%, 끝단 p95 3초)과 개선 여부(DM 실행기 분리 등) — 사용자 판단
- [ ] 후속: 취소 DM, MASTER 화면의 수동 지정 목록(프런트 협업 요청)

## 메모
- 버린 안(결정 로그 29): 두 신호 + 주문 행 조건부 UPDATE, 배정 완료 신호 + "아직이면 재시도", 메시지 큐
- 내부 API 번호: dev #27이 I-18(`DELETE .../creation`)을 먼저 써서 T09는 I-19 확정, I-20 배정 결과 알림, I-21 안전망으로 바꿨다(결정 로그 29). 로컬 DB에는 옛 V5(주석만 다름)가 적용돼 있어 다시 띄우면 Flyway 체크섬 오류 → `script/run.sh --reset`
- dev 문서 충돌(T09와 별개, 사용자 판단 대기): #27 머지가 `delivery-service/docs/tasks/T08.md`(성능 고도화 정본)를 "T08 주문 생성 복구" 문서로 덮어썼다(원본은 a3788df). 태스크 README에 T08 행 3개, 결정 로그에 25번이 두 개
- Docker 앱 스택 두 개(`logistics-management`, `-sync-lock`)는 사용자 요청으로 stop했다(컨테이너·볼륨 유지, 재시작 정책으로 Docker 재시작 시 다시 뜰 수 있음)
- 사용자 규칙: 질문에는 답만, 변경은 무엇을 바꿀지 말하고 "진행할까요?"로 확인. 커밋은 `git -c user.name=jihaneol -c user.email=6531z@naver.com commit`, AI 공동 작성자 줄 금지. 답 끝에 "기록: 경로"
