---
title: 서비스 8개가 Gradle 빌드를 한 번만 공유하는 Docker 멀티스테이지 빌드와 BuildKit 캐시
date: 2026-10-14
updated: 2026-10-14
asker: Junhyun An
level: 중급
tags: [docker, buildkit, gradle, msa, ci]
---

# 서비스 8개가 Gradle 빌드를 한 번만 공유하는 Docker 멀티스테이지 빌드와 BuildKit 캐시

## 질문
서비스 8개 이미지를 만들면서 Gradle 빌드는 한 번만 했다는 게 어떻게 가능한가.

## 짧은 답
- Dockerfile을 **빌드 단계**(JDK + 소스 + Gradle)와 **실행 단계**(JRE + jar)로 나눴다(멀티스테이지).
- BuildKit은 각 단계의 명령을 "입력이 같으면 결과도 같다"는 기준(캐시 키)으로 관리한다. 빌드 단계를 서비스마다 **완전히 똑같이** 만들었기 때문에, 8개 이미지가 같은 빌드 결과를 공유한다.
- 서비스마다 다른 것은 실행 단계의 `COPY --from=build .../${MODULE}...` 한 줄뿐이다.
- Gradle 캐시는 `RUN --mount=type=cache`로 이미지 밖에 저장해 다음 빌드부터 빨라진다.

## 자세히

### 1. 이 프로젝트의 Dockerfile
```dockerfile
# 1단계: 빌드 (서비스와 무관하게 항상 같다)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon -q          # 8개 모듈의 jar를 한 번에 만든다

# 2단계: 실행 (서비스마다 다른 부분은 여기뿐)
FROM eclipse-temurin:17-jre
ARG MODULE
RUN useradd --system --uid 1001 app
WORKDIR /app
COPY --from=build /workspace/${MODULE}/build/libs/${MODULE}-0.0.1-SNAPSHOT.jar app.jar
USER app
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
```
compose는 서비스마다 `args: { MODULE: user-service }`처럼 `MODULE`만 다르게 넘긴다.

### 2. 멀티스테이지 빌드
- 최종 이미지에는 마지막 단계만 남는다. JDK, Gradle, 소스, 의존성 캐시는 들어가지 않고 JRE와 jar만 남는다.
- 장점: 이미지가 작고, 실행 이미지에 소스와 빌드 도구가 없어 공격 표면이 줄어든다.
- 전에는 호스트에서 `./gradlew bootJar`를 먼저 해야 했다. 지금은 `docker compose up --build`만으로 된다. 빌드 환경(JDK 17)이 이미지에 고정되므로, 팀원 PC의 JDK와도 무관하다.

### 3. BuildKit 캐시 키와 공유
- BuildKit은 Dockerfile을 단계 그래프로 바꾸고, 각 단계에 캐시 키를 붙인다. 키는 "부모 단계 + 명령어 + 그 명령이 쓰는 입력(복사한 파일 내용, 사용하는 ARG)"으로 정해진다.
- 빌드 단계에는 `ARG MODULE`이 없다. 그래서 user-service를 빌드하든 hub-service를 빌드하든 빌드 단계의 키가 똑같다.
- compose가 8개 이미지를 함께 빌드하면, BuildKit은 같은 키의 작업을 한 번만 실행하고 나머지는 결과를 재사용한다. 실제 빌드 로그에서 `gradlew bootJar`가 한 번만 실행된 것을 확인했다.
- **주의할 점**: `ARG MODULE`을 빌드 단계에 선언하면, 그 뒤의 RUN은 `MODULE` 값이 환경 변수로 들어가 키가 서비스마다 달라진다. 그러면 Gradle 빌드가 8번 돈다. 그래서 `ARG`는 실행 단계에만 선언했다.

### 4. 캐시 마운트
```dockerfile
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar ...
```
- Gradle 배포판, 다운로드한 의존성을 `/root/.gradle`에 두는데, 이 폴더를 BuildKit 캐시 저장소에 연결한다.
- 이미지 레이어에는 들어가지 않고, 다음 빌드에서 다시 쓴다. 소스만 바뀌었을 때 의존성을 다시 받지 않는다.

### 5. 캐시가 깨지는 때와 .dockerignore
- `COPY . .`로 들어간 파일 중 하나라도 바뀌면 그 뒤 단계(Gradle 빌드)는 다시 실행된다.
- 그래서 `../../../../.dockerignore`가 중요하다. `build/`, `.git`, `docs`, `*.md`를 빼서 문서를 고쳐도 이미지가 다시 빌드되지 않게 했다. 빌드 컨텍스트 전송량도 준다.
- `../../../../config-repo`는 config-server가 jar 안에 넣으므로 포함한다.
- 개선 여지: 빌드 파일 (`*.gradle`, `../../../../gradle`)만 먼저 복사해 의존성을 받는 단계를 따로 두면, 소스만 바뀔 때 의존성 단계 캐시를 그대로 쓸 수 있다. 지금은 캐시 마운트가 그 역할을 대신한다.

### 6. 트레이드오프
- 첫 빌드는 이미지 안에서 Gradle을 받고 전체를 컴파일하므로 느리다. 빈 상태에서 빌드, 기동, e2e까지 229초였다(캐시 시나리오 65초 포함).
- 로컬에서 이미 빌드했어도 이미지 빌드는 컨테이너 안에서 다시 컴파일한다. 대신 "누가 어디서 빌드해도 같은 결과"를 얻는다. CI에서도 그대로 쓸 수 있다.

## 이 프로젝트에서는
- `../../../../Dockerfile`, `.dockerignore`, `docker-compose.yml`(서비스별 `MODULE` 인자)
- 실행 단계는 `app` 사용자(uid 1001)로 실행한다(root 실행 방지).
- 스텝 기록: [10-5](../steps/step-10-5-infra.md)

## 면접 포인트
- "멀티스테이지 빌드를 왜 쓰나": 이미지 크기, 보안(빌드 도구·소스 제외), 빌드 환경 고정
- "Docker 빌드 캐시는 언제 깨지나": 명령어, 복사한 파일 내용, 사용하는 ARG가 바뀔 때. 레이어 순서를 "자주 안 바뀌는 것 먼저"로 두는 이유
- "모노레포 MSA에서 이미지 빌드를 효율적으로 하는 방법": 공통 빌드 단계 공유, 캐시 마운트, .dockerignore

## 참고
- https://docs.docker.com/build/building/multi-stage/
- https://docs.docker.com/build/cache/ (캐시 무효화 규칙)
- https://docs.docker.com/build/cache/optimize/#use-cache-mounts
