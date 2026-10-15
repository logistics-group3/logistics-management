# 스텝 14. Eureka 로컬 튜닝

- 날짜: 2026-10-14
- 근거: Notion "팀 합의 대조·추가 검토" 2장. 네 명(haneol, KEJ, KMH, seungkeun)이 모두 자기 보호 모드를 껐다. 주기 값은 KMH 브랜치 값을 따랐다.
- 결과(스텝 21에서 측정, 별도 Compose 프로젝트): 정상 종료한 hub-service는 0초 만에 UP 목록에서 빠졌다. 강제 종료(`docker compose kill`)는 17초 뒤 빠졌다. 응답 캐시를 끄기 전에는 강제 종료가 60초 걸렸다.

## 바꾼 것
| 파일 | 설정 | 기본값 → 변경값 |
|---|---|---|
| `eureka-server/.../application.yml` | `eureka.server.enable-self-preservation` | true → false |
| 같은 파일 | `eureka.server.eviction-interval-timer-in-ms` | 60000 → 5000 |
| 같은 파일 | `eureka.server.wait-time-in-ms-when-sync-empty` | 300000 → 0 (KEJ) |
| 같은 파일 | `eureka.server.use-read-only-response-cache` | true → false (측정 후 추가) |
| `../../../../config-repo/application.yml` | `eureka.client.registry-fetch-interval-seconds` | 30 → 5 |
| 같은 파일 | `eureka.instance.lease-renewal-interval-in-seconds` | 30 → 5 |
| 같은 파일 | `eureka.instance.lease-expiration-duration-in-seconds` | 90 → 15 |

## 리뷰 포인트
1. **인스턴스가 목록에서 빠지기까지**: 마지막 갱신 후 만료 시간(15초)이 지나고, 다음 정리 주기(최대 5초)에 지워진다. 그 뒤 Gateway가 목록을 다시 받아 오기까지 최대 5초가 더 걸린다. 기본값이면 이 합이 몇 분 단위가 된다.
2. **자기 보호 모드를 운영에서 켜 두는 이유**: 네트워크가 잠깐 끊겨 갱신 신호가 한꺼번에 사라지면, 실제로 살아 있는 서비스를 대량으로 지울 수 있다. 로컬은 서비스를 자주 내리고 올리므로 오히려 방해가 된다.
3. **응답 캐시**: Eureka 서버는 목록 조회 응답을 읽기 전용 캐시에 담아 30초마다 갱신한다. 그래서 인스턴스를 지워도 다음 갱신 전까지는 목록에 남아 보였다. 측정해 보니 만료·정리 주기를 줄여도 강제 종료가 60초 걸렸고, 캐시를 끈 뒤 17초로 줄었다. 팀원 브랜치에는 없던 설정이다.
4. **정상 종료와 강제 종료**: 정상 종료하면 서비스가 스스로 DOWN을 알리고 등록을 해제하므로 바로 빠진다. 강제 종료나 장애는 갱신 신호가 끊긴 뒤 만료(15초)와 정리 주기(최대 5초)를 기다린다. Gateway는 UP 인스턴스만 쓴다.
5. **남은 지연**: Spring Cloud LoadBalancer도 인스턴스 목록을 캐시한다(기본 35초). 이번에는 팀원 브랜치와 같게 Eureka 값만 바꿨다.

## 팀원에게 알릴 것
- 운영 배포를 하게 되면 이 값들을 기본값으로 되돌릴지 다시 판단한다. 지금 값은 로컬 개발용이다.
