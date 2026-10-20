# 성능 측정 결과: BC0-s3-r1

## 조건
| 항목 | 값 |
|---|---|
| 시나리오 | s3 |
| 커밋 | a9a6630 |
| 목표 도착률 | 200/s |
| 워밍업 / 본 측정 | 1m / 5m |
| 캐시 설정 | auth=local/60s hub=none company=none |
| 추적 샘플링 비율 | 0.0 |
| 집계 구간 끝(UTC) | 2026-10-19T16:23:11.000Z |

## 사용자 체감 (k6, 본 측정 구간)
| 지표 | 값 |
|---|---|
| 완료 반복 수 | 60008 (200.03/s) |
| 처리하지 못한 반복(dropped) | 0 |
| 실패율 | 0.00% |
| 응답 시간 p50 / p95 / p99 | 3.2 / 4.1 / 20.4 ms |
| 응답 시간 평균 / 최대 | 4.0 / 102.7 ms |

## 항목별 주 지표 (요청 1건당)
| 항목 | 값 | 계산 |
|---|---|---|
| A. Gateway I-01 | 0.00 (217회) | user-service가 받은 I-01 217 − 다른 서비스의 Feign I-01 0 |
| B. I-03 허브 경로 | 0.00 (0회), hub-service API p95 1.6 ms | delivery → hub Feign, hub http_server_requests(가장 느린 URI) |
| C. order → company | 0.00 (0회), company 내부 API p95 - ms | order Feign, company http_server_requests |

## 서비스가 받은 요청 (Prometheus http_server_requests)
| 서비스 | 메서드 | URI | 상태 | 횟수 | p95(ms) |
|---|---|---|---|---|---|
| gateway-service | GET | UNKNOWN | 200 | 60003 | 3.7 |
| hub-service | GET | /api/v1/hub-routes/{hubRouteId} | 200 | 29918 | 1.6 |
| hub-service | GET | /api/v1/hubs/{hubId} | 200 | 30086 | 1.3 |
| user-service | GET | /internal/users/{userId} | 200 | 217 | 1.4 |

## 서비스 간 호출 (Prometheus feign_client_requests)
_데이터 없음_

## Gateway → 서비스 라우팅 (http_client_requests)
| 라우트 | 메서드 | 상태 | 횟수 | p95(ms) |
|---|---|---|---|---|
| hub-service | GET | 200 | 60004 | 2.2 |

## 자원
| 서비스 | CPU 평균(%) | DB 커넥션 사용 최대 | DB 커넥션 대기 최대 |
|---|---|---|---|
| company-service | 1.1 | 0 | 0 |
| delivery-service | 1.7 | 0 | 0 |
| gateway-service | 39.3 | - | - |
| hub-service | 20.4 | 1 | 0 |
| order-service | 1.1 | 0 | 0 |
| user-service | 1.2 | 0 | 0 |

Prometheus 수집 주기가 15초라 횟수는 근사값이다. 같은 조건의 회차끼리 비교한다.
