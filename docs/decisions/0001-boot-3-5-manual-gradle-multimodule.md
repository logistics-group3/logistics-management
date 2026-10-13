# 0001. Spring Boot 3.5.16 수동 Gradle 멀티 모듈 구성

- 상태: 채택
- 날짜: 2026-10-13
- 결정자: 팀(버전), junhyun(구성 방식)

## 맥락
- 팀이 정한 버전은 Java 17, Spring Boot 3.5.16, Spring Cloud 2025.0.x다.
- start.spring.io는 2026-10-13 기준 Spring Boot 4.0.0 이상만 생성한다. 3.5.16으로 요청하면 400 응답과 `Spring Boot compatibility range is >=4.0.0` 메시지가 온다. IntelliJ의 Spring Boot 생성기도 같은 서버를 쓴다.
- 개발자 PC의 기본 JDK는 21이고, JDK 17은 별도로 설치되어 있다.

## 결정
- Initializr를 쓰지 않고 Gradle 파일을 직접 작성한다.
- 루트 `build.gradle`의 `subprojects`에서 공통 설정(toolchain, 저장소, BOM, Lombok, 테스트)을 관리하고, 각 모듈은 자기 starter만 선언한다.
- Spring Cloud는 2025.0.x의 최신 패치인 2025.0.3을 쓴다(`gradle.properties`의 `springCloudVersion`).
- Gradle wrapper는 8.14.3으로 고정한다.
- Java는 Gradle toolchain으로 17을 고정한다. 셸 JAVA_HOME은 바꾸지 않는다. JDK 17이 없는 팀원을 위해 foojay resolver로 자동 설치되게 한다.
- Gateway는 2025.0.x에서 이름이 바뀐 `spring-cloud-starter-gateway-server-webflux`와 `spring.cloud.gateway.server.webflux.*` 설정을 쓴다.

## 검토한 대안
- 4.x로 생성한 뒤 버전만 3.5.16으로 낮추기: 4.x는 starter 구성과 기본 라이브러리(Jackson 3 등)가 달라 낮추는 과정에서 더 꼬인다.
- 모듈마다 독립된 build.gradle과 wrapper 두기: 버전 관리가 8곳으로 흩어진다.
- 셸 JAVA_HOME을 17로 바꾸기: 다른 프로젝트(21 사용)에 영향을 준다.

## 결과
- 버전은 루트 `build.gradle`과 `gradle.properties` 두 곳에서만 관리한다.
- Boot 3.5.x의 OSS 지원이 끝나면 4.x 이전을 따로 검토해야 한다.
