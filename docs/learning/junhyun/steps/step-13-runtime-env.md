# 스텝 13. 실행 환경 보강 (줄바꿈, 인코딩, 데몬 JDK, 비밀값)

- 날짜: 2026-10-14
- 근거: Notion "main 통합 방안 — 팀 합의 대조·추가 검토" 2장 (팀원 브랜치에 있고 통합안에 빠진 항목)
- 결과: `./gradlew --version`에 `Daemon JVM: Compatible with Java 17 (from gradle/gradle-daemon-jvm.properties)` 표시, `compileJava compileTestJava` 성공, `git add --renormalize .` 후 줄바꿈이 바뀐 파일 없음

## 바꾼 것
| 파일 | 내용 | 출처 |
|---|---|---|
| `.gitattributes` | `* text=auto`, `gradlew`·`*.sh` LF, `*.bat`·`*.cmd` CRLF, `*.jar` binary | KEJ, KMH, seungkeun |
| 루트 `build.gradle` | `JavaCompile`에 `options.encoding = 'UTF-8'` | haneol, KEJ, KMH, seungkeun |
| `gradle/gradle-daemon-jvm.properties` | `./gradlew updateDaemonJvm --jvm-version=17`로 생성. 데몬 JDK 17 고정, OS별 JDK 다운로드 주소 포함 | haneol |
| `.gitignore` | `.env.*`(단 `.env.example` 제외), `*.pem`, `*.key`, `*.p12`, `*.jks`, `CLAUDE.local.md`, `.claude/settings.local.json` | haneol |

## 리뷰 포인트
1. **toolchain과 데몬 JDK는 다르다**: `java.toolchain`은 컴파일·테스트에 쓰는 JDK를 정한다. 빌드 스크립트를 실행하는 Gradle 데몬은 셸의 JDK를 그대로 쓴다. 셸 기본 JDK가 Gradle이 지원하지 않는 버전이면 toolchain 설정까지 가지 못하고 실패한다. 데몬 JVM 기준 파일이 이 단계를 고정한다.
2. **파일을 직접 쓰지 않고 `updateDaemonJvm`로 만든 이유**: 직접 쓰면 `toolchainVersion=17`만 들어간다. 생성 태스크는 JDK 17이 없는 PC에서 내려받을 주소(`toolchainUrl.*`)까지 넣는다. 주소는 settings.gradle의 foojay resolver가 정한다.
3. **줄바꿈을 저장소 설정으로 고정하는 이유**: Windows의 `core.autocrlf=true`는 checkout할 때 LF를 CRLF로 바꾼다. 그 상태로 Docker 이미지에 들어간 `gradlew`와 `init.sh`는 첫 줄 `#!/bin/sh\r` 때문에 실행되지 않는다. `.gitattributes`는 개인 git 설정보다 우선한다.
4. **인코딩 기본값**: JDK 18부터 기본 문자셋이 UTF-8이지만(JEP 400), 이 프로젝트의 JDK 17은 OS 설정을 따른다.

## 확인한 것
- `git ls-files --eol`: `gradlew`, `script/*.sh`, `infra/postgres/init.sh`는 `attr/text eol=lf`, `gradlew.bat`은 `attr/text eol=crlf`.
- Windows에서 실제로 clone·실행해 보지는 않았다(미확인).

## 팀원에게 알릴 것
- 이미 clone한 Windows 환경에서는 한 번 `git rm --cached -r . && git reset --hard`로 다시 checkout해야 새 줄바꿈 규칙이 작업 트리에 적용된다. 커밋하지 않은 변경이 있으면 먼저 커밋하거나 stash한다.
