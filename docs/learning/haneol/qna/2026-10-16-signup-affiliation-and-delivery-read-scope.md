---
title: 회원가입 소속과 업체 관리자·배송담당자는 어떻게 정해지고, 배송 조회 권한은 어디서 판단하나
date: 2026-10-16
updated: 2026-10-16
asker: jihaneol
level: 중급
tags: [auth, jwt, gateway, authorization, msa, data-ownership]
---

# 회원가입 소속과 업체 관리자·배송담당자는 어떻게 정해지고, 배송 조회 권한은 어디서 판단하나

## 질문
회원가입할 때 소속 업체는 어떻게 흐르나? 업체 담당자는 한 명인가? 기본 USER는 배송을 못 보나? 업체 관리자인지 왜 company에 물어봐야 하나?

## 짧은 답
- 회원가입은 항상 `USER`다. 소속명(`affiliationName`)은 화면 표시용 자유 텍스트이고 업체·허브와 연결되지 않는다.
- 실제 역할은 가입 뒤에 따로 생긴다. 업체 관리자는 `p_companies.manager_id`(업체당 1명, 한 사람이 여러 업체 가능), 배송담당자는 MASTER가 U-07로 지정하는 `is_delivery_manager` + `hub_id`다.
- 권한은 `MASTER`/`USER` 2단계이고 JWT에는 ID·username·role만 있다. Gateway가 요청마다 user에서 role과 배송담당자 여부를 다시 확인해 `X-User-*` 헤더로 넣는다.
- 배송담당자 여부는 헤더로 오지만 "어느 업체의 관리자인가"는 company 테이블에만 있다. 그래서 배송 조회 범위를 업체로 좁히려면 company에 물어야 한다.

## 자세히

### 1. 회원가입 (U-01)
| 입력 | 저장 |
|---|---|
| username, password, name | 그대로(비밀번호는 BCrypt) |
| `affiliationType`(COMPANY/HUB), `affiliationName` | 그대로. 업체 테이블을 찾거나 확인하지 않는다 |
| `slackEmail` | 저장. `slackId`는 null |
| role | 항상 `USER` |
| 배송담당자 여부, `hub_id` | `false`, 없음 |

그래서 가입 때 쓴 소속명과 실제 담당 업체 이름이 다를 수 있다. 권한 판단에는 소속명을 쓰지 않으므로 권한에는 영향이 없고, 화면 표시만 어긋날 수 있다.

### 2. 역할이 생기는 곳
| 역할 | 어떻게 | 코드 |
|---|---|---|
| 업체 관리자 | USER가 업체를 만들면 본인, MASTER가 만들면 `managerId` 필수 | `CompanyService.resolveManager` |
| 배송담당자 | MASTER가 U-07로 `isDeliveryManager=true`, `hubId` 지정. 소속도 HUB·허브 이름으로 덮어쓴다 | `UserService.applyRoleChange`, `User.assignDeliveryManager` |
| MASTER | 기동 시 `.env` 값으로 생성 또는 `script/promote-master.sh` | `MasterAccountInitializer` |

업체 → 담당자는 정확히 1명(`manager_id` 하나, NOT NULL)이고, 사람 → 담당 업체는 0개 이상이다(`manager_id`에 유니크 제약 없음).

### 3. 로그인 후 요청이 서비스까지 가는 흐름
```
로그인 → JWT(sub=userId, username, role)
요청 → Gateway
  ① JWT 서명·만료 확인
  ② user I-01로 현재 role·탈퇴 여부 재확인(60초 캐시). 토큰 role과 다르면 401 TOKEN_ROLE_MISMATCH
  ③ 클라이언트가 보낸 X-User-* 헤더 제거(위조 방지)
  ④ X-User-Id, X-Username, X-User-Role, X-Delivery-Manager 주입
서비스 → @CurrentUser UserContext로 읽고 API별 권한 판단
```

### 4. 업체 관리자 확인을 company에 묻는 이유
| | 배송담당자 | 업체 관리자 |
|---|---|---|
| 저장 위치 | user (`is_delivery_manager`) | company (`manager_id`) |
| 헤더 | `X-Delivery-Manager` | 없음 |
| 범위에 필요한 값 | 내 userId(구간 `manager_id`와 비교) | 내가 관리하는 업체 ID 목록(배송의 공급·요청업체와 비교) |

필요한 것은 "업체 관리자인가"가 아니라 "어느 업체들인가"이고, 그 목록은 company만 가진다. 다른 방법도 있다.
- Gateway가 company를 조회해 `X-Managed-Company-Ids` 헤더로 넣기: 서비스는 원격 호출이 없지만 Gateway가 company에 의존한다.
- user에 복사: 지정·변경 때마다 동기화가 필요하고 어긋날 수 있다.
- 배송 생성 때 관리자 ID를 배송에 복사: 관리자가 바뀌면(`Company.assignManager`) 범위가 틀린다.

데이터를 가진 쪽에 묻는 방식이 항상 정확하다. 대가는 원격 호출 1번과 company에 대한 의존이다. 캐시와 장애 대응으로 줄인다.

## 이 프로젝트에서는
- SA는 D-01·D-02를 "로그인(배송담당자는 본인 담당만)"으로 정해, 기본 USER도 모든 업체의 배송 주소·수령인을 볼 수 있었다. 2026-10-16에 범위를 바꿨다: MASTER 전체 / 배송담당자 담당 구간 / 업체 관리자 자기 업체 / 그 외 빈 목록·403. 결정 로그 21, `docs/services/delivery/decisions/0004-delivery-read-scope.md`.
- 업체 관리자 확인은 company I-17(`GET /internal/companies?managerId=`)로 한다. 아직 company에 없어 COMPANY-002로 요청했다.
- 코드: `DeliveryQueryService`(MASTER·담당 구간 확인을 먼저 하고 필요할 때만 company 호출), `DeliveryScope`, `DeliveryQueryRepositoryImpl.within`.

## 면접 포인트
- "권한 정보를 JWT에 다 넣으면 안 되나?" → 토큰은 만료 전까지 바꿀 수 없어 권한 변경·탈퇴가 늦게 반영된다. 그래서 role만 넣고 Gateway가 짧은 캐시로 다시 확인한다. 업체 관리 관계처럼 자주 바뀔 수 있고 다른 서비스가 가진 데이터는 토큰에 넣지 않는다.
- "MSA에서 다른 서비스 데이터로 권한을 판단할 때는?" → 데이터 소유 서비스에 내부 API로 묻는다. 복사하면 정합성이 깨질 수 있다. 호출이 늘면 캐시(TTL만큼의 지연을 받아들임)나 Gateway 헤더 주입을 검토한다.
- "권한 없는 사용자의 목록 조회는 403인가 빈 목록인가?" → 단건은 403, 목록은 빈 결과가 자연스럽다. 목록은 "볼 수 있는 것 중 조건에 맞는 것"이라 없을 수도 있는 결과이기 때문이다.

## 참고
- 확인한 코드(2026-10-16): `AuthService.signup`, `JwtProvider`, `UserVerifyingJwtConverter`, `UserHeaderInjectionFilter`, `CompanyService.resolveManager`, `V1__create_p_companies.sql`, `OrderService.create`
- `./gradlew :delivery-service:test` 54개 통과(2026-10-16, 사용자 맥)

> 2026-10-19 추가: 위 범위 변경은 PR에 넣지 않았다. 팀이 dev 결정 로그 22(#12)로 "일반 USER 배송 전체 조회 유지"를 먼저 정했고, 사용자도 그 결정을 따르기로 했다. 아래쪽의 업체 관리자 조회 호출과 범위 코드는 `backup/delivery-T06-before-rebase` 브랜치에만 있다. 본문의 "결정 로그 21"은 rebase 전 번호다.
