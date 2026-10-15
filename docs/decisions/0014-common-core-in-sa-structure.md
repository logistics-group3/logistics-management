# 0014. SA 프로젝트 구조에 common-core 모듈 반영

- 상태: 제안
- 날짜: 2026-10-15
- 결정자: 팀 (company 담당 kej-2356 제기)

## 맥락
- 계획
  - SA v2.0 "프로젝트 구조 (Gradle 멀티 모듈)"와 `docs/progress/공통 인프라 개발 범위.md`의 범위 표에는 공통 모듈이 `common/` 하나뿐이다.
  - `ApiResponse`, `ErrorCode`도 `common/` 안에 있다.
- 구현
  - 공통 인프라 구현 중에 `common-core`를 따로 분리했다([0006](0006-common-core-module.md), 통합 채택 [0008](0008-base-setting-integration.md)).
  - `common-core`에 들어 있는 것: `ErrorCode`, `CommonErrorCode`, `SimpleErrorCode`, `BusinessException`, `ErrorResponse`, `ApiResponse`, `UserHeaders`, `Role`
  - 분리한 이유: Gateway(WebFlux)는 MVC 기반 `common`에 의존할 수 없는데, 에러 응답 형식과 사용자 헤더 이름은 서비스와 같아야 한다.
- 영향
  - 패키지 이름이 `com.sparta.logistics.common.*` 그대로이고 `common`이 `common-core`를 `api`로 노출한다.
  - 그래서 서비스 코드와 import는 계획서대로 `common`을 쓰는 것과 같다.
  - 다만 SA 구조 그림만 보면 `common-core`의 존재와 역할을 알 수 없다.

## 결정
- `common-core` 분리를 팀 결정으로 확인한다. [통합 리뷰](../reviews/base-setting-integration.md)의 "main 반영 전 리뷰" 1번 항목과 같다.
- 확인하면 SA 프로젝트 구조 그림에 `common-core`와 그 역할을 추가한다.

## 검토한 대안
- SA를 그대로 두기: 코드에는 영향이 없지만, SA만 보는 사람은 `common-core`가 왜 있는지 알 수 없다.
- `common` 하나로 다시 합치기: Gateway가 MVC·JPA를 함께 끌어오게 되어 0006의 문제로 돌아간다.

## 결과
- 코드 변경은 없다.
- 채택되면 SA 프로젝트 구조 그림을 고치는 후속 작업이 생긴다.
