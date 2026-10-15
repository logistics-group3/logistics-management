---
title: MSA에서 Spring Security는 Gateway와 user-service 중 어디에 두나
date: 2026-10-13
updated: 2026-10-14
asker: Junhyun An
level: 중급
tags: [spring-security, gateway, jwt, msa, auth]
---

# MSA에서 Spring Security는 Gateway와 user-service 중 어디에 두나

## 질문
Spring Security도 미리 넣어 두면 좋을 것 같다. 원래 Gateway에 두지 않고 user-service에 두는 것인가.
(추가 질문, 2026-10-14) user-service는 왜 Spring Security를 안 쓰나. Gateway에서 쓰기로 한 것인가.

## 짧은 답
- 정답이 하나로 정해져 있지 않다. **"누가 토큰을 검증하는가"**로 위치가 갈린다.
- 이 프로젝트의 SA는 **Gateway가 인증을 맡는 구조**다. 기술 스택 다이어그램에도 Spring Security가 gateway-service에 적혀 있다(SA 3.1). 그래서 Spring Security를 넣는다면 Gateway가 1순위다.
- user-service는 토큰을 **발급**만 한다. 비밀번호 해시(BCrypt)만 있으면 되므로 Security 전체가 필요하지 않다.
- 비즈니스 서비스는 Gateway가 넣은 헤더를 믿고 역할만 확인한다(SA 8.3 7단계 `@RequireRole` + 인터셉터).

## 자세히

### 세 가지 배치 방식
| 방식 | 토큰 검증 위치 | Spring Security 위치 | 장점 | 단점 |
|---|---|---|---|---|
| Gateway 집중 (이 프로젝트 SA) | Gateway | Gateway(WebFlux) | 인증 로직이 한곳에 있다. 서비스가 단순하다 | 내부망을 신뢰해야 한다. 서비스 포트가 열리면 헤더 위조에 뚫린다 |
| 서비스별 리소스 서버 | 모든 서비스 | 모든 서비스(`oauth2-resource-server`) | 서비스마다 스스로 검증하는 제로 트러스트 | 서비스마다 Security 설정과 키 배포가 필요하다 |
| 인증 서버 분리 | 인증 서버 + 각 서비스 | 인증 서버(Spring Authorization Server)와 리소스 서버들 | OAuth2 표준, 외부 연동에 유리하다 | 부트캠프 범위에서는 과하다 |

### user-service에 Security를 두는 경우
- 로그인을 `AuthenticationManager`와 `UserDetailsService`로 처리하고 싶을 때다. 이 프로젝트의 로그인은 "아이디로 찾고 비밀번호를 비교해서 토큰 발급"이 전부라 이점이 작다.
- `spring-boot-starter-security`를 넣으면 기본 설정이 모든 API를 막는다(기본 로그인 폼, CSRF). 그래서 `permitAll`, CSRF 비활성화, 세션 미사용 설정이 따로 필요하다.

### Gateway에 Spring Security를 두면 달라지는 것
- JWT 서명·만료 검증을 직접 짠 코드 대신 `ReactiveJwtDecoder`(Nimbus)가 맡는다. 화이트리스트는 `pathMatchers(...).permitAll()`, 401·403 응답은 `ServerAuthenticationEntryPoint`, `ServerAccessDeniedHandler`로 표준화된다.
- 그래도 SA의 고유 규칙은 직접 구현해야 한다. 사용자 헤더 제거, `/internal` 차단, I-01 재확인과 60초 캐시, 헤더 주입이 그렇다. Security 체인 앞뒤에 커스텀 필터로 붙인다.
- 리액티브용 설정(`SecurityWebFilterChain`, `@EnableWebFluxSecurity`)을 써야 한다. 서블릿용 `SecurityFilterChain`과 다르다.

### 서비스에도 넣는다면
- Gateway가 넣은 헤더를 읽어 `SecurityContext`에 사용자를 넣는 사전 인증 필터를 만든다. 그러면 `@PreAuthorize("hasRole('MASTER')")`, `@AuthenticationPrincipal`을 쓸 수 있다.
- 지금의 `@RequireRole`, `@CurrentUser`와 역할이 겹치므로 둘 중 하나로 정한다.

## 추가: user-service는 왜 Spring Security를 안 쓰나 (2026-10-14)

**맞다. SA에서 인증은 Gateway가 맡기로 정했다.**
- SA 3.1 구성도에 Spring Security는 gateway-service에만 적혀 있다.
- SA 8.1 인증 흐름에서 user-service가 하는 일은 두 가지뿐이다. 로그인 때 BCrypt로 비밀번호를 확인하고, JWT를 발급한다. 토큰 검증은 Gateway가 한다.
- SA 8.3 7단계에서 각 서비스의 역할 확인은 커스텀 어노테이션(`@RequireRole`)과 인터셉터로 한다고 정했다.

**정확히는 "Spring Security의 일부만" 쓴다.**
- Spring Security는 여러 모듈로 나뉜다. 그중 `spring-security-crypto`는 비밀번호 해시(BCrypt) 같은 암호화 도구만 담은 모듈이다. 필터도, 로그인 처리도 없다.
- user-service는 이 crypto 모듈만 쓴다. 웹 요청을 가로채는 보안 필터 체인(`spring-boot-starter-security`)은 쓰지 않는다.

**필터 체인까지 넣으면 생기는 일**
- 스타터를 넣는 순간 Boot가 기본 보안 설정을 켠다. 모든 API가 인증을 요구하고, 기본 로그인 폼과 CSRF 검사가 생기며, 기동 로그에 임시 비밀번호가 찍힌다.
- 이미 Gateway가 인증을 끝낸 요청이라 이 필터들이 할 일이 없다. 결국 "전부 허용, CSRF 끄기, 폼 로그인 끄기" 같은 해제 설정만 늘어난다.
- 토큰을 user-service에서도 검증하려면 secret을 서비스마다 나눠 갖고, 같은 검증 로직을 중복해야 한다.

**user-service가 필터 체인까지 쓰게 되는 경우**
- user-service가 OAuth2 인가 서버(Spring Authorization Server)가 되어, 외부 앱에 토큰을 발급하고 동의 화면을 띄워야 할 때
- 내부망도 믿지 않는 구조로 바꿔 모든 서비스가 JWT를 직접 검증할 때(리소스 서버). 이때는 user-service만이 아니라 서비스 5개 모두에 넣는다.

## 이 프로젝트에서는
- 처음 구현은 Gateway도 Spring Security 없이 `WebFilter`로 SA 8.3 순서를 직접 구현했다(ADR 0003). SA와 달랐다.
- 이 질문 뒤에 Gateway를 Spring Security(WebFlux)로 전환했다(ADR 0005, 스텝 9). user-service는 BCrypt만, 비즈니스 서비스는 `@RequireRole`을 유지한다.

## 면접 포인트
- "MSA에서 인증은 어디서 하나": Gateway 집중과 서비스별 검증의 트레이드오프(단순함과 내부망 신뢰 대 제로 트러스트)를 설명한다.
- "Spring Security 필터 체인 동작 방식": `DelegatingFilterProxy` → `FilterChainProxy` → `SecurityFilterChain`. WebFlux는 `WebFilterChainProxy` → `SecurityWebFilterChain`.
- "서비스가 헤더를 믿어도 되나": 내부망 격리(포트 비공개, 네트워크 정책)가 전제다. 더 엄격하게 하려면 서비스도 JWT를 검증하거나 mTLS를 쓴다.

## 참고
- SA 3.1 인프라 구성도, 8.1~8.3
- https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html
