# 리뷰 체크리스트

PR을 리뷰할 때 계층별로 확인한다. 근거는 [레이어별 규칙](../agents/rules/README.md)이다. 원본은 haneol `doc/guide/14-review-checklist.md`이고 이 저장소 구조에 맞췄다.

## 모든 PR
- [ ] `./gradlew build`가 통과했다. PR 본문에 확인한 명령과 결과가 있다.
- [ ] 전체 빌드·프런트·소비 API 계약 변경에는 프런트 build·lint·format:check·test도 확인했다([명령·범위](full-build.md)). 브라우저 E2E 실행 여부와 실제 Slack 비활성화를 별도로 적었다.
- [ ] 비밀값(토큰, 실제 비밀번호, `.env`)이 들어가지 않았다.
- [ ] 다른 서비스의 코드를 직접 고치지 않았다. 필요한 변경은 그 서비스 협업 로그에 요청했다.
- [ ] 계층 의존 방향을 지켰다(presentation → application → domain ← infrastructure, `coding-conventions.md` 1절).
- [ ] 쓰지 않는 코드, 지금 필요 없는 추상화가 없다.
- [ ] 코드와 함께 바뀌어야 할 문서를 고쳤다(`docs/doc-map.tsv`, `script/docs-check.sh`).
- [ ] 스킬(`.agents/skills/`)을 추가·수정·삭제했다면 이유가 PR 본문에 있다.

## presentation ([controller.md](../agents/rules/controller.md))
- [ ] 외부 API는 `ApiResponse`, 내부 API는 DTO 그대로 응답한다.
- [ ] Entity·`Result`를 그대로 응답하지 않는다.
- [ ] MASTER 전용 API에 `@RequireRole(Role.MASTER)`가 있다.
- [ ] Request에 SA 8.5 검증이 있고, `@Operation`에 SA API ID가 있다.

## application ([service.md](../agents/rules/service.md))
- [ ] 소유자·담당자 검증이 있다(`validateSelfOrMaster` 등).
- [ ] 트랜잭션 경계와 `readOnly`가 맞다. 오래 걸리는 원격 호출이 트랜잭션 안에 있지 않다.
- [ ] 상태 변경이 Entity 메서드로 이뤄진다.
- [ ] 여러 서비스를 바꾸는 흐름에 실패 지점별 보상이 있다.

## domain ([domain.md](../agents/rules/domain.md))
- [ ] `BaseEntity` 상속, `@SQLRestriction("deleted_at IS NULL")`, `@Setter` 없음.
- [ ] 다른 서비스 리소스는 UUID 필드만 갖는다.
- [ ] 새 에러 코드가 `{리소스}_{상황}` 규칙을 따르고 `docs/error-codes.md`에 있다(`script/error-codes-check.sh`).

## persistence와 마이그레이션 ([repository.md](../agents/rules/repository.md), [migration.md](../agents/rules/migration.md))
- [ ] Entity와 마이그레이션 SQL이 함께 바뀌었다. 커밋된 마이그레이션 파일을 고치지 않았다.
- [ ] 정렬이 허용 목록으로만 된다(`QuerydslSupport`, `SortAllowList`).
- [ ] N+1이 없다. 네이티브 쿼리에 `deleted_at IS NULL`이 있다.
- [ ] 동시에 바뀌는 행은 락이나 조건부 UPDATE로 보호한다.

## Feign ([feign.md](../agents/rules/feign.md))
- [ ] `/internal/**`만 호출하고 DTO 타입으로 받는다.
- [ ] 재시도는 GET과 멱등 API에만 붙였다.

## 테스트 ([test.md](../agents/rules/test.md))
- [ ] 새 public 메서드에 성공·실패 테스트가 있다. DB 동작은 `PostgresIntegrationTest`로 확인했다.
- [ ] 테스트가 구현 세부가 아니라 동작을 확인한다.
