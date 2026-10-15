# feat/delivery-T01

## 지금 하는 일
T01 배송 도메인 기반 구축 (구간 모델 확정, 마이그레이션 전)

## 진행 상태
- [x] `delivery-service/docs/` 작업 규칙, 태스크 목록, T01 문서 작성 (문서만 변경)
- [x] `docs/domain/` 정리(이모지, 옛 경로, 저장소 상태 반영)와 결정 로그 규칙, 결정 로그 11 추가 (문서만 변경)
- [x] 개인 훅(`.claude/hooks/context-guard.sh`, `post-compact.sh`) 단독 실행 테스트 통과. context-guard는 실제 세션에서 142k 토큰에 발동 확인(10/15), post-compact는 압축 전이라 미확인
- [x] ADR 0003 채택(SA v2.0 3구간), 0001 대체됨 (문서만 변경)
- [ ] 마이그레이션, 엔티티·상태 전이, 에러 코드, Repository
- 면접 포인트: 상태 전이를 서비스가 아니라 엔티티 메서드에서 검증하는 이유, 배송 상태를 구간 상태에서 계산하는 이유

## 다음 단계
1. SA 5.6과 ADR 0003을 보고 `V1__create_delivery_tables.sql`을 작성한다.
2. Delivery, DeliveryRoute 엔티티와 상태 전이 메서드, 3구간·2구간 단위 테스트.

세부 작업과 커밋 기록은 [`delivery-service/docs/tasks/T01.md`](../../delivery-service/docs/tasks/T01.md)에 있다.

## 직접 해보기
1. `git switch -c feat/delivery-T{nn}`으로 태스크 브랜치를 만든다.
2. `delivery-service/docs/tasks/T{nn}.md`를 4절 형식(`docs/workflow.md`)으로 만들고 세부 작업을 나열한다.
3. 세부 작업 하나를 끝낼 때마다 작업 기록에 항목을 추가하고 코드와 함께 커밋한다.

## 메모
- `docs/design/05-infra*`는 공통 작업 브랜치로 올린다(스테이징 해제함).
- 루트 `.gitignore`에 개인용 `blog` 줄이 커밋되지 않은 채 남아 있다. 개인 제외 경로는 `.git/info/exclude`로 옮기는 것이 팀 규칙이다.
