---
paths:
  - "*-service/src/main/java/**/application/**"
---
# application 규칙

적용 경로: `*-service/src/main/java/**/application/**`

## 클래스
- 쓰기는 `{X}Service`, 조회는 `{X}QueryService`로 둔다. 유스케이스 단위 이름도 된다(`AuthService`).
- `@Transactional`은 public 메서드에 둔다. 조회만 하는 클래스는 클래스에 `@Transactional(readOnly = true)`를 둬도 된다(`UserQueryService`).
- 메서드 Javadoc에 SA API ID와 핵심 규칙을 한 줄로 쓴다: `/** O-05 주문 취소. 집하 시작 후면 INVALID_STATUS_TRANSITION */`

## 입출력 DTO (`application/dto`)
- 입력은 `{동작}{X}Command`, 검색 조건은 `{X}SearchCondition`, 출력은 `{X}Result`이고 모두 `record`다. `Result`에는 `static from(Entity)`를 둔다.
- Entity를 application 밖으로 반환하지 않는다. presentation DTO를 import하지 않는다.

## 흐름
```java
@Transactional
public CompanyResult update(UserContext user, UUID companyId, UpdateCompanyCommand command) {
    Company company = getCompany(companyId);                 // 1. 조회(없으면 BusinessException)
    user.validateSelfOrMaster(company.getManagerId());       // 2. 소유자 검증
    company.update(command.name(), command.address());       // 3. Entity 메서드로 변경(변경 감지)
    return CompanyResult.from(company);                      // 4. Result 변환
}
```
- 상태 변경은 Entity 메서드로 한다. 서비스에서 필드를 하나씩 바꾸지 않는다.
- 새로 만들 때만 저장한다. 유니크 제약 위반을 그 자리에서 잡아야 하면 `saveAndFlush`를 쓴다(`AuthService.signup`).
- 삭제는 `entity.softDelete(user.username())`다. 물리 삭제하지 않는다.

## 권한과 소유자
- 역할(MASTER/USER)은 컨트롤러 `@RequireRole`이, 소유자·담당자 확인은 여기서 `UserContext` 헬퍼로 한다.
- 본인 리소스만 보이는 검색은 조건에 `userId`를 넣어 조회한다. 가져온 뒤 거르지 않는다.

## 다른 서비스 호출
- application에 인터페이스를 두고 infrastructure/client에서 Feign으로 구현한다([feign.md](feign.md)).
- 원격 에러는 `BusinessException`으로 올라온다. 분기가 필요하면 `e.getErrorCode().code()`를 문자열 상수와 비교한다.
- 여러 서비스를 차례로 바꾸는 흐름(주문 Saga)은 실패 지점 이후의 보상 호출을 명시하고, 보상 실패는 로그와 상태값으로 남긴다(SA 7장).
- 오래 걸리는 원격 호출은 DB 트랜잭션 밖에서 하거나 트랜잭션 범위를 메서드로 나눈다.

## 하지 않는 것
- `RuntimeException` 등을 직접 던지기(→ `BusinessException`)
- Spring Data 인터페이스나 `JPAQueryFactory`를 application에서 직접 쓰기(→ domain의 Repository 인터페이스)
- 설정값을 `@Value`로 흩어 받기(→ `@ConfigurationProperties`, [config.md](config.md))
