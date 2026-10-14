@agent.md

# gateway-service 세부 규칙

> 루트 `CLAUDE.md`(대전제·공통 규칙)를 전제로 한다. 서비스 정보·API 계약·커뮤니케이션 로그는 위에서 불러온 `agent.md`에 있다.
> 원본: `Docs/reference/SA문서.md`, 루트 `CLAUDE.md` §3-1, §8-1

## 1. 인증 처리
- WebFlux 기반이므로 `common` 모듈을 의존하지 않는다.
- JWT 검증 후 클라이언트가 보낸 `X-User-*` 헤더를 제거하고 재주입한다. 헤더 이름은 common `UserHeaders`와 같아야 한다.
- JWT 계약(HS256, `sub`=userId, `username`, `role`, `iat`, `exp`)은 루트 §8-1이며 user-service `JwtTokenIssuer`와 반드시 같아야 한다.
- `/internal/**`는 라우팅하지 않는다 (외부 차단).

## 2. 권한 재확인 (60초 캐시)
- I-01로 사용자 존재·권한을 확인하고 결과를 **userId 기준 60초 캐시**(Caffeine).
- I-01 응답의 `userId, username, role, isDeliveryManager`로 헤더를 주입한다.
- 탈퇴 404 → 401, 권한 불일치 → 401 `TOKEN_ROLE_MISMATCH`, user-service 장애 → 503.
- 캐시 때문에 권한 변경·탈퇴는 최대 60초 늦게 반영된다 (팀 결정).

## 3. 라우팅·권한 정책
- 새 외부 경로를 추가하는 서비스는 이 모듈 `agent.md` 로그에 라우팅 추가 요청을 남긴다 (예: 새 리소스 경로 prefix).
- MASTER 전용 같은 **권한 규칙은 Gateway에 두지 않는다** — 각 서비스의 `@MasterOnly`·소유자 검증으로 처리.
- 응답 타임아웃 30초 (루트 §9).
