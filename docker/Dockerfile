# syntax=docker/dockerfile:1
# =====================================================================
# 모든 서비스가 같이 쓰는 Dockerfile 템플릿
#   docker-compose 에서 build.args.MODULE 로 빌드할 모듈을 지정한다.
#   빌드 컨텍스트는 프로젝트 루트 (멀티 모듈이라 common 이 함께 필요)
#
# 단독 빌드 예:
#   docker build -f docker/Dockerfile --build-arg MODULE=user-service -t user-service .
# =====================================================================

# ---------- 1단계: Gradle 빌드 ----------
FROM eclipse-temurin:17-jdk AS build
ARG MODULE
WORKDIR /workspace

COPY . .
# Windows 에서 clone 했을 때 CRLF 로 바뀐 gradlew 대비
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Gradle 캐시를 BuildKit 캐시로 재사용 (두 번째 빌드부터 빨라짐)
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :${MODULE}:bootJar -x test --no-daemon \
 && cp ${MODULE}/build/libs/*.jar /workspace/app.jar

# ---------- 2단계: 실행 이미지 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=Asia/Seoul
COPY --from=build /workspace/app.jar app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
