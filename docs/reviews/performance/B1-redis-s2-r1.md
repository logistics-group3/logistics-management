# 성능 측정 결과: B1-redis-s2-r1

## 조건
| 항목 | 값 |
|---|---|
| 시나리오 | s2 |
| 커밋 | 1a0056d |
| 목표 도착률 | 60/s |
| 워밍업 / 본 측정 | 1m / 5m |
| 캐시 설정 | auth=local/60s hub=redis company=none |
| 추적 샘플링 비율 | 0.0 |
| 집계 구간 끝(UTC) | 2026-10-20T14:18:32.000Z |

## 사용자 체감 (k6, 본 측정 구간)
| 지표 | 값 |
|---|---|
| 완료 반복 수 | 18001 (60.00/s) |
| 처리하지 못한 반복(dropped) | 0 |
| 실패율 | 0.00% |
| 응답 시간 p50 / p95 / p99 | 11.7 / 14.7 / 17.1 ms |
| 응답 시간 평균 / 최대 | 12.3 / 100.3 ms |

## 항목별 주 지표 (요청 1건당)
| 항목 | 값 | 계산 |
|---|---|---|
| A. Gateway I-01 | 0.08 (1406회) | user-service가 받은 I-01 19408 − 다른 서비스의 Feign I-01 18001 |
| B. I-03 허브 경로 | 0.94 (16903회), hub-service API p95 1.3 ms | delivery → hub Feign, hub http_server_requests(가장 느린 URI) |
| C. order → company | 3.00 (54004회), company 내부 API p95 1.0 ms | order Feign, company http_server_requests |

## 서비스가 받은 요청 (Prometheus http_server_requests)
| 서비스 | 메서드 | URI | 상태 | 횟수 | p95(ms) |
|---|---|---|---|---|---|
| company-service | GET | /internal/companies/{companyId} | 200 | 36003 | 1.0 |
| company-service | GET | /internal/products/{productId} | 200 | 18001 | 1.0 |
| delivery-service | POST | /internal/deliveries | 201 | 18001 | 21.9 |
| gateway-service | POST | UNKNOWN | 202 | 18001 | 14.4 |
| hub-service | GET | /internal/hub-routes | 200 | 16913 | 1.3 |
| order-service | POST | /api/v1/orders | 202 | 18001 | 11.2 |
| user-service | GET | /internal/users/{userId} | 200 | 19408 | 1.1 |
| user-service | GET | /internal/users/delivery-managers | 200 | 34912 | 1.4 |

## 서비스 간 호출 (Prometheus feign_client_requests)
| 호출한 서비스 | 클라이언트 | 메서드 | URL | 상태 | 횟수 | p95(ms) |
|---|---|---|---|---|---|---|
| delivery-service | com.sparta.logistics.delivery.infrastructure.client.UserClient | GET | /{userId} | 200 | 18001 | 1.7 |
| delivery-service | com.sparta.logistics.delivery.infrastructure.client.UserClient | GET | /delivery-managers?hubId={hubId} | 200 | 34904 | 1.9 |
| delivery-service | com.sparta.logistics.delivery.infrastructure.client.HubClient | GET | /hub-routes?departureHubId={departureHubId}&arrivalHubId={arrivalHubId} | 200 | 16903 | 1.7 |
| order-service | com.sparta.logistics.order.infrastructure.client.CompanyClient | GET | /companies/{companyId} | 200 | 36003 | 1.7 |
| order-service | com.sparta.logistics.order.infrastructure.client.DeliveryCreateClient | POST | /deliveries | 201 | 18001 | 22.2 |
| order-service | com.sparta.logistics.order.infrastructure.client.CompanyClient | GET | /products/{productId} | 200 | 18001 | 1.4 |

## Gateway → 서비스 라우팅 (http_client_requests)
| 라우트 | 메서드 | 상태 | 횟수 | p95(ms) |
|---|---|---|---|---|
| order-service | POST | 202 | 18001 | 12.2 |

## 자원
| 서비스 | CPU 평균(%) | DB 커넥션 사용 최대 | DB 커넥션 대기 최대 |
|---|---|---|---|
| company-service | 15.8 | 0 | 0 |
| delivery-service | 38.6 | 1 | 0 |
| gateway-service | 17.9 | - | - |
| hub-service | 8.3 | 0 | 0 |
| order-service | 32.9 | 2 | 0 |
| user-service | 19.8 | 1 | 0 |

Prometheus 수집 주기가 15초라 횟수는 근사값이다. 같은 조건의 회차끼리 비교한다.
