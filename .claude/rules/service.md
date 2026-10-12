---
paths:
  - "backend/**/application/**"
---
# Service · application 규칙

## 클래스
- 이름: 쓰기 `{X}Service`, 조회 전용 `{X}QueryService` (조회가 많아지면 분리), 인증 등 유스케이스 단위도 가능 (`AuthService`)
- `@Service @RequiredArgsConstructor @Transactional(readOnly = true)` 를 클래스에, 쓰기 메서드에만 `@Transactional`
- 메서드 Javadoc에 SA API ID와 핵심 규칙 한 줄: `/** H-05. 재고 차감. 부족하면 STOCK_INSUFFICIENT (보상 분기에 사용) */`

## 입출력 DTO (`application/dto`)
- 입력 `{동작}{X}Command` (record), 검색 조건 `{X}SearchCondition`
- 출력 `{X}Result` (record) + `static from(Entity)`
- Entity를 application 밖으로 반환하지 않는다. presentation DTO(`Request`/`Response`)를 import하지 않는다

## 흐름
```java
@Transactional
public HubResult update(UserContext user, UUID hubId, UpdateHubCommand command) {
    Hub hub = getHubOrThrow(hubId);                 // 1. 조회 (없으면 BusinessException)
    user.validateSelfOrMaster(hub.getManagerId());  // 2. 권한·소유자 검증
    hub.update(command.name(), command.address());  // 3. 도메인 메서드로 상태 변경 (dirty checking)
    return HubResult.from(hub);                     // 4. Result 변환
}

private Hub getHubOrThrow(UUID hubId) {
    return hubRepository.findById(hubId)
            .orElseThrow(() -> new BusinessException(HubErrorCode.HUB_NOT_FOUND));
}
```
- 상태 변경은 Entity 메서드로. 서비스에서 필드를 하나씩 바꾸지 않는다
- 저장은 신규일 때만 `save`. 수정은 변경 감지
- 삭제: `entity.softDelete(user.username())`

## 권한 · 소유자
- 역할(MASTER/USER)은 컨트롤러 `@RequireRole`, **소유자·담당자 확인은 여기서** `UserContext`로
- 본인 리소스만 조회되는 검색은 조건에 `userId`를 넣는다 (가져온 뒤 거르지 않는다)

## 다른 서비스 호출
- application에 포트 인터페이스(`HubStockPort` 등)를 두고 infrastructure/client에서 Feign으로 구현 (`feign.md`)
- 원격 에러 분기: `catch (BusinessException e) { if (e.hasCode(HubErrorCode.STOCK_INSUFFICIENT)) ... }` — 다른 서비스 enum을 쓸 수 없으면 코드 문자열 상수로 비교
- 여러 서비스를 순서대로 바꾸는 흐름(주문 Saga)은 실패 지점 이후 보상 호출을 명시하고, 보상 실패는 로그 + 상태값으로 남긴다
- 원격 호출이 긴 경우 DB 트랜잭션 밖에서 호출하거나, 트랜잭션 범위를 메서드로 나눈다

## 금지
- `RuntimeException` 등 직접 throw (→ `BusinessException`)
- Repository 구현체·`JPAQueryFactory` 직접 사용 (→ domain Repository 인터페이스)
- `@Value` 남용 — 설정은 `@ConfigurationProperties` (`config.md`)
