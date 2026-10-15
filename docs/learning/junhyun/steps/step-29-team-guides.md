# 스텝 29. 팀 가이드와 문제 해결 기록

- 날짜: 2026-10-14
- 근거: Notion "docs·AI 지침 통합 방안" 팀 가이드 항목(haneol 11·12·15·16, kej IDE 병행 절차·8장, kmh 트러블슈팅 형식)
- 결과: `docs/guides/`에 구현·로컬 실행·설정 보안 가이드 3개, `docs/troubleshooting.md`를 추가했다. 문서 지도(`docs/README.md`)가 이미 이 경로를 가리키고 있어 링크가 이어진다.

## 바꾼 것
| 파일 | 원본 | 이 저장소에 맞춘 점 |
|---|---|---|
| `docs/guides/dev-guide.md` | haneol 11 | DDL 파일 → Flyway `V{n}__*.sql`, 04-api.md → 서비스 협업 창구, `.claude/rules` → `docs/agents/rules`, 문서 검사는 `docs-check.sh` |
| `docs/guides/local-run.md` | haneol 16, kej README 1장 | compose `profiles` 없음(9개 모두 기동), jar 선빌드 불필요(Dockerfile 안에서 빌드), `script/run.sh` 옵션, IDE 병행 시 `POSTGRES_PORT=5432`·`JWT_SECRET` |
| `docs/guides/config-security.md` | haneol 12 S1~S7 | config-repo가 jar에 들어가므로 변경 후 config-server 재빌드 + 서비스 재시작, actuator는 `health,info`, CHANGELOG 대신 PR 본문 |
| `docs/troubleshooting.md` | haneol 15, kej 8장, kmh 07 | 증상 표는 이 저장소에 해당하는 항목만, TS-001~005는 kmh 원문에 "이 저장소" 차이를 덧붙였다 |

## 리뷰 포인트
1. **자동 refresh 설명**: 설정 보안 가이드 5장에 haneol 구현(config-server가 파일 변경을 감지해 각 인스턴스에 `POST /actuator/refresh`)을 설명하고, 팀이 범위에서 뺀 근거(Notion 공통 인프라 범위)를 적었다. Notion에서 "무슨 기능인가" 질문에 답한 내용과 같다.
2. **IDE 병행 시 DB 포트 제약**: `config-repo`의 DB URL이 `localhost:5432`로 고정이고 Config Server 값이 환경 변수보다 우선한다. 그래서 `.env`에서 `POSTGRES_PORT`를 바꾸면 IDE 실행은 DB에 붙지 못한다. 가이드에는 "IDE를 함께 쓸 때는 5432로 둔다"고 적었다.
3. **미확인 표시**: Docker 안 Gateway가 IDE로 띄운 서비스에 닿는지는 실행해 보지 않았다. 가이드에 "미확인, 환경마다 다르다"로 적고 `EUREKA_PREFER_IP=true`를 대안으로 두었다.
4. **TS-004**: kmh는 캐시 마운트 충돌을 `sharing=locked`로 풀었다. 이 저장소 Dockerfile은 빌드 단계를 모든 이미지가 공유해 같은 문제가 생기는 구조가 아니라는 점을 적었다.

## 검증
- 가이드에 적은 명령과 값(`docker-compose.yml`, `.env.example`, `config-repo/*.yml`, `config-server/build.gradle`, `UserHeaders`, `AuthSampleController`, `MasterAccountInitializer` 로그)을 파일에서 확인했다.
- `script/run.sh` 동작은 스텝 31에서 만들고 확인한다.
