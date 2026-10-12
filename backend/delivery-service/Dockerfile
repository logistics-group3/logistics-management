# 공통 Dockerfile 템플릿: 먼저 루트에서 ./gradlew bootJar (또는 script/run.sh)
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Seoul
COPY build/libs/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
