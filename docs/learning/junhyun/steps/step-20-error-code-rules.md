# 스텝 20. 에러 코드 이름 규칙과 통합 목록

- 날짜: 2026-10-14
- 근거: Notion "팀 합의 대조·추가 검토" 3장 "접두어로 중복 방지하자 / 에러 코드를 통합해서 볼 수 있는 문서를 잘 작성해야 함". 규칙 문안은 KMH `common/agent.md` COMMON-001과 haneol `.claude/rules/common.md`를 합쳤다.
- 결과: `script/error-codes-check.sh` PASS(코드 15개). 다른 모듈에 같은 코드와 문서에 없는 코드를 둔 임시 enum으로 실패 경로(중복 1건, 누락 1건, 종료 코드 1)를 확인했다.

## 바꾼 것
| 파일 | 내용 |
|---|---|
| `docs/error-codes.md` | 공통 12개, user 3개 구현 코드와 SA에 정의된 서비스별 예정 코드 10개 |
| `script/error-codes-check.sh` | `ErrorCode`를 구현한 enum의 상수를 읽어 서비스 사이 중복과 문서 누락을 검사. macOS 기본 bash 3.2에서도 동작 |
| `CommonErrorCode` | `INVALID_STATUS_TRANSITION`(409) 추가. order와 delivery가 같은 의미로 쓰는 SA 공통 코드라 서비스 enum에 각각 두면 중복이 된다 |
| `docs/agents/coding-conventions.md` 2절 | 이름 규칙 |

## 리뷰 포인트
1. **중복이 문제가 되는 이유**: `FeignErrorDecoder`는 하위 서비스의 4xx 코드를 그대로 클라이언트에 전달한다. order가 company를 불러 `COMPANY_NOT_FOUND`를 받으면 클라이언트도 그 코드를 받는다. 두 서비스가 `NOT_FOUND_ITEM`처럼 같은 이름을 다른 뜻으로 쓰면 클라이언트가 원인을 구분할 수 없다.
2. **검사를 테스트가 아닌 스크립트로 둔 이유**: 각 모듈의 테스트는 자기 클래스패스만 본다. 다섯 서비스의 enum을 한 번에 보려면 소스를 직접 읽어야 한다. 빌드 없이 몇 초 안에 끝난다.
3. **공통으로 올리는 기준**: 같은 이름, 같은 의미, 같은 HTTP 상태를 두 서비스 이상이 쓰면 `CommonErrorCode`로 올린다.

## 팀원에게 알릴 것
- 서비스 에러 코드를 추가할 때 `docs/error-codes.md`의 해당 서비스 표에서 상태를 `예정` → `구현`으로 바꾸거나 새 줄을 추가한다.
- PR 전에 `script/error-codes-check.sh`를 실행한다. PR B에서 CI 점검에 넣는다.
