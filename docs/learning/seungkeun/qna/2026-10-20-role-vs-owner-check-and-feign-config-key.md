---
title: 역할 검사와 소유자 검사는 어느 계층에서 하고, 같은 서비스를 부르는 Feign 인터페이스를 왜 나누지 않았는가
date: 2026-10-20
updated: 2026-10-20
asker: choiseungkeun
level: 중급
tags: [spring, authorization, openfeign, msa]
---

# 역할 검사와 소유자 검사는 어느 계층에서 하고, 같은 서비스를 부르는 Feign 인터페이스를 왜 나누지 않았는가

## 질문
hub H-06 업체 관리자 입고를 구현하고 정리한 확인 질문 두 가지다.
1. 왜 권한 판단을 컨트롤러가 아니라 서비스에서 하는가?
2. 왜 company I-07용 Feign 클라이언트를 따로 만들지 않고 `ProductClient`에 합쳤는가?

## 짧은 답
- 역할(MASTER/USER)은 요청 헤더만 보면 알 수 있어 컨트롤러(`@RequireRole`)가 맡는다. "이 사용자가 이 상품 업체의 관리자인가"는 company 데이터와 요청 본문의 `productId`가 있어야 알 수 있어 application이 맡는다.
- company-service 타임아웃은 클라이언트 이름 `company-service`로 걸려 있다. 같은 `name`의 인터페이스를 둘로 나누려면 `contextId`가 필요한데, 그러면 설정 키가 달라져 타임아웃이 빠질 수 있다고 판단해 한 인터페이스에 모았다. 이 동작은 직접 실험하지 않았다.

## 자세히
### 1. 역할 검사와 소유자 검사
| 구분 | 필요한 정보 | 위치 |
|---|---|---|
| 역할 | 헤더의 role | 컨트롤러 `@RequireRole`, 인터셉터(`RequireRoleInterceptor.preHandle`)가 본문을 읽기 전에 판단 |
| 소유자·담당자 | 리소스의 주인(`Company.managerId`), 요청 본문의 `productId` | application에서 `UserContext` 헬퍼나 직접 비교 |

- `productId`는 요청 본문에 있어 인터셉터 시점에는 모른다. 인터셉터에서 company를 호출하려면 본문을 미리 읽어야 해서 부적절하다.
- 서비스에서 판단하면 외부 호출을 포함한 흐름이 한 곳에 모이고, mock을 이용한 단위 테스트로 허용·거절 경우를 모두 확인할 수 있다.
- `@RequireRole`을 없애면 로그인한 모든 사용자가 서비스까지 도달한다. 그래서 서비스는 MASTER가 아니면 기본 거부하고(업체 관리자임이 확인될 때만 허용), 헤더가 없으면 `@CurrentUser`가 401을 낸다.

### 2. 같은 서비스를 부르는 Feign 인터페이스
- 설정은 `spring.cloud.openfeign.client.config.<클라이언트 이름>`으로 건다. 이 프로젝트의 hub는 `company-service`에 연결 1초·읽기 2초를 걸어 두었다(`config-repo/hub-service.yml`).
- 같은 `name`을 쓰는 인터페이스가 둘이면 빈 이름이 겹쳐 `contextId`가 필요하다. `contextId`를 주면 클라이언트 이름이 `contextId`가 되어 위 설정 키와 달라진다고 알고 있어 합쳤다.
- 이 판단은 설정 구조를 근거로 한 것이고 이 프로젝트에서 실험으로 확인하지 않았다. 확인 방법: `contextId`를 준 클라이언트로 `ProductClientTimeoutIntegrationTest`와 같은 테스트를 돌려 2초에 끊기는지 본다.
- 대안은 클라이언트마다 같은 타임아웃 설정을 복제하거나 default 설정을 쓰는 것이다. 복제는 한쪽만 고쳐지기 쉽다.
- 감수한 점: `ProductClient`가 이제 상품만 부르지 않는다. 이름을 `CompanyServiceClient`처럼 바꾸는 것이 더 정확하다.

## 이 프로젝트에서는
- `hub-service/src/main/java/com/sparta/logistics/hub/application/StockRegistrationService.java`의 `inbound`, `validateSupplierManager`
- `hub-service/src/main/java/com/sparta/logistics/hub/presentation/HubStockController.java`
- `hub-service/src/main/java/com/sparta/logistics/hub/infrastructure/client/ProductClient.java`
- 근거와 감수한 점은 `docs/services/hub/decisions/0003-stock-allocation-supplier-hub-only.md`의 입고 권한 절에 있다.

## 면접 포인트
- **Q. 역할 검사와 소유자 검사를 왜 계층을 나누나요?** 역할은 요청만 보고 판단하므로 앞단에서 일찍 거르고, 소유자 검사는 도메인 데이터가 필요해 비즈니스 계층에서 합니다.
- **Q. 컨트롤러 역할 제한을 풀면 보안이 약해지지 않나요?** 서비스가 기본 거부로 판단하고 테스트로 허용·거절을 고정합니다. 기본이 허용이 되지 않게 하는 것이 핵심입니다.
- **Q. Feign 설정이 안 먹을 때 무엇을 보나요?** 클라이언트 이름(`name`, `contextId`)과 설정 키가 같은지, 그리고 실제로 응답 없는 서버로 호출해 타임아웃을 측정해 봅니다.

## 참고
- `common/src/main/java/com/sparta/logistics/common/auth/RequireRoleInterceptor.java`
- `hub-service/src/test/java/com/sparta/logistics/hub/integration/ProductClientTimeoutIntegrationTest.java`
