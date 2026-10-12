# .claude/rules — 코드 컨벤션

Claude Code가 작업할 때 자동으로 읽는 규칙이고, 사람이 리뷰할 때도 같은 기준으로 쓴다.
`paths`가 없는 파일은 항상 적용, 있는 파일은 해당 경로를 다룰 때 적용된다.

| 파일 | 적용 경로 | 내용 |
|---|---|---|
| `common.md` | 항상 | 계층 구조, common 모듈 사용, 예외, 네이밍, Lombok, 문서 동기화 |
| `controller.md` | `**/presentation/**` | URL, 응답 래퍼, 권한, Request/Response DTO, Swagger |
| `service.md` | `**/application/**` | 트랜잭션, Command/Result, 소유자 검증, 외부 호출 포트 |
| `domain.md` | `**/domain/**` | Entity, enum, Repository 인터페이스, ErrorCode |
| `repository.md` | `**/infrastructure/persistence/**` | JPA·QueryDSL 구현, 논리 삭제, 페이징·정렬, 락 |
| `feign.md` | `**/infrastructure/client/**` | FeignClient, 내부 API 호출, 에러 처리 |
| `erd.md` | `doc/sql/**`, `**/domain/model/**` | DDL 작성 규칙 (이름·제약·인덱스·주석) |
| `config.md` | `config/**`, `**/application*.yml`, `**/*Properties.java` | 설정·비밀값·refresh 가능한 설정 |
| `test.md` | `**/src/test/**` | 테스트 작성 |

리뷰 체크리스트: `doc/guide/14-review-checklist.md`
