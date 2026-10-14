# 스텝 14. Eureka 로컬 튜닝

- 날짜: 2026-10-14
- 근거: Notion "팀 합의 대조·추가 검토" 2장. 네 명(haneol, KEJ, KMH, seungkeun)이 모두 자기 보호 모드를 껐다. 주기 값은 KMH 브랜치 값을 따랐다.
- 결과: 런타임 확인은 스텝 21 전체 검증에서 한다(서비스를 내린 뒤 목록에서 사라지기까지 걸린 시간 측정).

## 바꾼 것
| 파일 | 설정 | 기본값 → 변경값 |
|---|---|---|
| `eureka-server/.../application.yml` | `eureka.server.enable-self-preservation` | true → false |
| 같은 파일 | `eureka.server.eviction-interval-timer-in-ms` | 60000 → 5000 |
| 같은 파일 | `eureka.server.wait-time-in-ms-when-sync-empty` | 300000 → 0 (KEJ) |
| `config-repo/application.yml` | `eureka.client.registry-fetch-interval-seconds` | 30 → 5 |
| 같은 파일 | `eureka.instance.lease-renewal-interval-in-seconds` | 30 → 5 |
| 같은 파일 | `eureka.instance.lease-expiration-duration-in-seconds` | 90 → 15 |

## 리뷰 포인트
1. **인스턴스가 목록에서 빠지기까지**: 마지막 갱신 후 만료 시간(15초)이 지나고, 다음 정리 주기(최대 5초)에 지워진다. 그 뒤 Gateway가 목록을 다시 받아 오기까지 최대 5초가 더 걸린다. 기본값이면 이 합이 몇 분 단위가 된다.
2. **자기 보호 모드를 운영에서 켜 두는 이유**: 네트워크가 잠깐 끊겨 갱신 신호가 한꺼번에 사라지면, 실제로 살아 있는 서비스를 대량으로 지울 수 있다. 로컬은 서비스를 자주 내리고 올리므로 오히려 방해가 된다.
3. **남은 지연**: Spring Cloud LoadBalancer도 인스턴스 목록을 캐시한다(기본 35초). 이번에는 팀원 브랜치와 같게 Eureka 값만 바꿨다.

## 팀원에게 알릴 것
- 운영 배포를 하게 되면 이 값들을 기본값으로 되돌릴지 다시 판단한다. 지금 값은 로컬 개발용이다.
