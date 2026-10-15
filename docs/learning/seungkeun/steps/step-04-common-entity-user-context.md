# 스텝 4. common 엔티티·사용자 컨텍스트

- 날짜: 2026-10-13
- 결과: BaseEntity와 Audit, `@CurrentUser`, `@RequireRole`, Feign 헤더 전달 구현. common 테스트 누적 13개 통과, 전체 빌드 통과

## 만든 것 (`../../../../common/src/main/java/com/sparta/logistics/common`)
| 파일 | 역할 |
|---|---|
| `entity/BaseEntity` | Audit 6개(created/updated/deleted × at/by), `softDelete(username)`, `isDeleted()` |
| `config/JpaAuditingConfig` | `@EnableJpaAuditing` + `AuditorAware`(X-Username, 없으면 `SYSTEM`) |
| `auth/UserHeaders` | 헤더 이름 상수 4개 |
| `auth/Role`, `auth/UserContext` | `MASTER`/`USER`, 현재 사용자(userId, username, role, deliveryManager) |
| `auth/CurrentUser`, `auth/CurrentUserArgumentResolver` | `@CurrentUser UserContext user`로 헤더를 받는다. 헤더가 없으면 401 |
| `auth/RequireRole`, `auth/RequireRoleInterceptor` | `@RequireRole(Role.MASTER)`로 역할을 제한한다. 다르면 403 |
| `config/WebMvcConfig` | resolver와 interceptor 등록 |
| `feign/FeignHeaderInterceptor` | Feign 호출에 X-User-* 헤더 전달 |

## 서비스에서 쓰는 법
```java
@Entity
@Table(name = "p_hubs")
@SQLRestriction("deleted_at IS NULL")
public class Hub extends BaseEntity { ... }

@RequireRole(Role.MASTER)
@DeleteMapping("/{hubId}")
public ApiResponse<Void> delete(@PathVariable UUID hubId, @CurrentUser UserContext user) { ... }
```
- 서비스 메인 클래스의 `scanBasePackages = "com.sparta.logistics"` 덕분에 별도 등록 없이 동작한다.

## 리뷰 포인트
1. **Audit은 자동**: `AuditingEntityListener`가 저장 직전에 `AuditorAware`에게 사용자 이름을 물어 채운다. 회원가입처럼 헤더가 없는 요청과 요청 밖의 작업(시드 적재)은 `SYSTEM`으로 기록된다.
2. **`@SQLRestriction`은 Entity마다 붙인다**: Hibernate는 Entity 클래스의 어노테이션만 보기 때문에 BaseEntity에 붙여도 상속되지 않는다.
3. **헤더 신뢰의 전제**: 서비스는 X-User-* 헤더를 검증 없이 믿는다. Gateway가 클라이언트 값을 지우고 다시 넣기 때문이다(스텝 6). 그러려면 서비스 포트가 외부에 열리지 않아야 한다(compose에서 서비스 포트를 공개하지 않음).
4. **401과 403의 구분**: 헤더가 없거나 형식이 깨지면 401(누구인지 모름), 역할이 맞지 않으면 403(누구인지는 알지만 권한 없음).
5. **인터셉터에서 던진 예외도 공통 형식**: 인터셉터는 DispatcherServlet 안에서 실행되므로 GlobalExceptionHandler가 처리한다. 필터에서 던지면 처리되지 않는다.
6. **RequestContextHolder**: 현재 HTTP 요청을 ThreadLocal에 묶어 두는 장치다. AuditorAware와 FeignHeaderInterceptor가 컨트롤러 파라미터 없이 헤더를 읽을 수 있는 이유다. 비동기 스레드(`@Async`)에서는 비어 있다는 점에 주의한다.

## 면접 포인트
- ArgumentResolver, Interceptor, Filter의 차이와 실행 순서
- ThreadLocal 기반 컨텍스트의 한계(비동기, 스레드 풀 재사용 시 정리)
- 논리 삭제의 장단점과 유니크 제약 처리(부분 인덱스)
