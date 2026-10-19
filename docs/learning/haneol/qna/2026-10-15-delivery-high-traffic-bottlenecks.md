---
title: 배송 서비스가 트래픽이 많을 때 먼저 막히는 곳은 어디인가
date: 2026-10-15
updated: 2026-10-15
asker: jihaneol
level: 고급
tags: [performance, feign, timeout, circuit-breaker, cache, index, hikari, postgresql]
---

# 배송 서비스가 트래픽이 많을 때 먼저 막히는 곳은 어디인가

## 질문
동시성은 잡혔고 많은 트래픽을 감당할 부분 분석해줘. (배송 행 비관적 잠금을 넣은 뒤, 같은 배송이 아니라 전체 요청량이 늘 때의 병목을 물었다)

## 짧은 답
같은 배송끼리의 경쟁은 잠금으로 정리됐다. 요청량이 늘 때 먼저 무너지는 곳은 DB 쿼리가 아니라 **배송 생성(I-09) 안의 원격 호출 사슬**이다. 주문 1건마다 hub·user를 최대 4번 차례로 부르고, 호출마다 읽기 타임아웃 20초와 재시도 3회가 걸려 있다. 상대가 느려지면 스레드와 DB 커넥션이 함께 묶인다. 그다음은 자동 배정의 담당 건수 쿼리와 동시 배정 쏠림, 커넥션 수 한계, D-01 목록 인덱스 순서다. 고치기 전에 부하 테스트와 실행계획으로 순서를 확인한다.

## 자세히

### 1. I-09 원격 호출 사슬 (가장 먼저 막힌다)
`DeliveryCreationService.create`는 주문 1건마다 아래를 **차례로** 부른다.

| 호출 | 횟수 | 바뀌는 빈도 |
|---|---|---|
| hub I-03 허브 경로 | 허브가 다르면 1 | 거의 안 바뀜(허브 17개 사이 경로) |
| user I-14 허브별 배송담당자 | 허브마다 1~2 | 가끔(담당자 지정·해제) |
| user I-01 수령인 Slack ID | 1 | 가끔 |

설정은 공통 `read-timeout: 20000`, `IdempotentRetryConfig`(`Retryer.Default(100, 1000, 3)`, 최대 3번 시도)다. user-service가 느려지면 호출 하나가 최악 약 60초, 사슬 전체는 몇 분까지 늘어난다. 그동안 일어나는 일:
- Tomcat 스레드(기본 200개)가 응답을 기다리며 쌓인다. 스레드가 바닥나면 조회 API까지 같이 멈춘다. 느린 의존 하나가 서비스 전체를 멈추는 **연쇄 장애**다.
- order도 I-09를 같은 설정(20초, 3회)으로 부른다. 안쪽(delivery → user) 대기가 바깥(order → delivery) 대기보다 길어서, order가 먼저 포기하고 다시 보내는 동안 delivery는 앞 요청을 계속 처리한다. **타임아웃 예산은 바깥이 안쪽보다 길어야 한다.** 멱등이라 데이터는 안전하지만 일이 두세 배가 된다.

대응(효과 큰 순서):
1. **캐시**: I-03은 결과가 거의 바뀌지 않아 긴 TTL로 캐시한다. I-14는 30~60초 TTL로 캐시한다(Caffeine). 담당자를 해제하고 최대 TTL 동안 배정될 수 있다는 지연을 받아들이는 선택이다. 이렇게 하면 주문당 원격 호출이 4번에서 거의 0~1번이 된다.
2. **타임아웃 줄이기**: 클라이언트별로 connect 1초, read 2~3초처럼 짧게 둔다. 내부 조회는 보통 수십 ms라 20초는 장애를 오래 끄는 역할만 한다.
3. **서킷 브레이커와 벌크헤드**(Resilience4j): 실패율이 높으면 호출을 바로 끊어 503으로 빨리 실패한다. 의존별로 동시 호출 수를 제한해 한 의존이 스레드를 독점하지 못하게 한다.
4. 독립 호출(I-14와 I-01)은 병렬로 부를 수 있지만, 캐시를 먼저 하면 대부분 필요 없다.

### 2. 자동 배정: 담당 건수 쿼리와 동시 배정 쏠림
- `countActiveRoutes`는 `manager_id IN (...) AND status IN ('WAITING','MOVING')`이다. V1의 인덱스는 `manager_id` 단일 인덱스라 담당자의 **과거 도착 구간까지** 훑는다. 도착 구간은 계속 쌓이므로 시간이 갈수록 느려진다. 진행 중 구간만 담는 부분 인덱스 `ON p_delivery_routes (manager_id) WHERE status IN ('WAITING','MOVING') AND deleted_at IS NULL`이 맞는 해법이다. 인덱스 크기가 진행 중 건수만큼으로 유지된다. 결정 로그 13에서 "실행계획 보고 추가"로 미룬 후보다.
- 결정 로그 16은 동시 배정 편차를 "1~2건"으로 보고 잠금을 두지 않았다. 이 가정은 동시 요청이 적을 때만 맞다. 같은 허브에 주문 50건이 동시에 들어오면 50건 모두 같은 건수를 읽고 같은 사람(동점이면 userId 순 첫 번째)을 고른다. 편차가 동시 요청 수만큼 커진다.
  - 허브 단위 advisory lock(`pg_advisory_xact_lock`): 단순하지만 같은 허브의 생성이 한 줄로 서서 처리량 상한이 생긴다.
  - 담당자별 건수 테이블을 두고 `SELECT ... ORDER BY count, user_id LIMIT 1 FOR UPDATE SKIP LOCKED` 후 `count + 1`: 잠긴 후보를 건너뛰어 병렬로 서로 다른 사람에게 배정된다. 구간 도착·취소 때 건수를 줄이는 일이 함께 필요하다.
  - 어느 쪽이든 SA의 "가장 적은 사람" 규칙을 얼마나 엄격히 지킬지에 대한 결정이므로 ADR 대상이다.

### 3. 커넥션: 풀과 DB 한계
- Hikari 기본 풀은 10개, Tomcat 스레드는 200개다. 동시에 DB를 쓰는 요청이 10개를 넘으면 나머지는 커넥션을 기다린다(기본 30초 후 실패).
- 비관적 잠금을 기다리는 요청도 커넥션을 쥔 채 기다린다. 한 트랜잭션이 오래 걸리면 대기가 커넥션 풀로 번진다. 잠금 대기에 상한을 둔다. 예: `ALTER ROLE delivery_app SET lock_timeout = '3s'` 또는 커넥션 초기화 SQL. 상한을 넘으면 실패시켜 스레드를 돌려받는다.
- D-04는 `@Transactional` 안에서 user를 부른다. 행 잠금보다 앞이지만, JPA 트랜잭션은 시작할 때 커넥션을 잡는다(기본 설정). 그래서 원격 응답을 기다리는 동안 커넥션 하나를 쥐고 있다. MASTER 전용이라 양은 적지만, 원칙은 원격 호출을 트랜잭션 밖으로 빼는 것이다(팀 `service.md`).
- 수평 확장(`--scale delivery-service=3`)은 잠금이 DB에 있어 인스턴스가 늘어도 그대로 맞다(JVM 안의 `synchronized`였다면 깨진다). 대신 PostgreSQL `max_connections`(기본 100)를 서비스 5개 × 인스턴스 수 × 풀 크기가 나눠 쓴다. 인스턴스를 늘리면 풀을 줄이거나 PgBouncer 같은 커넥션 풀러를 앞에 둔다.

### 4. 목록 조회 D-01
- 인덱스가 모두 단일 컬럼이고 `deleted_at IS NULL` 조건이 없다. 흔한 조회("내 허브의 배송을 최신순으로")는 `departure_hub_id` 인덱스로 거른 뒤 `created_at`으로 정렬을 따로 한다. 행이 많아지면 `(departure_hub_id, created_at DESC) WHERE deleted_at IS NULL` 같은 복합 부분 인덱스가 정렬까지 대신한다.
- 페이지마다 count 쿼리가 돈다. 뒤쪽 페이지는 OFFSET만큼 읽고 버린다. 배송담당자 화면처럼 "다음 페이지"만 필요하면 커서(키셋) 페이지네이션이 낫다. 다만 응답 계약(`PageResponse`)이 바뀌므로 SA 확인이 필요하다.
- 배송담당자 범위는 EXISTS 서브쿼리라 `p_delivery_routes.manager_id` 인덱스로 처리된다. 지금 구조로 충분하다.
- 어느 인덱스를 둘지는 감으로 정하지 않는다. `generate_series`로 수십만~백만 건을 넣고 `EXPLAIN (ANALYZE, BUFFERS)`로 확인한 뒤 고른다.

### 5. 측정 먼저
1. 지표 노출: 지금 actuator는 `health,info`만 연다. `metrics`(또는 Prometheus)를 열어 `hikaricp.connections.pending`, `http.server.requests` 지연 분포, Feign 호출 시간을 본다.
2. 부하 테스트: k6나 nGrinder로 E2E 경로(주문 생성 → D-05 → D-01)에 동시 사용자를 늘리며 p95 지연과 에러율이 꺾이는 지점을 찾는다. user-service에 인위적 지연을 넣어 1번 연쇄 장애를 재현해 본다.
3. 대량 데이터 실행계획: 2번 부분 인덱스와 4번 복합 인덱스 효과를 전후로 비교한다.

## 이 프로젝트에서는
- 원격 호출 사슬: `delivery-service/.../application/DeliveryCreationService.java`, 타임아웃 `config-repo/application.yml`(`spring.cloud.openfeign`), 재시도 `common/.../feign/IdempotentRetryConfig.java`
- 담당 건수 쿼리: `DeliveryQueryRepositoryImpl.countActiveRoutes`, 인덱스 `V1__create_delivery_tables.sql`
- 동시 배정 쏠림 근거: `ManagerAssigner` 클래스 주석, 결정 로그 16
- 잠금: `DeliveryJpaRepository.findByIdForUpdate`(결정 로그 21)
- 추천 순서: 1(캐시·타임아웃·서킷 브레이커) → 2(부분 인덱스) → 3(lock_timeout, 풀 크기) → 4(측정 후 인덱스). 1의 타임아웃과 Resilience4j는 `config-repo/application.yml`, `common`을 건드리면 팀 리뷰 대상이고, 캐시와 delivery 전용 Feign 설정은 delivery 안에서 할 수 있다.

## 면접 포인트
- "느린 외부 서비스 하나가 우리 서비스 전체를 멈출 수 있나?" → 동기 호출은 응답을 기다리는 동안 스레드와 커넥션을 쥔다. 짧은 타임아웃, 서킷 브레이커, 벌크헤드로 빨리 실패시키고, 잘 안 바뀌는 데이터는 캐시해 호출 자체를 줄인다.
- "타임아웃은 어떻게 정하나?" → 상대의 정상 응답 시간(p99)에 여유를 둔 값으로 정한다. 호출 사슬에서는 바깥 타임아웃이 안쪽 타임아웃과 재시도 합보다 길어야 한다. 그렇지 않으면 바깥이 포기한 뒤에도 안쪽 일이 계속되고 재시도가 일을 불린다.
- "부분 인덱스는 언제 쓰나?" → 조회가 항상 일부 행만 볼 때(진행 중 구간, 삭제 안 된 행). 인덱스가 작아 캐시에 잘 남고 쓰기 비용도 줄어든다.
- "비관적 잠금은 확장성에 불리하지 않나?" → 잠금 범위가 배송 한 건이라 서로 다른 배송끼리는 막지 않는다. 문제는 잠금 대기가 커넥션을 쥔다는 점이라 `lock_timeout`으로 상한을 둔다.

## 참고
- 코드 확인(2026-10-15): `read-timeout: 20000`, `Retryer.Default(100, 1000, 3)`, order `DeliveryCreateClient`도 `IdempotentRetryConfig` 사용, V1 인덱스는 단일 컬럼만, actuator 노출 `health,info`
- 기본값: Spring Boot 3.5 Tomcat `server.tomcat.threads.max` 200, HikariCP `maximumPoolSize` 10·`connectionTimeout` 30초, PostgreSQL 16 `max_connections` 100
- 부하 테스트와 실행계획은 아직 하지 않았다. 위 우선순위는 코드 구조로 판단한 것이다
