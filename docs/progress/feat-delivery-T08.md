# feat/delivery-T08

## 지금 하는 일
구간 담당자 배정을 배송 생성(I-09)에서 떼어 내 허브별 비동기 배치 배정으로 바꾼다. 태스크 문서는 `delivery-service/docs/tasks/T08.md`, 설계와 이유는 결정 로그 27.

## 진행 상태
- [x] 설계 문서: 결정 로그 27(동기 잠금에서 분리한 이유, 대안 비교, 설계, 단계), T08 태스크 문서, 학습 노트 `2026-10-19-sync-lock-vs-async-assignment.md`
- [x] 정함: `manager_id` NULL + `WAITING`, 후보 0명은 대기 + 30초 주기 재시도 + MASTER D-04, 주기 작업 30초
- [ ] 담당자 DM 시점 변경: 배정 완료 알림 방식(A). order 코드도 직접 바꾸고 커밋을 나눈다
- [ ] 협업 로그 요청(프런트: 담당자 없는 구간 표시)
- [ ] 구현과 측정

## 메모
- 동기 잠금 버전과 재현 테스트는 `feat/delivery-assignment-lock`(dev 미반영)에 있다. 재현 테스트만 옮겨 쓰고 브랜치는 그 뒤 지운다.
- k6 비교 기준: `load-test/results/fault-lock-40b-*`, `fault-lock-40c-*`(동기 잠금, 실패), `fault-cap5-40-*`(잠금 전). 결과 파일은 저장소에 올리지 않는다.
- 사용자가 직접 수정 중이던 `delivery-service/AGENTS.md`와 `DeliveryCreationService` 멱등성 주석은 `stash@{0}`에 있다.
- `main`에 잘못 머지된 PR #19는 revert가 필요하다. merge commit이라 나중에 dev → main 때 revert를 다시 revert해야 한다.
