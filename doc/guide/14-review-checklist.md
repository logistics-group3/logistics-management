# 14. 코드 리뷰 체크리스트

> 최종 수정: 2026-10-12 · 대상: **리뷰어**, PR 작성자 셀프 체크
> 근거 규칙: `.claude/rules/*.md` (각 항목 끝의 괄호)

## 공통
- [ ] 계층 의존 방향: presentation → application → domain ← infrastructure (`common.md`)
- [ ] common에 있는 것을 다시 만들지 않았다 (응답 래퍼, 예외, 사용자 헤더, 페이지 보정) (`common.md`)
- [ ] 예외는 `BusinessException(XxxErrorCode)`만 (`common.md`)
- [ ] 코드 변경과 같은 PR에서 문서·CHANGELOG 갱신 (`doc/README.md`)

## Controller (`controller.md`)
- [ ] 외부 API는 `ResponseEntity<ApiResponse<T>>`, 내부 API는 DTO 그대로
- [ ] MASTER 전용 API에 `@RequireRole(UserRole.MASTER)` — **누락이 가장 위험** (Gateway는 역할을 막지 않는다, ADR-0004)
- [ ] `@Valid` + Request record 검증 어노테이션
- [ ] Entity를 응답으로 내보내지 않는다
- [ ] `@Operation(summary)`에 SA API ID (예: `H-03 허브 수정`)

## Service (`service.md`)
- [ ] 클래스 `@Transactional(readOnly = true)`, 쓰기 메서드만 `@Transactional`
- [ ] 소유자 검증(본인 주문·담당 업체)을 `UserContext`로 수행
- [ ] Feign 호출 실패 시 보상 처리 또는 예외 전파가 명확하다
- [ ] 트랜잭션 안에서 오래 걸리는 외부 호출을 하지 않는다 (또는 이유가 주석에 있다)

## Domain · Repository (`domain.md`, `repository.md`)
- [ ] Entity에 `@Setter` 없음, 상태 변경은 의도가 드러나는 메서드
- [ ] `@SQLRestriction("deleted_at IS NULL")`, 삭제는 `softDelete(username)`
- [ ] 다른 서비스 테이블 참조는 UUID 필드만 (연관관계 매핑 없음)
- [ ] native query에는 `deleted_at IS NULL`을 직접 넣었다
- [ ] 목록 정렬은 `QuerydslSorts` 허용 필드만, size는 `PageRequests.normalize`
- [ ] N+1 없음 (fetch join 또는 batch size)

## DDL (`erd.md`)
- [ ] Entity 컬럼·길이·nullable이 DDL과 일치 (`ddl-auto: validate`)
- [ ] 유니크는 부분 인덱스 `WHERE deleted_at IS NULL`
- [ ] 제약·인덱스 이름 규칙 (`pk_`, `uk_`, `ck_`, `fk_`, `idx_`)

## 설정 (`config.md`, `doc/guide/12-config-ops.md`)
- [ ] 비밀값 평문 없음, 새 환경 변수는 `.env.example`에 추가
- [ ] 서비스 고유 설정은 JavaBean `@ConfigurationProperties`

## 테스트 (`test.md`)
- [ ] 새 Service 메서드에 성공·실패 케이스 단위 테스트
- [ ] `@DisplayName` 한국어로 규칙을 설명
