# feat/company-write-lock

## 지금 하는 일
코드 품질 감사([2026-10-20](../reviews/2026-10-20-code-quality-audit.md))의 1·2번 수정. company-service의 상품·업체 쓰기가 업체 행 잠금을 일관되게 쓰도록 맞춘다. 구현·검증 완료, 커밋 전.

## 진행 상태
- [x] 감사 8개 항목을 최신 dev와 대조: 1~5·7·8 현존, 6은 오탐(hub에 `ProductClient`가 있다). 6번은 문서에서 철회했다.
- [x] 1번: `ProductService` P-04·P-05가 업체 행을 먼저 잠근다(`getLockedProductOfCompany`).
- [x] 2번: `CompanyService.update` 트랜잭션 안 조회를 `findByIdForUpdate`로 바꿨다.
- [x] 테스트: 단위 3개 추가·수정, PostgreSQL 잠금 대기 회귀 2개(수정을 빼면 2개 모두 실패하는 것을 확인).
- [ ] 3번(`api()` 본문 수신 실패)·4·5·7·8번은 이 브랜치 범위 밖. 다음 브랜치 후보.

## 다음 단계
1. PR 리뷰·병합.
2. 감사 3번을 `feat/frontend-api-body-read` 같은 별도 브랜치로 처리.
