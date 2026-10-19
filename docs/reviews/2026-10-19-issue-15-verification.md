# #15 업체 삭제·상품 생성 경쟁 구현·검증

- 날짜: 2026-10-19.
- 브랜치: feat/delivery-T07. 사용자가 #13과 같은 브랜치에서 #15 수정과 PR 생성을 요청했다.
- 상태: [PR #17](https://github.com/logistics-group3/logistics-management/pull/17) 생성·기능 커밋 CI 확인 완료, 팀 리뷰 대기.
- 관련: [#15](https://github.com/logistics-group3/logistics-management/issues/15), [사전 확인한 계획](2026-10-19-issue-15-plan.md), [현재 progress](../progress/feat-delivery-T07.md).

## 구현

CompanyRepository.findByIdForUpdate를 선언하고 CompanyJpaRepository가 PESSIMISTIC_WRITE·업체 ID 조건 JPQL로 구현한다. ProductService.create와 CompanyService.delete는 이 조회를 사용하고 기존 트랜잭션이 커밋할 때까지 같은 업체 행을 잠근다.

잠금 획득 후 기존 권한을 확인한다. 조회 결과가 없으면 기존 COMPANY_NOT_FOUND다. DB 스키마·DTO·공개 권한·일반 검색 경로는 변경하지 않았다.

## 실제 PostgreSQL 검증

CompanyProductConcurrencyIntegrationTest는 테스트 컨테이너의 PostgreSQL 16에서 실제 CompanyService·ProductService를 호출한다. 외부 hub·user API를 호출하지 않는다. 실행 순서는 래치로 커밋 직전에서 제어하고 기다리는 연결과 잠금 보유 연결을 pg_blocking_pids로 확인한다.

| 순서 | 수정 전 | 수정 후 |
|---|---|---|
| 생성 선행 | 업체 삭제가 먼저 끝나고 뒤늦게 저장된 활성 상품이 남아 불변식 단언 실패 | 업체 삭제가 생성 커밋을 기다리고 새 상품까지 논리 삭제 |
| 삭제 선행 | 상품 생성이 성공해 기대한 COMPANY_NOT_FOUND가 발생하지 않음 | 생성이 삭제 커밋을 기다린 뒤 COMPANY_NOT_FOUND |
| 다른 업체 | 독립 진행 | 한 업체의 커밋 대기 중 다른 업체 생성 성공 |

검증은 실제 잠금 대기와 최종 DB 값을 확인한다. 임의의 sleep 시간만으로 경쟁 여부를 판정하지 않는다. 각 테스트는 임의 UUID로 데이터를 분리하고 스레드·트랜잭션을 종료한다.

## 검증 명령과 범위

- 수정 전: ./gradlew :company-service:test --tests '*CompanyProductConcurrencyIntegrationTest' — 3건 중 2건 실패를 확인했다.
- 수정 후: ./gradlew build — 성공. company 테스트 50개를 새로 실행했고 실패·오류·skip 0이다. 기존 46개에 동시성 3개와 삭제 대상 없음 1개를 추가했다.
- 전체 JUnit XML: 792개, 실패·오류·skip 0. 이번 build에서 다른 모듈 test는 UP-TO-DATE다. #13 배송 61개는 앞선 강제 새 실행 결과다.
- 프런트 build·lint·format:check·단위 test — 성공, 48개 통과.
- 최종 E2E: 두 수정이 포함된 서비스 이미지를 다시 빌드한 전용 logistics-ui-test에서 19개 통과(2.8분). Slack DM false·Webhook 비활성화와 전용 DB 확인을 유지하고 UI_TEST_BASE_URL=http://127.0.0.1:5175를 사용했다. 업체·상품 생성·삭제와 주문·배송·권한 시나리오를 포함한다.
- 테스트 후 원래 담당자 자격 복구와 #13 테스트 계정·주문 잔존 0건을 API로 확인했다.
- 환경 복구: 테스트 스택을 내리고 DB 볼륨은 보존했다. 이번 Vite 5175만 종료했고 기존 개발 스택·life-manager 5174는 유지했다. .env.ui-test는 백업과 SHA256이 일치한다.

## PR과 CI

- [PR #17](https://github.com/logistics-group3/logistics-management/pull/17): feat/delivery-T07 → dev. OPEN, 충돌 없음 확인.
- 코드 커밋: #13 91f2227, #15 9607154. 기준 dev a6313ee.
- 기능 커밋 9607154에서 GitHub docs-check·error-codes-check 모두 SUCCESS를 확인했다. 두 CI는 전체 백엔드·프런트·E2E를 실행하는 작업이 아니며 위 로컬 검증과 구분한다.
- 이 PR 기록은 후속 문서 커밋에 포함한다. 병합·실제 운영 배포·이슈 수동 종료는 수행하지 않았다.

## 범위와 비용

같은 업체의 생성은 직렬화돼 높은 동시 생성 부하에서는 대기가 늘 수 있다. 처리량·잠금 대기 시간의 부하 시험은 수행하지 않았다. 다른 업체의 독립 진행은 DB 테스트로 확인했다.

이번 수정은 P-01·C-05 경쟁 보호다. 다른 업체·상품 수정 경로의 모든 동시성 문제, 권한 정책 변경, #11 배송 상태 잠금은 별도 범위다.
