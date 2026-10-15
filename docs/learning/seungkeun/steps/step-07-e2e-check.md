# 스텝 7. 통합 검증 스크립트

- 날짜: 2026-10-13
- 결과: DB 볼륨을 지운 빈 상태에서 `script/e2e-check.sh --up` 한 번으로 빌드, 기동, 검증까지 64초, PASS 11 / FAIL 0

## 만든 것
| 파일 | 역할 |
|---|---|
| `../../../../script/e2e-check.sh` | 완료 기준 6개를 11개 항목으로 확인. `--up`이면 `.env` 준비, bootJar, compose 기동까지 한다 |
| `../../../../script/promote-master.sh` | 로컬에서 가입한 사용자를 MASTER로 올린다(SQL) |
| `../../../../README.md` | 빠른 실행, 모듈 구성, 문서 위치 |

## 검증 항목
| 기준 | 항목 |
|---|---|
| 1 | Eureka에 서비스 6개 등록 |
| 2 | 회원가입 201, 로그인 후 토큰 발급 |
| 3 | `@CurrentUser`로 받은 username이 가입한 사용자와 같음 |
| 4 | 토큰 없음 401, 위조 토큰 401, USER → MASTER 전용 403, MASTER → MASTER 전용 200 |
| 5 | `/internal/**` 404 |
| 6 | 위조 role 헤더로 MASTER 전용 403, 위조 username·role 헤더가 서비스에 전달되지 않음 |

## 리뷰 포인트
1. **준비 확인은 실제 경로로**: Eureka 등록 수만 보면 Gateway가 아직 목록을 못 받아 503이 나는 구간이 있다. 그래서 user-service를 실제로 거치는 로그인 요청이 401(인증 실패 = 라우팅 성공)을 돌려줄 때까지 기다린다.
2. **매번 새 사용자**: 실행 시각으로 username을 만들어서 여러 번 돌려도 중복 가입 409가 나지 않는다.
3. **MASTER는 가입 후 승격**: 회원가입은 항상 USER다(SA U-01). MASTER가 되는 API(U-07)는 이번 범위 밖이라 SQL로 올린다. 승격한 뒤에 로그인해야 토큰의 role과 저장된 role이 일치한다.
4. **실패 시 종료 코드 1**: CI에서 그대로 쓸 수 있다.

## 리뷰 시연 순서 (10/14)
```bash
docker compose down -v
script/e2e-check.sh --up
```
