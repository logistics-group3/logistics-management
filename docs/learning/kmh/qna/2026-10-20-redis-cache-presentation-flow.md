---
title: Redis 캐시 A·B·C 개발 내용을 핵심 코드로 설명하는 발표 흐름
date: 2026-10-20
updated: 2026-10-20
asker: minhyeok.kwon
level: 중급
tags: [redis, cache, caffeine, spring-cache, transaction, gateway, performance-test, presentation]
---

# Redis 캐시 A·B·C 개발 내용을 핵심 코드로 설명하는 발표 흐름

## 질문
개발한 캐시(A2, B1, C1)의 핵심 코드를 짚어 발표용 흐름으로 정리해 달라.

## 짧은 답
- 이 작업은 "캐시를 넣었다"가 아니라 "캐시를 설정으로 켜고 끌 수 있게 만들고, 같은 조건에서 측정해 채택 여부를 정한다"이다.
- 세 항목에 같은 원칙을 쓴다. 설정으로 저장소 선택(기본값은 기존 동작), 데이터를 가진 서비스에 캐시, Redis 장애는 성능 저하로만 끝남, 값 타입을 고정한 JSON 직렬화.
- 핵심 기술 포인트는 "캐시 삭제를 커밋 뒤로 미루는 것"이다. A는 `@TransactionalEventListener(AFTER_COMMIT)`, C는 `TransactionAwareCacheManagerProxy`로 같은 문제를 푼다.
- 측정은 기준선 → 하나씩 변경 → 예열 후 3회 → 편차보다 큰 차이만 개선으로 인정하는 순서로 한다.

발표 순서: 문제 → 설계 원칙 → A·B·C 핵심 코드 → 측정 방법 → 지금 결과와 남은 일

## 자세히

### 1. 설계 원칙 (ADR 0019)
| 원칙 | 코드에서 보이는 곳 |
|---|---|
| 설정 하나로 저장소를 바꾸고, 기본값은 지금 동작과 같다 | `AuthProperties.userCache`, `HubCacheProperties.type`, `CompanyCacheProperties.type` (`none`/`local`/`redis`) |
| 캐시는 데이터를 가진 서비스에 둔다 | A: Gateway가 저장하고 user-service가 삭제, B: hub-service, C: company-service |
| Redis 장애가 서비스 장애로 번지지 않는다 | 조회 실패 시 원래 호출로 넘어가고, 삭제 실패는 TTL이 안전망 |
| JSON에 클래스 이름을 넣지 않는다 | `Jackson2JsonRedisSerializer<>(objectMapper, 타입)`으로 값 타입 고정 |

같은 이미지에서 환경 변수만 바꿔 조건을 비교해야 측정이 공정하다. 기본값이 기존 동작이라 팀 실행 환경에는 영향이 없다.

### 2. A: Gateway 권한 재확인 캐시 (커밋 `f3397e6`)

**문제**: Gateway는 요청마다 user-service I-01로 role을 재확인한다. 기존 캐시는 Caffeine 로컬 60초다.
- 권한을 바꿔도 최대 60초 동안 옛 권한이 통한다(기준선 A1 측정값 60,180~60,281ms).
- Gateway 인스턴스마다 캐시가 따로 있다.

**흐름**
```
[조회] 요청 → UserVerifyingJwtConverter → UserLookup.find(userId)
                                           ├ local: CachedUserLookup (Caffeine, 기존)
                                           └ redis: RedisUserLookup
                                               GET auth:user:{id} → 없으면 I-01 → SET (TTL 60s)

[변경] U-07 권한 변경 / U-08 탈퇴 → UserService가 UserAuthChangedEvent 발행
        → 커밋 뒤 UserAuthChangedEventListener → RedisUserAuthCache.evict → DEL auth:user:{id}
```

**핵심 코드 1: 전략 교체 지점** (`gateway-service/.../security/AuthConfig.java`)
```java
if (properties.userCache() == AuthProperties.UserCacheType.REDIS) {
    return new RedisUserLookup(userSnapshotRedis(...), client::fetch, properties.userCacheTtl());
}
return new CachedUserLookup(client::fetch, properties.userCacheTtl());
```
`UserLookup` 인터페이스 뒤에서 구현만 바꾼다. 호출하는 JWT 변환기는 고치지 않았다.

**핵심 코드 2: 장애 격리** (`gateway-service/.../security/RedisUserLookup.java`)
```java
return redis.opsForValue().get(key)
        .map(Optional::of)
        .onErrorResume(e -> Mono.empty())                      // Redis 장애는 캐시 미스로 취급
        .switchIfEmpty(Mono.defer(() -> loader.apply(userId)   // I-01 직접 호출
                .flatMap(found -> store(key, found).thenReturn(found))));
```
Redis는 성능 계층이다. Redis가 죽어도 인증은 느려질 뿐 실패하지 않는다. 탈퇴한 사용자(빈 값)는 저장하지 않는다.

**핵심 코드 3: 커밋 뒤 삭제** (`user-service/.../application/event/UserAuthChangedEventListener.java`)
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onUserAuthChanged(UserAuthChangedEvent event) {
    userAuthCache.evict(event.userId());
}
```
커밋 전에 지우면, 지운 직후·커밋 전에 Gateway가 아직 바뀌지 않은 DB 값을 읽어 캐시에 다시 넣을 수 있다. 그러면 옛 권한이 TTL 동안 남는다. 롤백되면 이벤트가 실행되지 않아 지우지도 않는다. 삭제가 실패해도 이미 커밋한 변경은 되돌리지 않고 TTL로 반영된다(`RedisUserAuthCache`).

**핵심 코드 4: 키 규칙을 한 곳에** (`common-core/.../auth/UserAuthCacheKeys.java`, `"auth:user:" + userId`)
저장하는 서비스(gateway)와 지우는 서비스(user)가 다르다. 키 형식이 어긋나면 삭제가 오류 없이 조용히 실패하므로 공통 모듈에 둔다.

**기대 효과와 대가**: 권한 반영 60초 → 거의 즉시, 인스턴스 간 공유. 대가는 요청마다 Redis 왕복 1회이며 측정으로 확인한다.

### 3. B: 허브·경로 단건 조회 캐시 (커밋 `3a5af81`)

**대상**: H-02(허브 단건), I-02, H-04(경로 단건), I-03(허브 간 경로, 배송 생성 때 delivery가 호출)

**핵심 코드 1: 조회 메서드에 어노테이션만 붙인다** (`HubRouteQueryService`)
```java
@Cacheable(cacheNames = HubCacheNames.INTERNAL_HUB_ROUTE, key = "#departureHubId + ':' + #arrivalHubId")
public InternalHubRouteResult getInternalHubRoute(UUID departureHubId, UUID arrivalHubId) { ... }
```
출발·도착 복합 키를 쓴다. 404(예외)는 캐시하지 않는다. 목록 검색은 조건·페이지 조합이 많아 캐시하지 않는다.

**핵심 코드 2: CacheManager 하나로 저장소 선택** (`hub-service/.../infrastructure/config/HubCacheConfig.java`)
```java
return switch (properties.type()) {
    case NONE  -> new NoOpCacheManager();               // 어노테이션은 있어도 아무것도 하지 않음
    case LOCAL -> CaffeineCacheManager (TTL, 최대 1만 건)
    case REDIS -> RedisCacheManager (캐시마다 값 타입 고정 직렬화기, "hub:" 접두어,
                                     disableCreateOnMissingCache)
};
```
- `CacheManager`를 직접 빈으로 만들면 Spring Boot 캐시 자동 설정이 물러난다. 클래스패스에 Redis가 있다는 이유만으로 Redis 캐시가 켜지지 않는다.
- 허브·경로는 쓰기 API가 없다(Flyway 시드). 그래서 삭제 없이 TTL(10분)로만 갱신된다.

### 4. C: 업체·상품 내부 조회 캐시 (커밋 `a58e3f1`)

**대상**: I-07(업체), I-08(상품). 주문 1건마다 order → company 호출이 3회(공급업체, 수령업체, 상품)다.

**핵심 코드 1: 조회 캐시와 수정·삭제 시 키 삭제** (`ProductQueryService`, `ProductService`, `CompanyService`)
```java
@Cacheable(CompanyCacheNames.INTERNAL_PRODUCT)
public InternalProductResult getInternalProduct(UUID productId)

@CacheEvict(cacheNames = INTERNAL_PRODUCT, key = "#productId")
@Transactional
public ProductResult update(...)

// 업체 삭제는 소속 상품도 지우는데 상품 키는 상품 ID라 업체 ID로 고를 수 없다. 업체 삭제는 드물어 상품 캐시를 모두 비운다
@Caching(evict = {
    @CacheEvict(cacheNames = INTERNAL_COMPANY, key = "#companyId"),
    @CacheEvict(cacheNames = INTERNAL_PRODUCT, allEntries = true) })
```

**핵심 코드 2: 트랜잭션 인식 프록시** (`company-service/.../infrastructure/config/CompanyCacheConfig.java`)
```java
return new TransactionAwareCacheManagerProxy(manager);
```
- A의 `AFTER_COMMIT`과 같은 문제를 다른 방법으로 푼다. 트랜잭션 안에서 키를 지우면 커밋 전에 다른 요청이 옛 값을 다시 넣을 수 있다. 프록시는 트랜잭션이 있으면 저장·삭제를 커밋 뒤로 미룬다.
- 함정: 빈으로 등록되는 것은 바깥 프록시라 Spring이 안쪽 `RedisCacheManager`의 초기화를 부르지 않는다. 그래서 `redisManager.afterPropertiesSet()`을 직접 부른다.

**위치 선택**: order에 두면 HTTP 호출 수까지 줄지만, company의 수정·삭제를 order에 알릴 장치가 따로 필요하다. 수정 범위가 가장 작은 company를 골랐다(kmh 결정). 그래서 C의 효과는 호출 수가 아니라 company 내부 API 지연으로 판정한다.

### 5. 측정 설계
| 설계 | 이유 |
|---|---|
| 기준선(A0, A1, BC0)을 먼저 재고 한 번에 하나만 바꾼다 | 개선이 어디서 왔는지 분리한다 |
| 고정 도착률(`constant-arrival-rate`) | 서버가 느려지면 부하도 줄어드는 닫힌 모델의 착시(coordinated omission)를 피한다 |
| 조건마다 새로 띄우고 3분 예열(버림) 후 5분×3회 | 예열 없이 잰 첫 회차 p95가 2,340ms까지 튀었다(JIT, 커넥션 풀 준비) |
| k6(사용자 체감)와 Prometheus(서비스·구간별)를 같은 구간으로 합친다 | 느려졌는지와 어디서 느려졌는지를 함께 본다 |
| 개선 폭이 3회 편차보다 작으면 "차이 없음" | 잡음을 개선으로 착각하지 않는다 |

**지금까지의 결과(기준선)**
- A1: 권한 반영 약 60초, S1 p95 4.8~5.3ms
- BC0: S2 p95 14.9~18.4ms, 요청당 company 호출 3.00, S3 p95 4.0~4.1ms
- S2(주문 생성)의 한계는 캐시 대상이 아니라 order-service DB 커넥션 풀이다(120/s에서 대기 최대 186). 캐시를 넣어도 최대 처리량은 크게 오르지 않을 수 있다는 점을 측정으로 먼저 확인했다.

### 6. 남은 일
측정(A0, A2, B1-local, B1-redis, C1) → ADR 0019에 항목별 채택·기각 → 채택한 항목만 팀 Compose·`.env.example`에 추가하고 B·C 캐시 설정을 `common` 자동 설정으로 옮긴다.

## 이 프로젝트에서는
- 커밋: `f3397e6`(A2), `3a5af81`(B1), `a58e3f1`(C1). 브랜치 `feat/infra-perf-baseline`
- 켜는 설정: `AUTH_USER_CACHE=redis`(gateway·user 같은 값), `HUB_CACHE_TYPE=local|redis`, `COMPANY_CACHE_TYPE=local|redis`. 설정 파일은 `config-repo/{gateway,user,hub,company}-service.yml`
- 설계 결정: `docs/decisions/0019-redis-cache-measured-adoption.md`
- 측정 기록과 절차: `docs/reviews/performance/README.md`, `docs/guides/performance-test.md`
- 협업 로그: `docs/services/{gateway,user,common,hub,company}/README.md`의 GATEWAY-001, USER-003, COMMON-001, HUB-002, COMPANY-003

## 면접 포인트
- 왜 로컬 캐시로 충분하지 않나: 인스턴스 간 공유가 안 되고, 다른 서비스(user)가 Gateway 메모리의 캐시를 지울 수 없다.
- Redis가 죽으면: A는 I-01을 직접 호출한다. B·C는 캐시를 끄면 원래 동작이고, 삭제 실패는 TTL이 안전망이다.
- 캐시와 DB 정합성: 삭제를 커밋 뒤로 미룬다(A는 `AFTER_COMMIT`, C는 `TransactionAwareCacheManagerProxy`). 그 위에 TTL을 둔다.
- 성능 개선을 어떻게 증명했나: 기준선, 하나씩 변경, 예열 후 3회 중앙값과 편차, 고정 도착률 부하.
- 효과가 없으면: 기각하고 근거를 ADR에 남긴다. 넣지 않는 결정도 결과다.

## 참고
- Spring Framework 문서: Cache Abstraction(`@Cacheable`, `@CacheEvict`, `TransactionAwareCacheManagerProxy`), Transaction-bound Events(`@TransactionalEventListener`)
- Spring Data Redis 문서: `RedisCacheManager`, `ReactiveRedisTemplate`
- 관련 노트: [이 프로젝트에서 Redis 캐시를 적용할 만한 구간](2026-10-19-redis-cache-candidates.md), [캐시 도입 전 성능 기준선 측정 계획](2026-10-19-performance-baseline-plan.md), [k6와 Prometheus는 어떻게 동작하고 성능 측정에서 어떻게 함께 쓰나](2026-10-20-k6-prometheus-how-it-works.md)
