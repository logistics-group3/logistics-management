# 스텝 53 — 업체 삭제와 상품 생성의 커밋 순서 보호

- 날짜: 2026-10-19.
- 브랜치: feat/delivery-T07. 사용자 요청으로 #13과 같은 브랜치에서 #15를 수정하고 PR로 검토한다.
- 결과 정본: [#15 구현·검증](../../../reviews/2026-10-19-issue-15-verification.md).

## 무엇을 바꿨나

CompanyRepository에 findByIdForUpdate를 선언하고 CompanyJpaRepository가 ID 조건 JPQL과 PESSIMISTIC_WRITE로 구현한다. ProductService.create와 CompanyService.delete가 이 조회로 같은 업체를 잠근 뒤 권한을 확인한다.

생성이 먼저면 삭제가 커밋을 기다려 새 상품까지 삭제한다. 삭제가 먼저면 생성이 기다린 뒤 활성 업체를 찾지 못해 기존 COMPANY_NOT_FOUND로 거절된다. 다른 업체의 생성은 독립 진행한다.

## 왜 필요한가

논리 삭제는 업체의 물리 행을 남긴다. 상품의 FK가 유효하다는 사실만으로 업체가 활성 상태라고 보장할 수 없다. 상품 생성의 업체 확인과 업체 삭제의 상품 조회·삭제를 같은 업체 행의 잠금으로 순서화해야 한다.

같은 업체의 생성은 직렬화돼 대기가 생길 수 있다. 일반 조회에 잠금을 추가하지 않고 P-01·C-05의 기존 트랜잭션에만 적용했다. 스키마·DTO·공개 권한을 바꾸는 작업은 아니다.

## 검증

수정 전 실제 PostgreSQL의 실행 순서를 제어해 3건 중 2건이 실패하는 것을 확인했다. 수정 후에는 두 순서와 다른 업체 독립 진행이 통과했다. 단순 sleep 대신 pg_blocking_pids로 실제 대기를 확인하고 최종 DB의 활성 상품이 0건인지 단언했다.

company 테스트는 기존 46개에 동시성 3개·삭제 대상 없음 1개를 추가해 50개다. 전체 build와 최종 E2E·PR 결과는 결과 정본을 따른다.

## 직접 확인

1. IntelliJ에서 CompanyJpaRepository.findByIdForUpdate와 두 Service의 호출부를 읽는다. 잠금과 권한 확인의 순서를 확인한다.
2. `./gradlew :company-service:test --tests '*CompanyProductConcurrencyIntegrationTest'`로 세 경우를 실행한다.
3. 생성 선행 테스트에서 서비스 메서드가 반환된 뒤에도 외부 트랜잭션의 커밋 전까지 잠금이 유지되는 이유를 설명해 본다.

면접 포인트: FK와 논리 삭제의 차이, 행 잠금과 트랜잭션 경계, 같은 리소스의 직렬화 비용과 다른 리소스의 독립 진행.
