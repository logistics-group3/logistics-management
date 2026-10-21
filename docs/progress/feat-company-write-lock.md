# feat/company-write-lock

## 지금 하는 일
코드 품질 감사([2026-10-20](../reviews/2026-10-20-code-quality-audit.md))의 현존 항목 7개(1~5·7·8)를 모두 수정했다(6번은 오탐 철회). 구현·검증 완료, PR 리뷰 대기.

## 진행 상태
- [x] 감사 8개 항목을 최신 dev와 대조: 1~5·7·8 현존, 6은 오탐(hub에 `ProductClient`가 있다). 6번은 문서에서 철회했다.
- [x] 1번: `ProductService` P-04·P-05가 업체 행을 먼저 잠근다(`getLockedProductOfCompany`).
- [x] 2번: `CompanyService.update` 트랜잭션 안 조회를 `findByIdForUpdate`로 바꿨다.
- [x] 테스트: 단위 3개 추가·수정, PostgreSQL 잠금 대기 회귀 2개(수정을 빼면 2개 모두 실패하는 것을 확인).
- [x] 3번 `api()` 본문 수신 실패, 4번 배송 담당자 조회 재사용, 5번 I-17 검증 일원화, 7번 MASTER 검사 재사용, 8번 `notDeleted` 삭제. 사용자 요청으로 같은 브랜치·PR에 포함했다.
- [x] 검증: `./gradlew build` 성공, 프런트 build·lint·format:check·test(58개) 성공. 브라우저 E2E는 미실행.

## 다음 단계
1. PR 리뷰·병합. 8번은 `common`을 고쳐 팀 전체 리뷰가 필요하다.
2. 후보: 감사 프런트 구조 개선 제안(CreateOrder 분리, Catalog 타입 제한)은 다음 기능 변경 때.
