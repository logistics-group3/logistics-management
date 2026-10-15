# 스텝 19. MASTER 계정 자동 생성

- 날짜: 2026-10-14
- 근거: Notion "main 통합 방안" 6장 관리자 계정 "하나 만들어지게 하시죠 자동으로". 방식은 KMH 브랜치 `MasterAccountInitializer`(빈 값 건너뛰기, 동시 기동 처리)를 따랐다.
- 결과: user-service 테스트 23개 통과(새 테스트: 단위 5개, 통합 1개). 컨테이너 확인은 스텝 21 E2E에서 한다.

## 바꾼 것
| 파일 | 내용 |
|---|---|
| `User.master(username, encodedPassword, slackId)` | MASTER로 시작하는 유일한 생성 경로. 이름 "마스터", 소속 HUB "스파르타 물류 본사" |
| `infrastructure/init/MasterAccountInitializer` | 기동 시 `ApplicationRunner`로 실행. 빈 값, username 규칙 위반, 이미 있는 username이면 건너뛴다 |
| `infrastructure/init/MasterAccountProperties` | `master.username`, `master.password`, `master.slack-id`(기본 U0MASTER) |
| `../../../../config-repo/user-service.yml` | `master.*`를 `${MASTER_USERNAME:}`, `${MASTER_PASSWORD:}`로 받는다 |
| `../../../../docker-compose.yml`, `.env.example` | `MASTER_USERNAME=master`, `MASTER_PASSWORD=Master!2026` |
| `../../../../script/e2e-check.sh` | [4]의 MASTER 확인을 "가입 후 승격"에서 "기동 시 만든 MASTER 로그인"으로 바꿨다. `.env`는 셸로 실행하지 않고 KEY=VALUE만 읽는다 |
| `../../../../README.md` | MASTER 계정 안내 |

`../../../../script/promote-master.sh`는 남겨 둔다. 가입한 계정을 승격할 때와 E2E의 60초 권한 캐시 확인에 쓴다.

## 리뷰 포인트
1. **메서드에 `@Transactional`을 걸지 않은 이유**: 걸면 `saveAndFlush`가 유니크 제약 위반으로 실패할 때 바깥 트랜잭션이 rollback-only로 표시된다. 예외를 잡고 정상 종료해도 커밋 시점에 `UnexpectedRollbackException`이 난다. 리포지토리 메서드가 각자 트랜잭션을 연다.
2. **존재 확인과 저장 사이의 경쟁**: user-service를 2개 띄우면 둘 다 "없음"을 보고 동시에 INSERT할 수 있다. username 유니크 제약이 하나만 통과시키고, 진 쪽은 예외를 무시하고 기동을 계속한다.
3. **기존 계정은 승격하지 않는다**: 같은 username의 USER가 이미 있을 때 자동으로 MASTER로 바꾸면, 누군가 먼저 그 username으로 가입해 관리자 권한을 얻을 수 있다. 그래서 건너뛰기만 한다.
4. **Audit 값**: 요청 밖에서 저장하므로 `created_by`는 `SYSTEM`이다(통합 테스트로 확인).

## 팀원에게 알릴 것
- 기존 `.env`에는 `MASTER_USERNAME`, `MASTER_PASSWORD`가 없다. `.env.example`에서 두 줄을 복사한다. 없으면 MASTER를 만들지 않고 E2E [4]의 MASTER 확인이 실패한다.
