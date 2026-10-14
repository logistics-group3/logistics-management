# 스텝 21. 새로 시작 안내와 전체 검증

- 날짜: 2026-10-14
- 근거: Notion "main 통합 방안" 6장 기존 DB 이행 "통합 후 새로 시작할 수 있도록"
- 결과: 아래 표

## 바꾼 것
| 파일 | 내용 |
|---|---|
| `README.md` | 통합 후 첫 실행은 `docker compose down -v`로 DB를 새로 시작한다는 안내 |
| `config-repo/application.yml` | 주석의 `init.sql`을 실제 파일 `infra/postgres/init.sh`로 수정 |
| `docs/reviews/base-setting-integration-pr.md` | PR 본문(STAR 형식) |

## 확인한 것
| 검사 | 결과 |
|---|---|
| `./gradlew clean build` | 성공. 테스트 98개(common 48, gateway 24, user 23, common-core 2, config-server 1), 실패·오류·skip 0 |
| `script/e2e-check.sh --up --with-cache-ttl` | 새 볼륨, 별도 Compose 프로젝트(`logistics-pra-20261014`, 포트 25432/28888/28761/28080)에서 PASS 18 / FAIL 0 |
| Eureka 설정을 바꾼 뒤 `script/e2e-check.sh` | PASS 15 / FAIL 0 |
| `script/db-check.sh` | 계정 5개 PASS |
| `script/error-codes-check.sh` | PASS, 코드 15개 |
| user-service 기동 로그 | `MASTER 계정을 만들었다: master` |
| Eureka 등록 해제 | 정상 종료 0초, 강제 종료 17초(스텝 14) |
| `git ls-files --eol` | `gradlew`, `*.sh`가 `eol=lf` |

## 리뷰 포인트
1. **E2E를 별도 Compose 프로젝트로 돌린 이유**: 기존 개발 환경(같은 PC의 컨테이너 9개와 DB 볼륨)을 건드리지 않고 빈 DB에서 처음부터 확인하기 위해서다. 프로젝트 이름과 호스트 포트만 `.env`로 바꾸면 된다(README "기존 환경과 병행하기").
2. **측정하다 찾은 것**: 강제 종료한 서비스가 Eureka 목록에서 빠지는 데 60초가 걸렸다. 원인은 서버 응답 캐시였고, 꺼서 17초로 줄였다(스텝 14).

## 확인하지 못한 것
- Windows에서 clone·실행, 팀원별 IDE import
