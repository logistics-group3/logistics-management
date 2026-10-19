# feat/delivery-T08

## 지금 하는 일
목표(사용자): 생성 트래픽을 올려 실패 → 원인 → 개선 → 재측정을 반복한 "성능 고도화 단계"를 보여 준다. 정본은 `delivery-service/docs/tasks/T08.md` "성능 고도화 단계" 절(실패 기준: 생성 p95 500ms 또는 실패·dropped 1% 초과, 30만 건, 같은 조건 2회). 단계 0(허브 잠금, 정확성), 1(D-01 조회 병목 → 복합 인덱스)까지 정리, 1의 개선 뒤 값과 단계 2(조회 20 고정, 생성 80→320, 동기·비동기 비교)를 측정 중. 구현(비동기 배정)은 커밋 전, 설계 이유는 결정 로그 27(성능 근거는 재측정으로 반증됨, 마지막에 한 번 정정).

## 진행 상태
- [x] 설계 문서: 결정 로그 27, T08 태스크 문서, 학습 노트 `2026-10-19-sync-lock-vs-async-assignment.md`
- [x] 정함: `manager_id` NULL + `WAITING`, 후보 0명은 대기 + 30초 주기 재시도 + MASTER D-04, 주기 작업 30초
- [x] 구현(커밋 전): V3 마이그레이션, `ManagerAssignmentService`, `InProcessAssignmentDispatcher`(신호 합치기·배치 창), `PendingAssignmentPoller`, `ManagerAssignmentConfig`, I-09에서 배정 제거, 테스트(Mac에서 `ManagerAssignmentIntegrationTest` 통과, 전체 테스트는 미확인)
- [x] 측정: 배치 창 비교(25+25, 40+40), 80+80 몰림 확인 → 배치 창 효과 없음(T08.md)
- [x] 측정: 비동기 한계와 배치 창(160·200 × 창 0·200 × 2회, T08.md 4차). 경계는 200+200 근처, 겹치는 부하에서 창 200이 DB CPU 약 절반. 200+200 창 200 2회차 무너짐 원인 미확인
- [x] 측정: 동기 락 재측정(빈 DB). 25·40 각 2회, 80 1회 모두 생성 p95 10~19ms, 실패 0. 예전 28건/초 상한은 재현 안 됨. 예전 측정은 30만 건 시드 DB였다(T08.md)
- [ ] 재측정(30만 건, 사용자 승인됨, T08.md "재측정 계획" 순서대로): 1 준비 → 2 동기+30만 → 3 비동기+30만 → 4 부분 인덱스로 두 버전 → 5 요약 표·결정 로그 27 정정
  - 1·2·3 끝. 동기·비동기 모두 30만 건에서 80 정상, 160에서 같이 무너짐. 병목은 D-01 조회(count+정렬 약 33ms/건). 4번 보류, 사용자 판단 대기. 비동기 스택 실행 중, 동기 스택 stop
  - 단계 0~3 측정 끝, T08.md 단계 표 채움(3은 640/초 통과, 다음 후보 WAL). 남은 것(사용자 결정): D-01 인덱스 마이그레이션(V4) 여부, 결정 로그 27 정정, 배치 창 기본값, 전체 테스트 → 커밋. 비동기 스택 실행 중(인덱스 SQL 적용 상태), 동기 스택 stop
- [ ] 200+200 창 200ms 무너짐 원인 확인 → 배치 창 기본값 결정
- [ ] 배치 창 기본값 200 → 0(`config-repo/delivery-service.yml`, `docker-compose.fault.yml`, `ManagerAssignmentConfig`), 결정 로그 27 정정(1,512번·4.7구간은 Mac 저하 때 값) — 사용자 확인 후
- [ ] 측정 기준선 확인 추가: 두 번째 예열 p95 > 100ms면 측정 중단 — 사용자 확인 후
- [ ] 전체 테스트 통과 확인 → T08 커밋(T08.md 기록 필수, commit-msg 훅) → dev PR
- [ ] 담당자 DM 시점 변경(배정 완료 알림, A 방식). order 코드도 직접 바꾸고 커밋을 나눈다 — 측정 뒤로 미룸
- [ ] 협업 로그 요청(프런트: 담당자 없는 구간 표시)

## 측정 규칙(오늘 배운 것)
- 측정 전: 카메라·화상회의 앱 끄기, 전원 연결, 부하 없이 `docker stats --no-stream` CPU 합계가 낮은지 확인. 19:00~20:05 측정은 Mac 저하(카메라 사용·발열 추정, delivery 기동 272초)로 무효
- 예열 2회(10+10) → 쉼 60초 → 배송 데이터 비움 → 측정 1분, 같은 조건 2회가 비슷할 때만 채택
- 부하 중 `-stats.txt`의 컨테이너 CPU 합계가 800%(Docker 할당)에 가까우면 포화
- 현재 안정 상태 기준값: 80+80에서 생성 p95 6~10ms, 실패 0, 배정 지연 p95 6~9ms

## 메모
- 동기 잠금 버전: 태그 `perf/sync-lock`(bc3177b, `feat/delivery-assignment-lock`, dev 미반영, dev와 148파일 차이). 태그는 아직 원격에 안 올렸다(`git push origin perf/sync-lock`)
- 결과 파일은 `load-test/results/`(저장소 밖). 이력서·블로그 초안은 `blog/`(개인, git 제외): `blog/portfolio-delivery-performance.md`, `blog/devlog/2026-10-19.md`
- 사용자가 직접 수정 중이던 `delivery-service/AGENTS.md`와 `DeliveryCreationService` 멱등성 주석은 `stash@{0}`에 있다
- `main`에 잘못 머지된 PR #19는 revert가 필요하다. merge commit이라 나중에 dev → main 때 revert를 다시 revert해야 한다
- 사용자 규칙: 질문에는 답만, 변경은 무엇을 바꿀지 말하고 "진행할까요?"로 확인. 커밋은 `git -c user.name=jihaneol -c user.email=6531z@naver.com commit`, AI 공동 작성자 줄 금지. 답 끝에 "기록: 경로"
