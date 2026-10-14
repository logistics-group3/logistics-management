@agent.md

# user-service 세부 규칙

> 루트 `CLAUDE.md`(대전제·공통 규칙)를 전제로 한다. 서비스 정보·API 계약·커뮤니케이션 로그는 위에서 불러온 `agent.md`에 있다.
> 원본: `Docs/reference/SA문서.md`, 루트 `CLAUDE.md` §3-2, §8-1

## 1. 회원·권한
- **회원가입 검증**: SA 기준 — username 4~10자. 그 외 형식 규칙은 추후 결정 (USER-002). 가입 시 권한은 항상 `USER`.
- **초기 MASTER 계정**: 기동 시 `.env`의 `MASTER_USERNAME`/`MASTER_PASSWORD`로 없으면 생성 (비워 두면 생략).
- 권한은 `MASTER`/`USER` 2단계. 배송담당자는 `is_delivery_manager` 속성 + `hub_id`(지정 시 필수, I-02로 허브 존재 확인).
- 로그인 실패는 아이디·비밀번호 구분 없이 401 `LOGIN_FAILED`.
- username 유니크는 부분 인덱스(`db/indexes.sql`) — 탈퇴한 username은 재사용 가능.

## 2. JWT·Gateway 연동
- JWT 계약은 루트 §8-1 (gateway `JwtVerifier`와 반드시 같아야 한다).
- I-01 응답은 Gateway가 매 요청 권한 재확인에 쓰고 60초 캐시한다 → 권한·탈퇴 변경은 최대 60초 늦게 반영된다.

## 3. 컬럼 길이
- `p_users`: `username varchar(10)`(4~10자), `password varchar(100)`(BCrypt 60자 + 여유), `name varchar(50)`, `slack_id varchar(50)`, `affiliation_name varchar(100)`
