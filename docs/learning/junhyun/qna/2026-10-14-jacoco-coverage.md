---
title: JaCoCo로 테스트 커버리지 재기 - 원리, 리포트 읽는 법, 함정
date: 2026-10-14
updated: 2026-10-14
asker: Junhyun An
level: 중급
tags: [jacoco, test-coverage, testing, gradle, ci]
---

# JaCoCo로 테스트 커버리지 재기 - 원리, 리포트 읽는 법, 함정

## 질문
JaCoCo에 대해 쉽고 자세하게 알고 싶다.

## 짧은 답
- JaCoCo(Java Code Coverage)는 **테스트가 실행되는 동안 어떤 코드가 실제로 실행됐는지** 기록하고, 그 결과를 리포트로 보여 주는 도구다.
- 코드를 고치지 않는다. 테스트를 돌릴 때 JVM에 붙어서 몰래 체크 표시를 남긴다.
- 숫자는 "얼마나 실행됐나"를 알려 줄 뿐, "제대로 검증했나"를 알려 주지는 않는다. 그래서 숫자보다 **빨간 줄(실행 안 된 곳)을 보고 빠진 테스트를 찾는 도구**로 쓰는 게 가장 유용하다.

## 쉽게 이해하기
- 코드를 지도라고 하면, 테스트는 그 지도 위를 걸어가는 사람이다.
- JaCoCo는 사람이 지나간 길에 형광펜을 칠해 준다. 테스트가 끝나면 형광펜이 칠해진 길(실행된 코드)과 안 칠해진 길(실행 안 된 코드)이 보인다.
- 안 칠해진 길은 "한 번도 테스트해 보지 않은 길"이다. 거기서 버그가 나도 테스트는 모른다.
- 다만 형광펜은 "지나갔다"는 표시일 뿐이다. 지나가면서 길이 맞는지 확인(assert)했는지는 알려 주지 않는다.

## 자세히

### 1. 동작 원리: 측정 → 기록 → 리포트
1. **측정**: `./gradlew test`를 하면 Gradle이 테스트 JVM에 JaCoCo 에이전트를 붙인다. 에이전트는 클래스가 메모리에 올라오는 순간 바이트코드 곳곳에 작은 체크 지점(probe)을 끼워 넣는다. 소스 코드와 class 파일은 바뀌지 않는다.
2. **기록**: 테스트가 코드를 실행할 때마다 지나간 체크 지점이 표시된다. 테스트 JVM이 끝나면 결과를 `build/jacoco/test.exec` 파일로 남긴다.
3. **리포트**: `jacocoTestReport` 작업이 `test.exec`, 컴파일된 class 파일, 소스 파일을 합쳐 HTML, XML, CSV 리포트를 만든다. HTML은 `build/reports/jacoco/test/html/index.html`에 생긴다.

### 2. 지표 종류
| 지표 | 뜻 | 볼 만한가 |
|---|---|---|
| Instruction | 바이트코드 명령 단위로 실행 여부 | 가장 정밀하지만 사람이 읽기 어렵다 |
| **Line** | 소스 한 줄 중 일부라도 실행됐나 | 직관적이다. 가장 많이 쓴다 |
| **Branch** | if, switch, `&&`, `\|\|`, 삼항 연산자의 갈래를 모두 지나갔나 | **가장 중요하다.** 줄은 실행됐어도 한쪽 갈래만 지나갔을 수 있다 |
| Complexity | 코드의 독립 경로 수(순환 복잡도) 중 몇 개를 지나갔나 | 테스트가 몇 개 더 필요한지 가늠할 때 |
| Method, Class | 한 번이라도 실행된 메서드·클래스 비율 | 아예 손대지 않은 곳을 찾을 때 |

라인과 분기의 차이가 핵심이다.
```java
if (body != null && body.hasNonNull("code") && body.hasNonNull("message")) {
```
이 한 줄에는 갈래가 6개 있다(조건 3개 × 참·거짓). 테스트가 이 줄을 한 번만 지나가도 라인 커버리지는 100%가 된다. 분기 커버리지는 6개 중 몇 개를 지나갔는지 따로 센다.

### 3. 실제 리포트 읽기: 우리 프로젝트의 FeignErrorDecoder
2026-10-14에 common 테스트로 측정한 결과다(저장소를 바꾸지 않고 임시 init script로 측정).
```
[실행  ]  34   HttpStatusCode status = HttpStatusCode.valueOf(response.status());
[실행  ]  35   if (status.is4xxClientError()) {                                     분기 2/2
[실행  ]  36       JsonNode body = readBody(response);
[일부  ]  37       if (body != null && body.hasNonNull("code") && body.hasNonNull("message")) {  분기 4/6
[실행  ]  38           return new BusinessException(new SimpleErrorCode(...));
[실행  ]  41   log.warn("하위 서비스 호출 실패: ...");
[실행  ]  42   return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);

[일부  ]  46   if (response.body() == null) {                                       분기 1/2
[미실행]  47       return null;
[실행  ]  49   try (InputStream body = response.body().asInputStream()) {
[실행  ]  50       return objectMapper.readTree(body);
[실행  ]  51   } catch (IOException e) {
[실행  ]  52       return null;
```
- 클래스 전체: 라인 16/17(94%), 분기 7/10(70%)
- HTML 리포트에서는 초록(실행), 빨강(미실행), 노랑(분기 일부)으로 칠해진다. `[일부]`가 노란 줄이다.

**이 리포트가 알려 주는 빠진 테스트**
- 37번 줄 분기 4/6: 404 + `{"code","message"}` 응답(참 경로)과, HTML 본문이라 파싱에 실패한 응답(`body == null`)은 테스트했다. 하지만 **"JSON이긴 한데 `code`가 없는 4xx"**(`{"error":"x"}` 같은)와 **"`message`가 없는 4xx"**는 테스트하지 않았다. 다른 프레임워크로 만든 서비스나 Gateway 기본 오류가 이런 모양으로 올 수 있다.
- 46~47번 줄: **본문이 아예 없는 4xx 응답**(`response.body() == null`)을 테스트하지 않았다.
- 37번 줄은 라인 커버리지로는 "실행됨"이다. 분기를 보지 않으면 이 빈틈을 놓친다.
- 51~52번 줄 `catch`가 실행된 이유: `<html>bad</html>` 본문을 JSON으로 읽다가 `JsonParseException`(IOException의 하위 클래스)이 나서 잡혔다. 리포트가 어떤 테스트가 어떤 경로를 지나는지까지 짐작하게 해 준다.

### 4. Gradle 설정
```groovy
// 루트 build.gradle, subprojects 안
apply plugin: 'jacoco'

tasks.named('test') {
    finalizedBy 'jacocoTestReport'          // 테스트가 끝나면 리포트 생성
}

tasks.named('jacocoTestReport') {
    reports { html.required = true; xml.required = true }   // xml은 CI 도구(SonarQube 등)가 읽는다
    classDirectories.setFrom(files(classDirectories.files.collect {
        fileTree(it) { exclude '**/*Application.class', '**/config/**' }   // 측정 가치가 낮은 코드 제외
    }))
}

tasks.named('jacocoTestCoverageVerification') {
    violationRules {
        rule {
            limit { counter = 'BRANCH'; minimum = 0.70 }   // 분기 70% 미만이면 빌드 실패
        }
    }
}
tasks.named('check') { dependsOn 'jacocoTestCoverageVerification' }
```
- `jacocoTestCoverageVerification`에 기준을 걸면 기준 아래로 떨어질 때 `./gradlew build`가 실패한다. CI에서 품질 하한선으로 쓴다.

### 5. 자동으로 빠지는 코드와 직접 빼야 하는 코드
- **자동으로 빠진다**: JaCoCo는 이름에 `Generated`가 들어가고 class 파일이나 런타임에 남는 어노테이션이 붙은 클래스·메서드를 측정에서 뺀다. 우리 프로젝트의 QueryDSL Q클래스(`QUser`)에는 `@com.querydsl.core.annotations.Generated`가 붙어 있어 리포트에 아예 나오지 않는다(실제 리포트로 확인). 컴파일러가 만드는 enum `values()`, try-with-resources의 숨은 분기도 자동으로 빠진다.
- **설정해야 빠진다**: Lombok이 만든 getter, 생성자는 기본적으로 측정에 들어간다. 프로젝트 루트에 `lombok.config` 파일을 두고 `lombok.addLombokGeneratedAnnotation = true`를 넣으면 Lombok이 `@Generated`를 붙여 자동으로 빠진다.
- **판단해서 뺀다**: `main` 메서드가 있는 Application 클래스, 빈 등록만 하는 설정 클래스. 단위 테스트 가치가 낮고 e2e로 확인한다.

### 6. 멀티 모듈의 함정
- 기본 리포트는 **모듈마다 자기 모듈의 테스트만** 센다.
- 우리 프로젝트에서 common의 `BaseEntity`, `ApiResponse`가 0%로 나왔다. 실제로는 user-service 통합 테스트가 실행하는 코드인데, user-service 테스트는 user-service 리포트에만 집계되기 때문이다.
- 모듈을 넘어 합쳐 보려면 Gradle의 `jacoco-report-aggregation` 플러그인으로 전체 리포트를 하나 더 만든다.

### 7. 커버리지의 함정
- **실행했다고 검증한 것은 아니다**: assert가 하나도 없는 테스트도 커버리지를 올린다. 100%여도 버그를 못 잡을 수 있다.
- **숫자만 쫓으면 낭비다**: getter, 설정 클래스까지 테스트해 숫자를 올리는 건 비용만 든다. 도메인 규칙, 보안 경계, 예외 처리처럼 틀리면 아픈 코드를 높게 유지한다.
- **보완 도구**: 코드를 일부러 바꿨을 때 테스트가 실패하는지 보는 뮤테이션 테스트(PIT)가 "검증했는가"를 잰다. 우리 프로젝트의 동시 가입 테스트에서 `saveAndFlush`를 `save`로 되돌려 실패를 확인한 것과 같은 생각이다.

### 8. 실무에서 쓰는 방식
- **전체 숫자보다 새 코드의 커버리지**: SonarQube, Codecov 같은 도구가 JaCoCo XML을 읽어, PR에서 새로 추가·수정한 줄의 커버리지만 따로 보여 준다. "이번 PR에서 추가한 코드는 80% 이상" 같은 기준이 전체 기준보다 현실적이다.
- **리뷰 도구로 쓰기**: PR 리뷰 때 빨간 줄을 보고 "이 예외 경로는 왜 테스트가 없나"를 묻는다.

## 이 프로젝트에서는
- 도입: 보류(2026-10-14 결정). 측정만 임시로 해 봤다. 결과는 [리뷰 2](../steps/review-02-reinvention-check.md)에 있다.
- 측정값(모듈 자체 테스트 기준): common 라인 72%·분기 64%, gateway 87%·73%, user-service 89%·100%
- 도입한다면 제안: 서비스별 도메인 코드가 생긴 뒤, 분기 70%를 최소 기준으로 걸고, `lombok.config`, Application·설정 클래스 제외, `jacoco-report-aggregation`으로 전체 리포트를 함께 만든다.
- 이번 측정으로 찾은 빠진 테스트: FeignErrorDecoder의 "code·message가 없는 4xx JSON", "본문이 없는 4xx"

## 면접 포인트
- "라인 커버리지와 분기 커버리지의 차이": 한 줄의 if 안에 갈래가 여러 개일 수 있다. 분기 커버리지가 빈틈을 더 잘 드러낸다.
- "커버리지 100%면 안전한가": 아니다. 실행과 검증은 다르다. 뮤테이션 테스트로 보완하고, 중요한 코드에 집중한다.
- "JaCoCo는 어떻게 측정하나": Java 에이전트가 클래스 로딩 시 바이트코드에 probe를 삽입(on-the-fly instrumentation)하고, 실행 결과를 .exec 파일로 남긴다.

## 참고
- https://www.jacoco.org/jacoco/trunk/doc/counters.html (지표 정의)
- https://www.jacoco.org/jacoco/trunk/doc/faq.html (Generated 어노테이션 필터)
- https://docs.gradle.org/current/userguide/jacoco_plugin.html
- https://docs.gradle.org/current/userguide/jacoco_report_aggregation_plugin.html
- 관련 노트: [Flyway와 JaCoCo 개요](2026-10-14-flyway-and-jacoco.md), [flush 시점과 동시성 테스트 검증](2026-10-14-jpa-flush-timing-and-concurrency-test.md)
