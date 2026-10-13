# 모든 Spring Boot 모듈이 같이 쓰는 이미지 템플릿. 빌드 전에 ./gradlew bootJar 를 실행한다.
FROM eclipse-temurin:17-jre

ARG MODULE
WORKDIR /app
COPY ${MODULE}/build/libs/${MODULE}-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-Xms128m -Xmx384m" \
    TZ=Asia/Seoul
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
