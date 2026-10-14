# 스텝 8. springdoc, QueryDSL 공통 설정 (시간이 남으면 하는 범위)

- 날짜: 2026-10-13
- 결과: Gateway Swagger UI에서 서비스 5개 문서를 선택해 조회, QUser·QBaseEntity 생성 확인. 전체 빌드와 e2e(PASS 11/0) 회귀 없음
- 결정 기록: [ADR 0004](../../decisions/0004-springdoc-querydsl.md)

## 만든 것
| 파일 | 역할 |
|---|---|
| `common/build.gradle` | springdoc webmvc-api, querydsl-jpa를 `api`로 노출 |
| 루트 `build.gradle` | common과 비즈니스 서비스에 QueryDSL apt 설정 |
| `common/.../config/QuerydslConfig` | `JPAQueryFactory` 빈 |
| `common/.../config/OpenApiConfig` | 서비스 문서 제목, 상대 서버 주소, Bearer 인증 버튼, `@CurrentUser` 숨김 |
| `gateway-service/build.gradle` | springdoc webflux-ui |
| `config-repo/gateway-service.yml` | `/{서비스}/v3/api-docs` 라우트 5개, Swagger UI 서비스 목록, 화이트리스트 추가 |
| `config-repo/application.yml` | `springdoc.paths-to-exclude: /internal/**` |

## 사용법
- 문서: http://localhost:8080/swagger-ui.html → 오른쪽 위에서 서비스 선택 → Authorize에 로그인 토큰 입력 → Try it out
- 검색 쿼리:
  ```java
  @Repository
  @RequiredArgsConstructor
  public class HubQueryRepositoryImpl implements HubQueryRepository {
      private final JPAQueryFactory queryFactory;
      // queryFactory.selectFrom(QHub.hub).where(...).fetch();
  }
  ```

## 리뷰 포인트
1. **QBaseEntity 문제**: 처음에는 비즈니스 서비스에만 apt를 걸었는데, `QUser extends QBaseEntity`라서 common에서도 Q클래스를 만들어야 컴파일된다. 빌드 실패로 확인하고 common을 포함했다.
2. **상대 서버 주소**: 서비스 문서의 servers를 `/`로 두면 Try it out 요청이 문서를 연 주소(Gateway)로 간다. 기본값이면 서비스 내부 IP가 찍혀 브라우저에서 호출할 수 없다.
3. **`@CurrentUser` 숨김**: 처음에는 문서에 `user`라는 필수 query 파라미터로 보였다. `SpringDocUtils.addAnnotationsToIgnore`로 뺐다.
4. **문서 경로 화이트리스트**: `/swagger-ui.html`, `/swagger-ui/**`, `/webjars/**`, `/v3/api-docs/**`, `/*/v3/api-docs/**`는 토큰 없이 열린다. 운영에서는 문서 노출 여부를 따로 정해야 한다.
5. **QueryDSL 포크**: 패키지명이 `com.querydsl.*` 그대로라 기존 강의와 블로그 코드를 그대로 쓸 수 있다.

## 면접 포인트
- QueryDSL을 쓰는 이유(타입 안전한 동적 쿼리, 컴파일 시점 검증)와 Q클래스 생성 원리(annotation processor)
- MSA에서 API 문서를 Gateway로 모으는 방법
