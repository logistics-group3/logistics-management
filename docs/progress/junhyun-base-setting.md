# junhyun/base-setting

## 지금 하는 일
작업 규칙과 학습 모드 세팅

## 진행 상태
- [x] Gradle 멀티 모듈 뼈대 생성 (Boot 3.5.16, Cloud 2025.0.3, Gradle 8.14.3, toolchain 17). `./gradlew build` 통과, eureka-server 기동 시 "using Java 17.0.20" 확인
- [x] 모듈 8개: common, eureka-server(8761), gateway-service(8080, SA 라우팅 반영), user/hub/company/order/delivery-service(8081~8085)
- [x] 작업 규칙 문서: AGENTS.md, CLAUDE.md, docs/agents, .agents/skills
- [x] 기록 체계: docs/progress, docs/decisions, docs/learning
- [x] 외부 품질 플러그인 설치 (superpowers 6.4.1, ponytail 5.2.0 lite, jdtls-lsp + jdtls 1.61.0), project scope. `claude plugin list`에서 enabled 확인
- [x] 맥락 이어받기 테스트: 새 `claude -p`, `codex exec` 세션 모두 AGENTS.md와 progress만으로 상태·다음 단계·규칙을 정확히 요약. Codex가 `.agents/skills` 3종을 인식
- [x] Claude Code 학습 패널(study-mode): 패널 3탭, 상태줄, 새 노트 자동 선택. tsc, 테스트 3개, validate 통과. 시스템 프롬프트 주입 기능은 제거했다(메모 참고)
- [x] 변경 사항 커밋 (스캐폴드 / 작업 규칙 문서 / 플러그인·패널 3개로 분리)
- 면접 포인트: 멀티 모듈 구성 이유, 팀원 간 JDK 버전 차이를 빌드 수준에서 맞추는 방법

## 다음 단계
1. `docker-compose.yml`과 `infra/postgres/init.sql` 작성 (SA 3.2, 3.4, 10.3 기준)
2. 비즈니스 서비스 5개를 DB와 함께 기동하고 Eureka 등록 확인
3. common 모듈 구현: BaseEntity, ApiResponse, ErrorCode, GlobalExceptionHandler, UserContext

## 직접 해보기
처음부터 같은 뼈대를 만드는 순서다.

1. 버전 확인
   - `curl -s https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/maven-metadata.xml | grep 3.5.16`
   - Initializr가 3.5를 지원하지 않으므로 파일을 직접 쓴다.
2. 루트 파일 작성
   - `settings.gradle`: foojay 플러그인, `rootProject.name`, 모듈 `include`
   - `build.gradle`: Boot와 dependency-management 플러그인을 `apply false`로 선언하고, `subprojects`에 toolchain 17, mavenCentral, BOM 두 개, Lombok, 테스트 설정을 둔다.
   - `gradle.properties`: `springCloudVersion=2025.0.3`
3. Gradle wrapper 생성
   - CLI: `brew install gradle` 후 `gradle wrapper --gradle-version 8.14.3`
   - IntelliJ: Gradle 탭 → Tasks → build setup → wrapper
4. 모듈 작성 (eureka-server 예시)
   - `eureka-server/build.gradle`: `plugins { id 'org.springframework.boot' }`, 의존성 `spring-cloud-starter-netflix-eureka-server`
   - `src/main/java/com/sparta/logistics/eureka/EurekaServerApplication.java`에 `@EnableEurekaServer`
   - `src/main/resources/application.yml`: port 8761, `register-with-eureka: false`, `fetch-registry: false`
5. 확인
   - `./gradlew build` 결과가 BUILD SUCCESSFUL이면 성공
   - `./gradlew :eureka-server:bootRun` 후 브라우저에서 http://localhost:8761 대시보드가 열리면 성공
   - 로그에 "using Java 17"이 찍히는지 확인한다.
6. IntelliJ 설정
   - Project Structure → SDK: 17
   - Settings → Build Tools → Gradle → Gradle JVM: 17

## 메모
- 비즈니스 서비스는 DB가 없으면 기동에 실패한다. 정상이다.
- ADR: `docs/decisions/0001-boot-3-5-manual-gradle-multimodule.md`, `0002-agent-neutral-guidelines.md`
- 보류: Spring Security/JJWT, QueryDSL, springdoc (버전 미정)
- study-mode: 사용자 tier 플러그인의 `prompt.compose`는 Claude Code 기본 보안 플러그인(cc-plugin-sec-default)이 우회시킨다(debug 로그 확인). 학습 모드 지침은 AGENTS.md에 이미 있으므로 주입 기능과 `/study on|off`를 뺐다. 다른 경로(prompt.submit context)로 같은 지침을 넣는 방식은 보안 정책 우회에 해당하므로 쓰지 않는다.
