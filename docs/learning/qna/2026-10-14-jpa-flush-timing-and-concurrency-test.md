---
title: save()로는 동시 가입이 500이 되고 saveAndFlush()로는 409가 되는 이유, 그리고 그 테스트가 진짜인지 확인하는 법
date: 2026-10-14
updated: 2026-10-14
asker: Junhyun An
level: 고급
tags: [jpa, hibernate, transaction, concurrency, testing, testcontainers]
---

# save()로는 동시 가입이 500이 되고 saveAndFlush()로는 409가 되는 이유, 그리고 그 테스트가 진짜인지 확인하는 법

## 질문
동시 가입 테스트가 실제로 버그를 잡는지 증명했다는 게 무슨 의미인가. 왜 `save()`에서는 500이 되고 mock 테스트로는 잡히지 않았나.

## 짧은 답
- JPA는 `save()`(persist)를 해도 INSERT를 바로 실행하지 않고 모아 두었다가 **flush 시점(보통 커밋 직전)**에 실행한다.
- 커밋은 `@Transactional` 프록시가 메서드가 **끝난 뒤** 한다. 그래서 유니크 제약 위반 예외가 메서드 안의 `try/catch`를 이미 빠져나간 뒤에 터지고, 409로 바꾸지 못해 500이 됐다.
- `saveAndFlush()`는 그 자리에서 INSERT를 실행하므로 예외가 `try` 안에서 나고, 409로 바뀐다.
- mock 테스트에는 영속성 컨텍스트도, 커밋 시점도, DB 유니크 제약도 없다. 그래서 실제 PostgreSQL(Testcontainers)로만 재현된다.
- 고친 코드를 잠깐 되돌려서 테스트가 실패하는지 확인했다. 실패하지 않는 테스트는 버그를 막아 주지 못한다.

## 자세히

### 1. 문제가 된 코드
```java
@Transactional
public SignupResult signup(SignupCommand command) {
    if (userRepository.existsByUsernameIncludingDeleted(command.username())) {   // (1) 중복 확인
        throw new BusinessException(DUPLICATE_USERNAME);
    }
    User user = User.signup(...);
    try {
        return SignupResult.from(userRepository.save(user));                     // (2) 저장
    } catch (DataIntegrityViolationException e) {                               // (3) 유니크 위반 → 409
        throw new BusinessException(DUPLICATE_USERNAME);
    }
}
```
의도는 이렇다. (1)에서 대부분의 중복을 막고, 두 요청이 동시에 (1)을 통과하면 DB 유니크 제약이 최종 방어선이 되고, 그 예외를 (3)에서 409로 바꾼다.

### 2. 실제로 일어난 일 (시간 순서)
```
스레드 A                                   스레드 B
트랜잭션 시작                               트랜잭션 시작
(1) exists? → false                         (1) exists? → false      ← 둘 다 통과
(2) save() → INSERT 예약(아직 실행 안 함)    (2) save() → INSERT 예약
try 블록 종료, 메서드 return                 try 블록 종료, 메서드 return
프록시: flush → INSERT 실행 → 커밋 성공     프록시: flush → INSERT → 유니크 위반!
                                            예외가 try 밖(프록시)에서 발생 → 500
```

### 3. 왜 save()는 INSERT를 미루나: 쓰기 지연
- 영속성 컨텍스트는 변경 사항을 모아 두었다가 flush 때 한꺼번에 SQL로 보낸다. SQL 왕복을 줄이고 배치로 묶기 위해서다.
- flush가 일어나는 때는 세 가지다. 트랜잭션 커밋 직전, 같은 엔티티를 조회하는 JPQL 실행 전(AUTO 모드), `flush()`를 직접 호출할 때.

### 4. id 생성 전략에 따라 결과가 달라진다
| 전략 | INSERT 시점 | 이 버그 |
|---|---|---|
| `IDENTITY` (DB auto increment) | persist 즉시. id를 DB가 만들어 줘야 영속성 컨텍스트에 넣을 수 있다 | 예외가 try 안에서 나서 드러나지 않는다 |
| `UUID`, `SEQUENCE` | flush 때. id를 미리 알 수 있어 INSERT를 미룰 수 있다 | 예외가 커밋 때 나서 500 |

같은 코드가 id 전략만 바꿔도 다르게 동작한다는 점이 이 버그를 찾기 어렵게 만든다. 이 프로젝트는 SA 5.1에 따라 UUID를 쓴다.

### 5. 해결: saveAndFlush
```java
return SignupResult.from(userRepository.saveAndFlush(user));   // INSERT를 지금 실행
```
- flush가 try 안에서 일어나므로 유니크 위반을 잡아 409로 바꾼다.
- PostgreSQL은 제약 위반이 난 트랜잭션을 abort 상태로 만든다. 여기서는 바로 BusinessException을 던져 롤백하므로 문제없다.
- 근본 원칙: 애플리케이션의 중복 확인(1)은 사용자에게 친절한 메시지를 주는 장치이고, 정합성은 DB 유니크 제약이 보장한다. 둘 사이의 틈(경쟁 조건)은 제약 위반 예외를 잡아서 메운다.

### 6. 왜 mock 단위 테스트로는 못 잡았나
```java
given(userRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("uk"));
```
- 이 단위 테스트는 "예외가 나면 409로 바꾼다"는 매핑만 검증한다.
- mock에는 영속성 컨텍스트, flush 시점, 트랜잭션 프록시, DB 제약이 없다. "예외가 어디서 나는가"는 실제 DB와 실제 트랜잭션에서만 드러난다.
- 그래서 Testcontainers로 PostgreSQL 16을 띄운 통합 테스트가 필요했다.

### 7. 동시성 테스트 만드는 법
```java
CountDownLatch start = new CountDownLatch(1);
for (int i = 0; i < 8; i++) {
    futures.add(executor.submit(() -> {
        start.await();                       // 8개 스레드를 출발선에 세운다
        try { return authService.signup(sameUsername); }
        catch (BusinessException e) { return e.getErrorCode(); }
    }));
}
start.countDown();                           // 동시에 출발
// 검증: 성공 정확히 1개, 나머지 7개는 모두 DUPLICATE_USERNAME(다른 예외 0개)
```
- `CountDownLatch`로 스레드들을 동시에 출발시켜 경쟁 확률을 높인다.
- 어떤 스레드는 (1)에서, 어떤 스레드는 (3)에서 막힌다. 실행할 때마다 다르다. 그래서 "몇 번째 스레드가 어디서 막히는가"가 아니라 "성공은 1개, 나머지는 전부 409, 그 외 예외는 0개"라는 불변식을 검증한다.

### 8. 테스트가 진짜인지 확인: 고친 코드를 일부러 되돌리기
- 테스트가 통과한다고 해서 버그를 잡는 테스트라는 보장은 없다. 경쟁이 한 번도 일어나지 않아서 통과했을 수도 있다.
- 그래서 `saveAndFlush`를 잠깐 `save()`로 되돌리고 같은 테스트를 3번 돌렸다. 3번 모두 실패했다.
  ```
  ExecutionException: DataIntegrityViolationException: could not execute statement
  [ERROR: duplicate key value violates unique constraint "p_users_username_key"]
  ```
- 이렇게 "코드를 일부러 망가뜨렸을 때 테스트가 실패하는지" 보는 방식을 **뮤테이션 테스트**라고 한다. 자동화 도구로 PIT(pitest)가 있다.
- 확인 후 코드를 원래대로 돌렸고, `git diff`로 변경이 남지 않았음을 확인했다.

## 이 프로젝트에서는
- 수정: `user-service/.../application/AuthService.java`(saveAndFlush), `domain/UserRepository.java`(saveAndFlush 선언)
- 테스트: `user-service/.../integration/UserAuthIntegrationTest.concurrentSignup`
- 기반: `common/src/testFixtures/.../PostgresIntegrationTest`(PostgreSQL 16 컨테이너 공유)
- 스텝 기록: [10-3](../steps/step-10-3-user-service.md), [10-6](../steps/step-10-6-integration-tests.md)

## 면접 포인트
- "JPA 쓰기 지연이란? flush는 언제 일어나나?"
- "중복 가입을 어떻게 막나?": 애플리케이션 확인 + DB 유니크 제약 + 제약 위반 예외 처리. 확인만으로는 경쟁 조건을 막을 수 없다.
- "id 생성 전략이 INSERT 시점에 주는 영향": IDENTITY는 즉시 INSERT라 배치 INSERT도 어렵다.
- "테스트가 버그를 잡는지 어떻게 확신하나": 일부러 버그를 넣어 실패하는지 확인(뮤테이션 테스트).

## 참고
- Hibernate ORM 문서, Flushing: https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#flushing
- Testcontainers: https://java.testcontainers.org/
- PIT 뮤테이션 테스트: https://pitest.org/
