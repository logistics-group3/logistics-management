# 스텝 31. 로컬 실행 스크립트 `script/run.sh`

- 날짜: 2026-10-14
- 근거: Notion "docs·AI 지침 통합 방안" 5장(haneol, KEJ, KMH의 `run.sh`와 통합안 `e2e-check.sh --up`을 하나로)
- 결과: `script/run.sh`를 추가하고 README 빠른 실행을 이 스크립트 기준으로 바꿨다. 별도 Compose 프로젝트에서 `--reset --check`가 E2E PASS 15 / FAIL 0으로 끝났다.

## 사용법
| 명령 | 동작 | 출처 |
|---|---|---|
| `script/run.sh` | `.env`가 없으면 `.env.example` 복사 → 빌드 → 전체 기동 → 상태와 접속 주소 출력 | KEJ |
| `script/run.sh hub-service` | 지정한 서비스만 빌드하고 기동(의존 서비스는 떠 있지 않을 때만 띄운다) | haneol, KMH |
| `script/run.sh --scale hub-service=2` | docker compose에 그대로 전달 | KMH |
| `script/run.sh --reset` | `docker compose down -v` 후 기동 | haneol |
| `script/run.sh --check` | 기동 후 `script/e2e-check.sh` | 통합안, KEJ `smoke-test.sh` |
| `script/run.sh --no-build` | 빌드 없이 기동 | |

## 리뷰 포인트
1. **지정한 서비스만 빌드하는 이유**: 처음에는 `docker compose up -d --build hub-service`를 그대로 불렀다. 실행해 보니 hub만 바꿨는데 config-server와 eureka-server도 매번 다시 만들어졌다. `up --build`는 의존 서비스까지 빌드하고, 빌드할 때마다 이미지 ID가 바뀐다(생성 시각은 같고 ID만 달랐다). 그래서 `docker compose build {서비스}`로 지정한 서비스만 빌드한 뒤 `up -d`를 빌드 없이 부른다. 바꾼 뒤에는 hub 컨테이너만 교체됐다.
2. **haneol `--profile domain`을 넣지 않았다**: 이 저장소 compose에는 profiles가 없고 9개가 모두 뜬다. 일부만 띄우려면 서비스 이름을 넘긴다.
3. **로컬 Gradle 빌드 단계가 없다**: Dockerfile이 컨테이너 안에서 빌드한다. haneol 방식의 `./gradlew bootJar` 선행 단계는 필요 없다.
4. **bash 3.2 호환**: macOS 기본 bash는 `set -u`에서 빈 배열을 펼치면 오류를 낸다. `${arr[@]+"${arr[@]}"}` 형태로 피했다.
5. **포트는 compose에서 조회**: 출력하는 주소는 `.env`를 셸로 실행하지 않고 `docker compose port`로 실제 공개 포트를 읽는다. 병행 환경(다른 포트)에서도 맞는 주소가 나온다.

## 검증
- 가짜 `docker` 명령으로 인자 전달을 확인했다(`/bin/bash` 3.2): `.env` 복사, `build hub-service order-service` → `up -d --scale hub-service=2 hub-service order-service`, `--reset`은 `down -v --remove-orphans` 뒤 `build`·`up`, `--no-build`는 `up -d`만, 잘못된 옵션은 exit 2.
- 실제 실행은 별도 Compose 프로젝트(`logistics-pra-20261014`, 호스트 포트 2xxxx)에서 했다. 기존 `logistics-management` 컨테이너 9개는 건드리지 않았고 검증 뒤 테스트 프로젝트는 `down -v`로 지웠다.

| 명령 | 결과 |
|---|---|
| `script/run.sh --reset --check` | E2E PASS 15 / FAIL 0. 주소 4줄이 2xxxx 포트로 출력 |
| `script/run.sh --no-build --scale hub-service=2 hub-service` | hub 컨테이너 2개, Eureka `HUB-SERVICE` UP 2 |
| `script/run.sh hub-service` (수정 전) | config-server, eureka-server, hub가 다시 만들어짐 |
| `script/run.sh hub-service` (수정 후) | hub만 빌드·교체, 8초 |
| `script/run.sh --check` (수정 후, 빌드 캐시 있음) | E2E PASS 15 / FAIL 0, 32초 |

- 첫 실행은 Docker Hub에서 Dockerfile 문법 이미지(`docker/dockerfile:1`)를 받는 데 약 6분 걸렸다. 네트워크 상황에 따른 것이고 다음 실행부터는 캐시를 쓴다.
