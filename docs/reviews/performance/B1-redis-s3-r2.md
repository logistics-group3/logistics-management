# 성능 측정 결과: B1-redis-s3-r2

## 조건
| 항목 | 값 |
|---|---|
| 시나리오 | s3 |
| 커밋 | 046e81b |
| 목표 도착률 | 200/s |
| 워밍업 / 본 측정 | 1m / 5m |
| 캐시 설정 | auth=local/60s hub=redis company=none |
| 추적 샘플링 비율 | 0.0 |
| 집계 구간 끝(UTC) | 2026-10-20T14:50:32.000Z |

## 사용자 체감 (k6, 본 측정 구간)
| 지표 | 값 |
|---|---|
| 완료 반복 수 | 60001 (200.00/s) |
| 처리하지 못한 반복(dropped) | 0 |
| 실패율 | 0.00% |
| 응답 시간 p50 / p95 / p99 | 2.6 / 3.2 / 9.4 ms |
| 응답 시간 평균 / 최대 | 3.1 / 117.4 ms |

## 항목별 주 지표 (요청 1건당)
| 항목 | 값 | 계산 |
|---|---|---|
| A. Gateway I-01 | 0.00 (211회) | user-service가 받은 I-01 211 − 다른 서비스의 Feign I-01 0 |
| B. I-03 허브 경로 | 0.00 (0회), hub-service API p95 1.0 ms | delivery → hub Feign, hub http_server_requests(가장 느린 URI) |
| C. order → company | 0.00 (0회), company 내부 API p95 - ms | order Feign, company http_server_requests |

## 서비스가 받은 요청 (Prometheus http_server_requests)
| 서비스 | 메서드 | URI | 상태 | 횟수 | p95(ms) |
|---|---|---|---|---|---|
| gateway-service | GET | UNKNOWN | 200 | 60003 | 2.9 |
| hub-service | GET | /api/v1/hub-routes/{hubRouteId} | 200 | 29883 | 1.0 |
| hub-service | GET | /api/v1/hubs/{hubId} | 200 | 30119 | 1.0 |
| user-service | GET | /internal/users/{userId} | 200 | 211 | 1.4 |

## 서비스 간 호출 (Prometheus feign_client_requests)
_데이터 없음_

## Gateway → 서비스 라우팅 (http_client_requests)
| 라우트 | 메서드 | 상태 | 횟수 | p95(ms) |
|---|---|---|---|---|
| hub-service | GET | 200 | 60003 | 1.6 |

## 자원
| 서비스 | CPU 평균(%) | DB 커넥션 사용 최대 | DB 커넥션 대기 최대 |
|---|---|---|---|
| company-service | 0.9 | 0 | 0 |
| delivery-service | 1.2 | 0 | 0 |
| gateway-service | 35.4 | - | - |
| hub-service | 15.1 | 0 | 0 |
| order-service | 1.1 | 0 | 0 |
| user-service | 1.0 | 0 | 0 |

Prometheus 수집 주기가 15초라 횟수는 근사값이다. 같은 조건의 회차끼리 비교한다.
