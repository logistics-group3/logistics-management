---
title: Initializr가 Boot 3.5를 만들지 못할 때와 Gradle toolchain으로 프로젝트별 JDK 고정하기
date: 2026-10-13
updated: 2026-10-13
asker: Junhyun An
level: 중급
tags: [gradle, java, spring-boot, toolchain]
---

# Initializr가 Boot 3.5를 만들지 못할 때와 Gradle toolchain으로 프로젝트별 JDK 고정하기

## 질문
JDK 17이 설치돼 있는데 PATH가 21로 잡혀 있다. 셸 기본값(JAVA_HOME)은 21로 두고 이 프로젝트만 17을 쓰려면 어떻게 하나. 팀이 정한 Spring Boot 3.5.16은 어떻게 설치하나.

## 짧은 답
- Spring Boot는 따로 설치하는 프로그램이 아니다. `build.gradle`의 플러그인 버전으로 지정하는 라이브러리다.
- start.spring.io는 지원 기간이 끝난 버전을 목록에서 뺀다. 2026-10 기준 4.0.0 이상만 생성하므로 3.5.16 프로젝트는 Gradle 파일을 직접 써야 한다.
- 프로젝트별 JDK는 Gradle toolchain으로 고정한다. Gradle 자체는 21로 돌아도 컴파일, 테스트, `bootRun`은 17로 실행된다.

## 자세히

### Gradle toolchain
```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
```
- Gradle이 설치된 JDK를 자동으로 찾는다(macOS의 `/usr/libexec/java_home`, IntelliJ가 받은 JDK, Gradle이 내려받은 JDK).
- 맞는 JDK가 없으면 `settings.gradle`의 foojay resolver 플러그인이 자동으로 내려받는다.
```groovy
plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}
```
- `./gradlew -q javaToolchains`로 Gradle이 찾은 JDK 목록을 볼 수 있다.

### JAVA_HOME과의 차이
| 방법 | 범위 | 단점 |
|---|---|---|
| JAVA_HOME 변경 | 셸 전체 | 다른 프로젝트에도 영향을 준다 |
| `gradle.properties`의 `org.gradle.java.home` | 이 프로젝트의 Gradle 데몬 | 경로가 PC마다 달라 팀 저장소에 커밋하기 어렵다 |
| toolchain | 이 프로젝트의 컴파일·실행 | Gradle 6.7 이상이 필요하다 |

- IntelliJ는 셸 설정을 따르지 않는다. Project Structure → SDK와 Settings → Gradle → Gradle JVM을 17로 따로 맞춘다.

### Initializr가 버전을 거부할 때
```bash
curl -s "https://start.spring.io/starter.zip?type=gradle-project&bootVersion=3.5.16"
# {"status":400,"message":"Invalid Spring Boot version '3.5.16', Spring Boot compatibility range is >=4.0.0"}
curl -s https://start.spring.io/metadata/client   # 현재 지원 버전 목록
```
- 4.x로 만든 뒤 버전만 낮추면 starter 이름과 기본 라이브러리가 달라서 더 꼬인다.
- Maven Central에서 버전이 실제로 있는지 확인한 뒤 직접 작성한다.
```bash
curl -s https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/maven-metadata.xml | grep 3.5.16
```

### Spring Cloud 버전 맞추기
- Spring Cloud는 Boot 버전마다 호환되는 릴리스 트레인이 정해져 있다. Boot 3.5.x에는 2025.0.x(Northfields)를 쓴다.
- BOM(`spring-cloud-dependencies`)으로 가져오면 개별 starter에 버전을 적지 않아도 된다.

## 이 프로젝트에서는
- 루트 `build.gradle`의 `subprojects`에 toolchain 17, Boot BOM, Cloud BOM을 둔다.
- `gradle.properties`의 `springCloudVersion=2025.0.3`
- ADR: `docs/decisions/0001-boot-3-5-manual-gradle-multimodule.md`

## 면접 포인트
- "팀원마다 JDK 버전이 다르면 어떻게 맞추나": toolchain으로 빌드 수준에서 고정하고, 자동 설치(foojay)로 환경 차이를 없앤다. 컨테이너 이미지도 같은 버전(eclipse-temurin:17)을 쓴다.
- "BOM이 뭔가": 서로 호환되는 라이브러리 버전 묶음이다. 직접 버전을 섞어 쓸 때 생기는 충돌을 막는다.

## 참고
- https://docs.gradle.org/current/userguide/toolchains.html
- https://spring.io/projects/spring-cloud (Supported Versions 표)
- 실행 확인: `./gradlew :eureka-server:bootRun` 로그 "Starting EurekaServerApplication using Java 17.0.20"
