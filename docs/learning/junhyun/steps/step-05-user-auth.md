# 스텝 5. user 인증 (U-01, U-02, I-01)

- 날짜: 2026-10-13
- 결과: Gateway를 거쳐 회원가입(201), 중복 가입(409), 로그인(200, JWT) 확인 (완료 기준 2). I-01 내부 호출 확인(없는 사용자 404). user-service 테스트 7개 통과
- 결정 기록: [ADR 0003](../../../decisions/0003-auth-libraries.md)

## 만든 것 (`user-service/.../user/`)
| 계층 | 파일 | 역할 |
|---|---|---|
| domain | `User`, `AffiliationType`, `UserRepository`, `UserErrorCode` | `p_users` Entity, 저장소 인터페이스, DUPLICATE_USERNAME·LOGIN_FAILED·USER_NOT_FOUND |
| application | `AuthService`, `UserQueryService`, `TokenIssuer`, dto | 가입, 로그인, I-01 조회. 토큰 발급은 인터페이스로만 안다 |
| infrastructure | `UserJpaRepository`, `JwtProvider`, `JwtProperties`, `SecurityConfig` | Spring Data, JJWT, BCrypt |
| presentation | `AuthController`, `InternalUserController`, dto | `/api/v1/auth/signup`, `/login`, `/internal/users/{id}` |
| presentation.sample | `AuthSampleController` | `/api/v1/users/sample/me`, `/sample/admin`(MASTER). 채택 후 삭제 |

## 리뷰 포인트
1. **계층 의존 방향**: domain의 `UserRepository`는 Spring Data를 모른다. infrastructure의 `UserJpaRepository extends JpaRepository<User, UUID>, UserRepository` 한 줄이 구현을 대신한다. application은 `TokenIssuer` 인터페이스만 알고 JJWT를 모른다.
2. **탈퇴자 username 중복 확인**: `@SQLRestriction("deleted_at IS NULL")` 때문에 JPQL과 메서드 이름 쿼리는 탈퇴자를 보지 못한다. username은 탈퇴 후에도 재사용할 수 없으므로(SA 5.2) 제한이 적용되지 않는 네이티브 쿼리로 확인한다. 반대로 로그인과 I-01은 탈퇴자가 자동으로 빠져서 LOGIN_FAILED, 404가 된다.
3. **동시 가입**: 존재 확인과 저장 사이에 같은 username 가입이 끼어들 수 있다. 최종 방어선은 DB 유니크 제약이고, `DataIntegrityViolationException`을 같은 409로 바꾼다.
4. **로그인 실패 메시지 통일**: 없는 사용자, 탈퇴 사용자, 비밀번호 불일치를 모두 LOGIN_FAILED로 응답해 계정 존재 여부를 노출하지 않는다.
5. **BCrypt만 쓰는 이유**: `spring-boot-starter-security`를 넣으면 기본 필터 체인이 모든 API를 막는다. 인증은 Gateway가 맡으므로 `spring-security-crypto`만 쓴다.
6. **JWT 내용**: `sub`(userId), `username`, `role`, `iat`, `exp`(60분). payload는 Base64로 인코딩만 되므로 누구나 읽을 수 있다. 비밀번호나 개인정보를 넣지 않는다. 서명으로 위조만 막는다.
7. **UUID 생성**: `@GeneratedValue(strategy = GenerationType.UUID)`로 Hibernate가 애플리케이션에서 만든다. id를 직접 넣고 `save()`하면 Spring Data가 새 Entity로 보지 않아 merge(SELECT 후 INSERT)를 하는데, 그 문제를 피한다.
8. **Audit 확인**: 회원가입은 헤더가 없는 요청이라 `created_by`, `updated_by`가 `SYSTEM`으로 저장됐다(DB 조회 확인).
9. **설정**: `jwt.secret`은 `${JWT_SECRET}` 자리표시자만 두고 기본값이 없다. 로컬 실행 시 환경 변수를 지정해야 한다.

## 발견한 보안 공백 (스텝 6에서 해결)
- 지금은 Gateway에 인증 필터가 없어, 클라이언트가 `X-User-Role: MASTER` 헤더를 직접 넣으면 `/sample/admin`이 200으로 통과한다. 서비스가 헤더를 믿는 구조라서 Gateway가 헤더를 지우고 다시 넣어야 한다.

## 면접 포인트
- JWT와 세션 방식의 차이, JWT의 단점(즉시 무효화가 어렵다 → 이 프로젝트는 Gateway의 60초 캐시 재확인으로 보완)
- BCrypt가 같은 비밀번호도 매번 다른 해시를 만드는 이유(salt)와 느리게 설계된 이유
- 유니크 제약 경쟁 조건과 애플리케이션 검사의 한계
