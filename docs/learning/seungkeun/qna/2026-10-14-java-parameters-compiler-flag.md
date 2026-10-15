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
(추가 질문) "메서드 파라미터 이름을 바이트코드에 남기지 않는다"는 게 정확히 무슨 뜻인가. 다른 언어나 프레임워크와 비교하면 어떤가.

## 짧은 답
- Java 컴파일러는 기본적으로 메서드 파라미터 **이름**을 바이트코드에 남기지 않는다. 리플렉션으로 보면 `quantity`가 아니라 `arg0`이다.
- `javac -parameters`로 컴파일하면 이름이 바이트코드에 남고, Spring이 `@RequestParam int quantity`에서 `quantity`라는 이름을 읽을 수 있다.
- Spring Boot Gradle 플러그인은 이 옵션을 자동으로 붙이지만, Boot 플러그인이 없는 common (라이브러리 모듈)에는 빠져 있었다. 루트 `../../../../build.gradle`에서 모든 모듈에 명시해 해결했다.

## 쉽게 이해하기

### 컴파일하면 이름표가 떨어진다
- 우리가 쓰는 코드는 사람이 읽는 글이다. `order(int quantity, String productName)`
- 컴파일하면 JVM이 실행하는 명령(바이트코드)으로 바뀐다. JVM은 파라미터를 이름이 아니라 **자리 번호**로 다룬다. "1번 자리 값을 꺼내라", "2번 자리 값을 꺼내라".
- 실행에는 번호만 있으면 충분하다. 그래서 Java 컴파일러는 기본적으로 `quantity`, `productName`이라는 이름표를 떼어 버린다.
- 비유하면 식당 주문서와 같다. 주방은 "3번 테이블 김치찌개"만 알면 음식을 만든다. 손님 이름은 주문서에 적지 않는다.

### 그런데 Spring은 이름이 필요하다
- Spring이 하는 일은 실행이 아니라 **연결**이다. 요청 URL `?quantity=3`의 `quantity`와 메서드의 어느 파라미터가 짝인지 찾아야 한다.
- 주방(JVM)은 번호만 알면 되지만, 홀 직원(Spring)은 "quantity 손님이 몇 번 자리인지" 알아야 음식을 가져다줄 수 있다.
- 이름표가 떨어져 있으면 Spring은 `arg0`, `arg1`만 보게 되고, `quantity`가 어느 것인지 알 수 없다.
- `-parameters`는 "이름표를 떼지 말고 class 파일에 같이 넣어 달라"는 옵션이다.

## 직접 확인한 실험 (JDK 17)
```java
public int order(int quantity, String productName) { return quantity; }
// 리플렉션으로 파라미터 이름을 출력
```
| 컴파일 | 리플렉션이 본 이름 | class 파일 크기 |
|---|---|---|
| `javac` (옵션 없음) | `arg0`, `arg1` (`isNamePresent() = false`) | 1,660 bytes |
| `javac -parameters` | `quantity`, `productName` (`isNamePresent() = true`) | 1,737 bytes |

- `javap -v`로 보면 `-parameters` 쪽에만 `MethodParameters` 속성과 `quantity`, `productName` 문자열이 들어 있다. 늘어난 크기 77바이트가 이 이름표다.
- `javap -c`로 `order`의 바이트코드를 보면 `iload_1`, `ireturn` 두 줄이다. "1번 자리 정수를 꺼내 돌려준다"는 뜻이고, 이름은 어디에도 없다. JVM은 이름 없이도 잘 실행한다.

## 다른 언어·프레임워크와 비교
| 언어 | 실행 중에 파라미터 이름을 알 수 있나 | 웹 프레임워크가 요청 값을 연결하는 방식 |
|---|---|---|
| Java (기본) | 모른다(`arg0`) | Spring은 `-parameters`가 필요하다. 없으면 `@RequestParam("quantity")`처럼 이름을 문자열로 직접 적는다 |
| Java + `-parameters` | 안다 | Spring Boot Gradle·Maven 플러그인이 자동으로 켠다. 이번 common처럼 플러그인이 없는 모듈에서만 빠진다 |
| Kotlin (JVM) | Java 리플렉션 기준으로는 기본적으로 모른다 | `-java-parameters` 옵션이 같은 역할을 한다. Spring Boot 플러그인이 Kotlin 컴파일에도 자동으로 켠다 |
| C# (.NET) | 항상 안다. 파라미터 이름이 어셈블리 메타데이터에 늘 들어간다 | ASP.NET Core는 설정 없이 `int quantity`를 `?quantity=`와 연결한다 |
| Python | 항상 안다. 함수 객체가 이름을 들고 있다(`inspect.signature`) | FastAPI는 `def order(quantity: int)`만 써도 `?quantity=`를 연결한다. 이름과 타입 힌트를 그대로 읽기 때문이다 |
| JavaScript | 소스에는 있지만 압축(minify)하면 `a`, `b`로 바뀐다 | AngularJS 1.x는 파라미터 이름으로 의존성을 주입하다가 압축 후 깨지는 문제로 유명했다. 그래서 `['$http', function ($http) {}]`처럼 이름을 문자열로 따로 적었다. Express는 처음부터 `req.query.quantity`로 문자열을 직접 쓴다 |
| Go | 리플렉션으로 함수 파라미터 이름을 얻을 수 없다 | Gin은 `c.Query("quantity")`나 구조체 태그 `form:"quantity"`처럼 이름을 문자열로 적는다 |

정리하면 두 갈래다.
- **이름을 실행 환경에 남기는 쪽**(C#, Python): 프레임워크가 이름만 보고 자동으로 연결해 준다. 편하다.
- **이름을 버리거나 바뀔 수 있는 쪽**(Java 기본, Go, 압축된 JS): 이름을 문자열로 따로 적거나, 컴파일 옵션으로 이름을 남겨야 한다.
- Java는 그 중간이다. 기본값은 버리지만, Java 8부터 `-parameters`로 남길 수 있다. Spring은 이 옵션을 켠 상태를 전제로 동작한다.

### Java는 왜 기본값이 "버린다"인가
- JVM은 실행에 이름이 필요 없다(실험의 `iload_1`). class 파일 형식은 Java 1.0 때부터 그렇게 설계됐다.
- Java 8(JEP 118)에서 이름을 남기는 기능을 **선택 옵션**으로 추가했다. 기존 class 파일과의 호환성, class 파일 크기 때문이다.
- 참고로 디버그 정보(`-g`)에도 지역 변수 이름이 들어가서, Spring 6.0까지는 그걸 읽어 썼다. 하지만 인터페이스나 추상 메서드에는 본문이 없어 지역 변수 정보도 없다. 그래서 예전에는 Spring Data 리포지토리 인터페이스의 쿼리 메서드에 `@Param("username")`을 꼭 붙여야 했다.

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
- 루트 `../../../../build.gradle`의 `subprojects`에 `-parameters`를 명시했다 (스텝 10-1).
- 회귀 테스트: `common/.../GlobalExceptionHandlerTest.methodParameterValidation`

## 면접 포인트
- "Spring은 컨트롤러 파라미터 이름을 어떻게 알아내나": 리플렉션 + `-parameters`로 남긴 `MethodParameters` 속성. Spring 6.1부터 디버그 정보 우회가 없어졌다.
- 빌드 설정을 모듈마다 두지 않고 루트에서 일관되게 거는 이유: 플러그인 구성이 다른 모듈(라이브러리 모듈)에서 설정이 조용히 빠지는 일을 막는다.

## 참고
- https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html (`-parameters`)
- https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x (6.1 parameter name retention)
- 스텝 기록: [10-1](../steps/step-10-1-common-errors-input.md)
