---
title: 모듈마다 Spring 프로젝트를 두면 작업 환경이 무거워지지 않나
date: 2026-10-13
updated: 2026-10-13
asker: Junhyun An
level: 기초
tags: [gradle, multi-module, msa, docker, intellij]
---

# 모듈마다 Spring 프로젝트를 두면 작업 환경이 무거워지지 않나

## 질문
모듈별로 Spring 프로젝트를 세팅하면 엄청 무거운 워크스페이스가 될 것 같아 걱정된다.

## 짧은 답
- **코드와 빌드는 무겁지 않다.** 멀티 모듈은 "Spring 프로젝트 8개"가 아니라 Gradle 프로젝트 하나 안에 폴더 8개가 있는 구조다. IDE 창 하나, Gradle 데몬 하나로 다룬다. 공통 설정은 루트에 한 번만 쓰기 때문에, 모듈 하나는 build.gradle 몇 줄과 메인 클래스, 설정 파일 정도로 이루어진다.
- **무거운 것은 실행이다.** 서비스마다 JVM이 하나씩 떠서, 이 프로젝트를 전부 띄우면 메모리를 약 3GB 쓴다.
- 그래서 **필요한 것만 띄우는 습관**이 핵심이다. 지금 작업 중인 서비스와 그 서비스가 의존하는 것만 실행한다.

## 자세히

### 실제 측정값 (2026-10-13, docker stats)
| 컨테이너 | 메모리 |
|---|---|
| eureka-server | 약 495MB |
| 서비스 5개 | 각 약 405~450MB |
| gateway-service | 약 373MB |
| postgres | 약 118MB |
| **합계** | **약 3.1GB** |

- 빈 서비스도 400MB 안팎을 쓰는 이유: Spring 컨텍스트, Hibernate, Eureka 클라이언트, 내장 Tomcat이 기본으로 올라오기 때문이다. Dockerfile에서 `-Xmx384m`로 힙 상한을 정해 뒀다.

### 무거움을 줄이는 방법
1. **필요한 컨테이너만 띄운다**
   ```bash
   docker compose up -d postgres eureka-server gateway-service user-service
   ```
2. **작업 중인 서비스는 IDE에서 실행한다.** 나머지는 compose로 띄우고, 고치는 서비스 하나만 IntelliJ에서 디버그 모드로 실행한다(같은 포트의 컨테이너는 먼저 내린다).
3. **해당 모듈만 빌드하고 테스트한다**
   ```bash
   ./gradlew :user-service:test
   ./gradlew :user-service:bootRun
   ```
4. **공통 설정은 한곳에 둔다.** 루트 `build.gradle`의 `subprojects`와 config-server를 쓰면 모듈마다 같은 내용을 반복하지 않는다. 이 프로젝트의 모듈 build.gradle은 7~16줄이다.

### 왜 이렇게까지 나누나
- 서비스마다 배포, 확장, 장애가 독립적이어야 한다는 MSA의 목표 때문이다. 대신 운영 복잡도와 리소스 비용을 감수한다.
- 작은 팀이나 초기 제품이라면 모놀리스(또는 모듈러 모놀리스)가 더 합리적인 경우가 많다. 이 프로젝트는 MSA 학습이 목적이라 나눈 것이다.

## 이 프로젝트에서는
- 루트 `build.gradle`(45줄)이 공통 설정을 맡고, `eureka-server/build.gradle`은 7줄, `user-service/build.gradle`은 16줄이다.
- 다음 스텝의 config-server가 서비스 설정 중복을 한 번 더 줄인다.

## 면접 포인트
- "MSA의 단점은?": 리소스 비용(서비스마다 런타임), 운영 복잡도(배포, 모니터링, 분산 추적), 분산 트랜잭션, 로컬 개발 환경의 무게. 이 단점을 감수할 만큼 독립 배포와 확장이 필요할 때 선택한다.
- "모놀리스와 MSA 중 무엇을 고르겠나": 팀 규모, 도메인 경계의 명확성, 트래픽 특성을 근거로 답한다. 처음부터 MSA보다 모듈러 모놀리스에서 시작해 분리하는 전략도 언급할 수 있다.

## 참고
- 측정: `docker stats --no-stream`
- 관련 노트: [멀티 모듈 초기 세팅](2026-10-13-spring-boot-multimodule-setup.md), [Eureka 설정](2026-10-13-eureka-server-setup.md)
