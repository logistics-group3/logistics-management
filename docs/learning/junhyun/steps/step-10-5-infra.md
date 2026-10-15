# 스텝 10-5. 인프라와 검증 스크립트 보강 (리뷰 반영)

- 날짜: 2026-10-14
- 근거: [리뷰 1](review-01-common-infra.md) minor 11(compose 단독 기동), 12(e2e), 13(포트 공개), nit(promote 스크립트, root 실행)
- 결과: 호스트 빌드 결과물과 DB 볼륨을 지운 빈 상태에서 `script/e2e-check.sh --up --with-cache-ttl` PASS 17 / FAIL 0 (229초, 이미지 안 Gradle 빌드 포함)

## 무엇을 고쳤나
| 문제 | 수정 |
|---|---|
| 이미지가 미리 빌드한 jar를 복사해서, 빈 checkout에서는 `docker compose up`만으로 뜨지 않음(완료 기준 1 문구와 다름) | 멀티스테이지 Dockerfile: 빌드 단계(JDK 17)에서 `./gradlew bootJar`, 실행 단계(JRE 17)는 jar만 복사 |
| postgres, config-server, eureka가 모든 주소(0.0.0.0)에 공개되고 예시 비밀번호가 알려져 있음 | 관리용 포트는 `127.0.0.1`에만 바인딩. 외부에는 Gateway 8080만 공개 |
| 컨테이너가 root로 실행 | `app` 사용자(uid 1001)로 실행 |
| e2e: `--up` 실패 후에도 진행, Eureka UP 상태 미확인, curl 타임아웃 없음 | 빌드·기동 실패 시 즉시 종료, UP 인스턴스가 있는 서비스만 집계, 준비 대기 5분 상한, curl `--max-time 10`, HTTP 상태와 JSON `code`를 함께 확인 |
| `promote-master.sh`가 username을 SQL 문자열에 그대로 넣음, updated_at 미갱신 | 형식 검사(`^[a-z0-9]{4,10}$`) + psql 변수(`:'username'`)로 전달, updated_at·updated_by 갱신 |

## 빌드 단계를 서비스 8개가 공유하는 방법
- Dockerfile의 빌드 단계에는 `MODULE` 인자를 쓰지 않는다. 서비스마다 다른 것은 실행 단계의 `COPY --from=build .../${MODULE}...` 한 줄뿐이다.
- 그래서 8개 서비스의 빌드 단계는 내용이 완전히 같고, BuildKit이 한 번만 실행해 나머지가 재사용한다. 로그에서 `gradlew bootJar`가 한 번만 실행된 것을 확인했다.
- `RUN --mount=type=cache,target=/root/.gradle`로 Gradle 배포판과 의존성을 캐시해 두 번째 빌드부터 빨라진다.
- `.dockerignore`는 반대로 바뀌었다. 전에는 jar만 보냈지만, 이제는 소스를 보내고 `build`, `.git`, `docs` 등을 뺀다. `config-repo`는 config-server 빌드에 필요해서 포함한다.

## 실측으로 확인한 리뷰 반영 (이번 컨테이너)
- `text/plain` 회원가입: 415 UNSUPPORTED_MEDIA_TYPE (전에는 500)
- `"affiliationType": 1`: 400 VALIDATION_ERROR (전에는 HUB로 가입)
- 경로 우회: SA 형식 400 INVALID_REQUEST (전에는 본문 없는 400)
- 컨테이너 사용자 `app`, 공개 포트는 gateway만 0.0.0.0

## 실행 방법 변경
```bash
cp .env.example .env
docker compose up -d --build     # 로컬 Gradle 빌드 불필요
script/e2e-check.sh              # --up을 붙이면 기동까지
```

## 면접 포인트
- 멀티스테이지 빌드의 장점(실행 이미지에 JDK·소스·빌드 도구가 없다)과 빌드 캐시
- 컨테이너를 root로 실행하면 안 되는 이유
- 포트 바인딩 주소(0.0.0.0과 127.0.0.1)의 차이
