# 스텝 10-3. user-service와 설정 클라이언트 보강 (리뷰 반영)

- 날짜: 2026-10-14
- 근거: [리뷰 1](review-01-common-infra.md) major 3(동시 가입 500), minor 15(Swagger), minor 16(config fail-fast), nit(로그인 타이밍)
- 결과: user-service 테스트 9개 통과, config-server 없이 띄우면 즉시 실패하는 것을 실행으로 확인

## 무엇을 고쳤나
| 문제 | 수정 |
|---|---|
| 같은 username 동시 가입 시 409 대신 500 | `save()` → `saveAndFlush()`. INSERT를 try 안에서 실행해 유니크 위반을 409로 바꾼다 |
| 없는 사용자 로그인은 BCrypt 비교를 건너뛰어 응답이 빨라, 시간으로 계정 존재를 추측할 수 있음 | 사용자가 없으면 더미 해시와 비교해 같은 비용을 쓴다 |
| Swagger가 회원가입·로그인에도 Bearer가 필요하다고 표시 | `AuthController`에 빈 `@SecurityRequirements` |
| config-server 없이 서비스를 띄우면 기본값(포트 8080, datasource 없음)으로 떠서 헷갈리게 실패 | 서비스 6개 `application.yml`에 `spring.cloud.config.fail-fast: true` |

## 왜 save()로는 409가 안 됐나
- `@GeneratedValue(strategy = UUID)`이면 Hibernate는 id를 미리 만들 수 있다. 그래서 `persist` 시점에 INSERT를 실행하지 않고, 쓰기 지연 저장소에 모아 두었다가 flush(보통 커밋 직전)에 실행한다.
- 커밋은 `@Transactional` 프록시가 메서드가 끝난 뒤에 한다. 그래서 유니크 위반 예외는 메서드 안의 try/catch를 이미 빠져나간 뒤에 터지고, 500으로 응답했다.
- `saveAndFlush()`는 그 자리에서 flush해 INSERT를 실행하므로, 예외가 try 안에서 난다. 실제 PostgreSQL에서 동시 가입 결과가 201과 409인지는 10-6의 Testcontainers 테스트로 확인한다.
- 참고: `IDENTITY` 전략이면 id를 DB가 만들기 때문에 persist 즉시 INSERT를 한다. 같은 코드라도 id 전략에 따라 예외가 나는 시점이 달라진다.

## optional과 fail-fast를 같이 쓰는 이유
- `optional:configserver:`는 config 클라이언트가 꺼져 있을 때(테스트에서 `spring.cloud.config.enabled=false`) import 자체를 건너뛰게 해 준다.
- `fail-fast: true`는 클라이언트가 켜져 있는데 접속하지 못하면 기동을 멈춘다.
- 확인: `CONFIG_SERVER_URL=http://localhost:1`로 띄우면 `ConfigClientFailFastException: Could not locate PropertySource and the fail fast property is set` 후 종료 코드 1

## 면접 포인트
- JPA 쓰기 지연과 flush 시점, id 생성 전략(IDENTITY, SEQUENCE, UUID)에 따른 INSERT 시점 차이
- 타이밍 공격과, 로그인 실패 응답을 통일하는 이유
