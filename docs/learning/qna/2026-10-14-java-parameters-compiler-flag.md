---
title: -parameters 컴파일 옵션이 없으면 왜 Spring이 @RequestParam 이름을 못 읽나
date: 2026-10-14
updated: 2026-10-14
asker: Junhyun An
level: 중급
tags: [java, spring, gradle, reflection]
---

# -parameters 컴파일 옵션이 없으면 왜 Spring이 @RequestParam 이름을 못 읽나

## 질문
common 모듈에 `-parameters` 컴파일 옵션이 빠져 있었다는 게 무슨 뜻이고, 왜 문제가 되는가.

## 짧은 답
- Java 컴파일러는 기본적으로 메서드 파라미터 **이름**을 바이트코드에 남기지 않는다. 리플렉션으로 보면 `quantity`가 아니라 `arg0`이다.
- `javac -parameters`로 컴파일하면 이름이 바이트코드에 남고, Spring이 `@RequestParam int quantity`에서 `quantity`라는 이름을 읽을 수 있다.
- Spring Boot Gradle 플러그인은 이 옵션을 자동으로 붙이지만, Boot 플러그인이 없는 common(라이브러리 모듈)에는 빠져 있었다. 루트 `build.gradle`에서 모든 모듈에 명시해 해결했다.

## 자세히

### 바이트코드에 남는 것과 사라지는 것
```java
@GetMapping("/sample/quantity")
int quantity(@RequestParam int quantity) { ... }
```
- 타입(`int`), 어노테이션(`@RequestParam`)은 바이트코드에 남는다.
- 파라미터 이름(`quantity`)은 기본적으로 사라진다. `Method.getParameters()[0].getName()`이 `arg0`을 돌려준다.
- `-parameters`로 컴파일하면 클래스 파일에 `MethodParameters` 속성이 생기고, 리플렉션이 실제 이름을 돌려준다.

### Spring은 왜 이름이 필요한가
`@RequestParam`에 이름을 직접 쓰지 않으면, Spring은 파라미터 이름을 요청 파라미터 이름으로 쓴다. 이름을 모르면 어떤 쿼리 파라미터를 꺼내야 할지 알 수 없다. 이름이 필요한 곳은 생각보다 많다.
- `@RequestParam`, `@PathVariable`, `@RequestHeader`에 이름을 생략했을 때
- 메서드 파라미터 검증 실패 응답에서 필드명을 만들 때(`HandlerMethodValidationException`)
- Spring Data의 `@Query`에서 `:username`처럼 이름으로 바인딩할 때(`@Param` 생략 시)
- `@Cacheable(key = "#userId")` 같은 SpEL에서 파라미터를 이름으로 참조할 때

### 예전에는 왜 없어도 됐나
- Spring 6.0까지는 `-parameters`가 없으면 디버그 정보(LocalVariableTable)에서 이름을 읽는 우회로가 있었다.
- Spring 6.1(Boot 3.2)에서 이 우회로가 제거됐다. 그래서 지금은 `-parameters`가 사실상 필수다.
- 이름을 못 찾으면 이런 예외가 난다.
  ```
  IllegalArgumentException: Name for argument of type [int] not specified, and parameter name
  information not available via reflection. Ensure that the compiler uses the '-parameters' flag.
  ```

### 이번에 어떻게 드러났나
- common에 "파라미터 검증 실패는 400 VALIDATION_ERROR"라는 테스트를 추가했는데, 400이 아니라 500이 나왔다.
- 원인은 검증 이전 단계였다. 이름을 못 읽어 위 예외가 났고, 공통 예외 처리기의 마지막 단계가 그것을 500으로 바꿨다.
- 서비스 모듈은 Boot 플러그인이 옵션을 붙여 줘서 문제가 없었다. common에는 지금 컨트롤러가 없어서 그동안 드러나지 않았다. 팀원이 common에 공통 컨트롤러나 `@Cacheable` SpEL을 넣었다면 그때 터졌을 문제다.

### 해결
```groovy
// 루트 build.gradle, subprojects 블록
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs << '-parameters'
}
```
- 다른 방법은 이름을 항상 명시하는 것이다(`@RequestParam("quantity")`). 하지만 사람이 빠뜨리기 쉬워서, 빌드 설정으로 모든 모듈에 일관되게 거는 편이 안전하다.
- IntelliJ가 Gradle 대신 자체 컴파일러로 빌드하도록 바꿔 두었다면, Settings → Build → Compiler → Java Compiler에도 `-parameters`를 넣어야 한다. 기본값(Gradle에 위임)이면 위 설정이 그대로 적용된다.

## 이 프로젝트에서는
- 루트 `build.gradle`의 `subprojects`에 `-parameters`를 명시했다(스텝 10-1).
- 회귀 테스트: `common/.../GlobalExceptionHandlerTest.methodParameterValidation`

## 면접 포인트
- "Spring은 컨트롤러 파라미터 이름을 어떻게 알아내나": 리플렉션 + `-parameters`로 남긴 `MethodParameters` 속성. Spring 6.1부터 디버그 정보 우회가 없어졌다.
- 빌드 설정을 모듈마다 두지 않고 루트에서 일관되게 거는 이유: 플러그인 구성이 다른 모듈(라이브러리 모듈)에서 설정이 조용히 빠지는 일을 막는다.

## 참고
- https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html (`-parameters`)
- https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x (6.1 parameter name retention)
- 스텝 기록: [10-1](../steps/step-10-1-common-errors-input.md)
