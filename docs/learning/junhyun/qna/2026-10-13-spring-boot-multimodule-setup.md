---
title: Spring Boot 멀티 모듈 프로젝트 초기 세팅 - IntelliJ와 CLI
date: 2026-10-13
updated: 2026-10-13
asker: Junhyun An
level: 기초
tags: [spring-boot, gradle, intellij, multi-module]
---

# Spring Boot 멀티 모듈 프로젝트 초기 세팅 - IntelliJ와 CLI

## 질문
이미 Git 저장소가 있는 디렉터리에 Spring Boot 프로젝트를 초기 세팅하려면 어떻게 하나. IntelliJ를 쓰는 방법과 커맨드를 쓰는 방법을 둘 다 알고 싶다.

## 짧은 답
- 단일 서비스라면 Spring Initializr(start.spring.io)로 만든 결과를 디렉터리에 풀면 끝난다.
- MSA처럼 서비스가 여러 개면 Gradle 멀티 모듈로 구성한다. 루트에는 wrapper와 `settings.gradle`만 두고, 서비스마다 하위 모듈을 둔다.
- IntelliJ는 File → New → Module → Spring Boot(Ultimate 전용)로 모듈을 추가한다. CLI는 `curl https://start.spring.io/starter.zip`으로 모듈을 받는다.
- 어느 쪽이든 모듈마다 생기는 wrapper와 `settings.gradle`을 지우고, 루트 `settings.gradle`에 `include`로 등록해야 한다.

## 자세히

### 멀티 모듈 구조
```
root/
├── settings.gradle     include 'common', 'user-service', ...
├── build.gradle        공통 설정 (subprojects 블록)
├── gradlew, gradle/    wrapper는 루트에 하나만
├── common/             공통 라이브러리 (bootJar 없음)
└── user-service/       실행 가능한 Spring Boot 앱
```
- `settings.gradle`은 어떤 모듈이 이 빌드에 속하는지 정한다.
- 루트 `build.gradle`의 `subprojects { }`에 공통 설정을 두면 모듈별 build.gradle에는 의존성만 남는다.
- 다른 모듈을 쓰려면 `implementation project(':common')`를 선언한다.

### CLI 방법
```bash
curl https://start.spring.io          # 사용 가능한 버전과 의존성 id 목록

curl -s https://start.spring.io/starter.zip \
  -d type=gradle-project -d language=java -d javaVersion=17 \
  -d groupId=com.sparta -d artifactId=user-service \
  -d packageName=com.sparta.logistics.user \
  -d dependencies=web,data-jpa,postgresql,validation,lombok \
  -d baseDir=user-service -o m.zip && unzip -q m.zip && rm m.zip
```
받은 뒤에는 모듈 안의 `gradlew*`, `gradle/`, `settings.gradle`을 지운다.

### IntelliJ 방법
1. File → Open으로 루트 디렉터리를 연다.
2. 루트에 `settings.gradle`을 만들고 Load Gradle Project를 누른다.
3. Gradle 탭 → Tasks → build setup → `wrapper`로 wrapper를 만든다.
4. File → New → Module → Spring Boot. Location은 루트, Type은 Gradle - Groovy로 지정한다.
5. 모듈 안의 wrapper와 settings를 지우고 루트 `include`에 등록한 뒤 Reload를 누른다.

### 주의할 점
- Gateway(Spring Cloud Gateway)는 WebFlux 기반이다. `spring-boot-starter-web`(MVC)을 함께 넣으면 충돌한다.
- 공통 모듈의 빈을 서비스에서 쓰려면 컴포넌트 스캔 범위를 넓혀야 한다. 서비스 패키지가 `com.sparta.logistics.user`면 기본 스캔이 `com.sparta.logistics.common`에 닿지 않는다.
- Initializr가 원하는 버전을 지원하지 않으면 이 방법들은 쓸 수 없다. 관련 노트: [Gradle toolchain과 Initializr](2026-10-13-gradle-toolchain-and-initializr.md)

## 이 프로젝트에서는
- 팀 버전(Boot 3.5.16)을 Initializr가 지원하지 않아 Gradle 파일을 직접 작성했다. ADR: `docs/decisions/0001-boot-3-5-manual-gradle-multimodule.md`
- 공통 설정은 루트 `build.gradle`의 `subprojects`, 서비스 메인 클래스는 `scanBasePackages = "com.sparta.logistics"`

## 면접 포인트
- 멀티 모듈을 쓰는 이유: 공통 코드 재사용, 빌드 설정 일원화, 모듈 간 의존 방향을 빌드 수준에서 강제할 수 있다.
- 공통 모듈의 단점: 변경이 모든 서비스에 영향을 준다. MSA에서는 공통 모듈을 작게 유지하고 도메인 로직을 넣지 않는다.

## 참고
- https://start.spring.io
- https://docs.gradle.org/current/userguide/multi_project_builds.html
