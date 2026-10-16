# 0015. 실제 Gateway와 연결하는 물류 운영 테스트 UI

- 상태: 제안 (사용자가 이 브랜치의 구현·실행을 요청함, 팀 채택은 PR 리뷰 대상)
- 날짜: 2026-10-15
- 결정자: Junhyun An 작업

## 맥락

주문 O-01~O-06이 dev에 병합됐다. 사용자는 실제 물류 사이트 형태의 전체 메뉴와 주문·배송 우선 테스트 UI, 실행된 서버·화면을 요청했다.
기존 저장소에는 프런트엔드가 없고 실제 개발 DB·Slack Webhook을 가진 실행 환경이 있다. UI 검증이 기존 환경을 변경하지 않아야 한다.

## 결정

1. 독립된 `frontend/` npm 프로젝트를 추가한다. React·TypeScript·Vite, React Router, TanStack Query를 사용한다. Tailwind CSS와 직접 관리하는 Radix Dialog·Slot/CVA 버튼으로 화면을 구성한다. Gradle 모듈·서비스 API 계약은 변경하지 않는다.
2. 브라우저는 Vite의 `/api` 프록시를 통해 Gateway만 호출한다. JWT는 메모리에 보관하고 로그아웃·401 때 인증과 조회 캐시를 정리한다.
3. 주문 생성 키와 원문은 호출 전에 사용자별 sessionStorage에 보존한다. 네트워크 실패·5xx·처리 중은 같은 키로 확인하고, 복구 대기·키 충돌은 새 요청으로 우회하지 않는다. 탭 종료 뒤 복구와 자동 복구는 보장하지 않는다.
4. Java long 금액은 `lossless-json`으로 문자열을 유지하고 BigInt로 계산·표시한다. 요청 수량은 서버 int 범위로 검증한다.
5. `logistics-ui-test` Compose 프로젝트·전용 `.env.ui-test`·DB 볼륨을 사용한다. Gateway 18080, UI 5174를 loopback으로 바인딩한다. 테스트 스크립트는 Slack Webhook을 빈 값으로 강제한다.
6. 시드는 실제 공개 API로 계정·업체·상품·주문을 준비한다. 같은 허브의 집하·업체 배송 2구간을 우선 검증한다. 미구현 I-03을 성공한 것으로 꾸미지 않는다.
7. 조회 화면에는 실제 건수·로딩·빈 결과·오류를 구분한다. 상태 색상에는 텍스트를 병기하고 라벨·키보드·초점·작은 화면을 검증한다.

## 검토한 대안

- Spring 정적 HTML에 UI를 넣기: 서비스 빌드와 화면 변경이 결합된다. 프록시와 독립 빌드로 분리한다.
- Next.js/SSR: 로그인 후 운영 콘솔이 주 용도이고 기존 Spring API를 사용하므로 별도 웹 서버 계층을 늘리지 않는다.
- shadcn CLI 전체 생성: 필요한 컴포넌트가 적어 같은 Radix 기반 접근을 직접 구성한다. 사용하지 않는 컴포넌트는 추가하지 않는다.
- localStorage JWT: 새로고침 로그인은 편하지만 토큰 보존 범위가 길어진다. 테스트 UI는 메모리를 선택한다.
- 응답 유실 때 새 키로 주문 재생성: 중복 주문 위험이 있으므로 같은 키와 본문을 유지한다.
- 기존 개발 DB에서 E2E: 데이터·실제 Slack 알림에 영향을 줄 수 있어 전용 스택을 사용한다.

## 결과

사용자가 같은 화면에서 주문·배송·권한·실패 동작을 재현할 수 있다. 도입한 패키지 버전은 `frontend/package-lock.json`에 고정하며 Node 24·npm 환경에서 검증한다.
별도 Spring 서비스 8개와 PostgreSQL을 추가 실행하므로 자원을 더 사용한다. 테스트 종료 시 전용 `down`으로 중지할 수 있고 데이터는 남는다.
회원가입은 기존 U-01의 USER 생성 계약을 사용하고 비밀번호를 화면 밖에 저장하지 않는다. 소속명 입력으로 업무 권한을 자동 부여하지 않는다. 업체 등록 C-01은 기존 서버 정책에 따라 USER 본인을 담당자로 연결한다.
후속 요청으로 업체 등록·수정·담당자 변경, 상품 등록, 배송담당자·허브 지정을 기존 C-01·C-04·P-01·U-07에 연결했다. API·DB 스키마는 변경하지 않았다.
상품 등록 요청의 큰 원화 가격도 Number를 거치지 않고 정확한 JSON 정수로 직렬화한다. 업체·상품 생성 결과가 불확실하면 자동 재전송하지 않고 목록 확인을 안내한다.
업체·상품 삭제, 상품 수정, 계정 역할 변경·비활성화, 배송지 수정·구간 재배정, cross-hub 주문, 생성 진행 조회·자동 복구는 후속 범위다.

공식 근거: [Vite](https://vite.dev/guide/), [TanStack Query](https://tanstack.com/query/latest/docs/framework/react/overview), [Radix Dialog](https://www.radix-ui.com/primitives/docs/components/dialog), [lossless-json](https://github.com/josdejong/lossless-json).
