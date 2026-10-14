# syntax=docker/dockerfile:1
# 모든 애플리케이션 모듈이 공유하는 멀티 스테이지 이미지. MODULE 빌드 인자로 대상 모듈을 지정한다.
# docker compose up --build 한 번으로 컨테이너 안에서 Gradle 빌드까지 끝난다 (호스트 JDK 불필요).

FROM eclipse-temurin:17-jdk AS build
ARG MODULE
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew
# Gradle 배포판·의존성은 BuildKit 캐시에 보관해 두 번째 빌드부터 재다운로드하지 않는다.
# sharing=locked: compose가 이미지 7개를 동시에 빌드할 때 빈 캐시에 동시에 내려받다 실패하는 것을 막는다 (순차 실행).
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :${MODULE}:bootJar --no-daemon --console=plain

FROM eclipse-temurin:17-jre
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/build/libs/${MODULE}.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
