---
title: common과 common-core를 나눈 이유와 설계상 이점
date: 2026-10-15
updated: 2026-10-15
asker: choiseungkeun
level: 중급
tags: [gradle, multi-module, webflux, spring-mvc, msa]
---

# common과 common-core를 나눈 이유와 설계상 이점

## 질문
프로젝트에 common과 common-core가 분리되어 있다. 이렇게 나누는 것이 설계상 이점이 있는지, 아니면 다른 이유가 있는지.

## 짧은 답
- 이유는 웹 스택 차이다. 서비스 5개는 Spring MVC, gateway는 WebFlux라서 MVC에 묶인 common을 gateway가 쓸 수 없다.
- 그래서 스택과 무관한 계약(에러 코드, 에러 응답, 사용자 헤더 이름, 역할)만 common-core로 뺐고, gateway는 common-core만 의존한다.
- 이점은 "한 곳에서 정의한 계약을 모든 모듈이 공유"하는 것이다. 이전에는 gateway에 같은 내용을 따로 정의해 어긋날 위험이 있었다.
- 일반적인 설계 이점(의존 범위 최소화, 변경 영향 축소)도 있지만 이 프로젝트의 직접 동기는 gateway 때문이다.

## 자세히
- 스택이 다른 두 모듈은 같은 클래스패스에 `spring-boot-starter-web`(서블릿)과 WebFlux가 섞이면 안 된다. Boot는 둘이 함께 있으면 MVC로 기동하는 등 gateway 동작이 어긋난다.
- 해결책은 공통 코드를 "스택에 의존하는 부분"과 "의존하지 않는 부분"으로 나누는 것이다. 의존 방향은 `서비스 → common → common-core`, `gateway → common-core`다.
- common-core의 의존성은 `spring-web`(HttpStatus 등, MVC와 WebFlux가 함께 쓰는 기반)과 `jackson-annotations`뿐이다.
- 나누지 않은 대안 비교
  - 중복 정의 후 테스트로 감시: 어긋남은 잡지만 중복 자체는 남는다.
  - common-core/common-web으로 이름까지 변경: 목적은 같지만 서비스 5개의 의존성 선언과 문서를 모두 바꿔야 해서 채택하지 않았다.

### 동기/비동기 때문인가 (추가 질문)
- 근본 원인은 실행 모델 차이다. MVC는 서블릿 위에서 요청당 스레드 하나를 쓰는 블로킹 방식이고, WebFlux는 Netty 이벤트 루프 위의 논블로킹 방식이다.
- common의 인터셉터, ArgumentResolver, 예외 핸들러, Feign, JPA는 서블릿 API나 블로킹을 전제로 해서 gateway에서 쓸 수 없다.
- 다만 "동기/비동기"는 정확한 기준이 아니다. MVC도 `@Async`, `DeferredResult`로 비동기를 쓸 수 있다. 분리 기준은 "스택에 의존하는 코드인가"이고, 블로킹 여부와 서블릿 타입이 없는 순수 계약만 common-core에 둔다.
- WebFlux에서 블로킹 호출을 하면 이벤트 루프 스레드가 막혀 다른 요청까지 지연된다.

### 한 모듈로 합치지 못하는 이유가 있나 (추가 질문)
- 합칠 수 없는 것은 아니다. 현재 빌드 구성에서는 합치면 gateway가 쓸 수 없다는 뜻이다.
- `common/build.gradle`이 `starter-web`, `data-jpa`, `openfeign`을 `implementation`으로 선언한다. `implementation`은 runtime classpath로 전이되므로 gateway가 합친 모듈에 의존하면 `starter-web`이 들어와 Boot가 MVC로 기동하고, JPA 자동 설정은 DB 설정 없음으로 실패한다.
- 합치는 방법은 Spring Boot 자체가 쓰는 방식이다. 웹, JPA, Feign 의존성을 `compileOnly`로 바꾸고 `@ConditionalOnClass`, `@ConditionalOnWebApplication`으로 조건부 등록한다(ADR 0009가 이미 비슷한 조건부 자동 설정을 쓴다).
- 비용: `compileOnly`는 전이되지 않아 서비스 5개가 의존성을 각자 선언해야 한다. 컴파일 시점의 격리 보증이 사라져 실수가 런타임 `NoClassDefFoundError`로 드러난다. test fixtures와 QueryDSL 어노테이션 프로세서도 따로 정리해야 한다.
- 모듈 하나를 더 두는 비용은 작고 실수를 컴파일 시점에 막으므로 분리를 유지한다. 구조를 바꾸려면 팀 합의와 새 ADR이 필요하다.
- Gradle 의존성 구성: `api`는 컴파일과 런타임 모두 소비자에게 노출, `implementation`은 런타임에만 전이, `compileOnly`는 전이 없음.

### 현업에서도 많이 쓰는 방식인가 (추가 질문)
- 흔한 패턴이다. 통계로 확인한 것은 아니고 일반적으로 알려진 관행에 근거한다.
- Spring 자체가 `spring-web`(공통 기반), `spring-webmvc`, `spring-webflux`로 나뉜다. common-core가 의존하는 spring-web이 그 공통 기반이다. 조건부 자동 설정도 Boot starter의 표준 방식이다.
- Spring Cloud Gateway가 WebFlux 기반이라 "MVC 서비스와 WebFlux gateway의 계약 공유" 문제는 실무에서 실제로 만난다.
- 다른 점: 서비스가 많거나 팀이 나뉘면 서비스별 저장소로 나누고, 공통 라이브러리는 사내 Nexus/Artifactory에 버전을 붙여 배포한다. 서비스는 업그레이드 시점을 각자 고른다. 모노레포는 공통 변경이 모든 서비스에 동시에 반영된다.
- MSA에서 공통 라이브러리를 크게 두면 서비스끼리 결합된다는 비판이 있어, 현업도 횡단 관심사와 계약(타입)만 두고 비즈니스 로직은 넣지 않는 것을 원칙으로 삼는 곳이 많다.
- 규모가 더 커지면 공유 클래스 대신 OpenAPI, protobuf 같은 스키마로 계약을 정의해 언어와 프레임워크에 독립적으로 만들기도 한다.

## 이 프로젝트에서는
- `common-core`: `ErrorCode`, `CommonErrorCode`, `BusinessException`, `ErrorResponse`, `ApiResponse`, `UserHeaders`, `Role`
- `common`: MVC 의존 코드. `GlobalExceptionHandler`, 인증 인터셉터와 ArgumentResolver, Feign 설정, JPA `BaseEntity`, QueryDSL 지원, 자동 설정, 통합 테스트 기반
- `common/build.gradle`의 `api project(':common-core')` 덕분에 서비스는 common만 선언해도 common-core를 쓰고, 패키지명이 같아 import가 바뀌지 않는다.
- `ErrorResponseContractTest`(common-core)가 서비스와 gateway의 응답 형식을 함께 고정한다.
- 규칙: common-core에 MVC나 WebFlux 의존을 넣지 않는다.
- 결정 기록: `docs/decisions/0006-common-core-module.md`

## 면접 포인트
- Q. 공통 모듈을 왜 둘로 나눴나? A. 웹 스택이 다른 소비자(MVC 서비스, WebFlux gateway)가 같은 계약을 공유해야 해서, 스택 무관 부분만 분리했다.
- Q. 공통 모듈의 위험은? A. 공유 라이브러리가 커지면 모든 서비스가 같이 배포·변경되는 결합이 생긴다. 그래서 계약(타입)만 두고 비즈니스 로직은 넣지 않는 기준이 필요하다.
- Q. `api`와 `implementation`의 차이는? A. `api`는 의존 모듈의 클래스패스까지 소비자에게 노출하고, `implementation`은 내부에만 쓴다.

## 참고
- `common/build.gradle`, `common-core/build.gradle`, `gateway-service/build.gradle` 확인
- `docs/decisions/0006-common-core-module.md`
