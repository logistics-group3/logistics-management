# codex/logistics-test-ui

## 지금 하는 일
이메일만 받는 가입·Slack DM과 프런트 포함 빌드 지침을 커밋·푸시했다. dev 대상 [PR #7](https://github.com/logistics-group3/logistics-management/pull/7)에서 팀 리뷰를 기다린다.

## 진행 상태

- [x] PR #6의 dev 병합을 GitHub에서 확인했다. 병합 커밋은 b25e2d81b7cdc33d23b4209ffae514886e6a746c다.
- [x] 로컬 dev를 origin/dev, main을 origin/main까지 fast-forward하고 dev로 전환했다.
- [x] 기존 로컬 브랜치 5개를 삭제했다. 현재는 main·dev·codex/logistics-test-ui만 있다. 새 UI 브랜치의 시작점은 dev와 같다.
- [x] 삭제 전 모든 로컬 브랜치·기존 stash를 Git bundle로 백업했다. dev에 포함되지 않은 junhyun/base-setting의 이력도 백업에 포함했다. 원격 브랜치는 변경하지 않았다.
- [x] 주 작업 공간의 미커밋 문서 5개와 별도 검토 worktree의 문서 7개를 보존했다. 검토 worktree는 같은 커밋에 둔 채 detached HEAD로 전환했다. .env·IDE 설정·기존 stash도 유지했다.
- [x] 현재 저장소에 프런트엔드 패키지가 없음을 확인했다. Gateway 경로와 user·company·order·delivery·hub의 실제 Controller·DTO를 읽었다.
- [x] 1차 범위를 전체 메뉴·주문 전체 흐름·배송 조회/단계 변경 우선으로 정했다. 실제 API가 제공하는 데이터와 권한으로 동작하게 계획한다.
- [x] 사용자가 구현·실제 API 검증·백엔드/프런트엔드 실행 유지까지 요청했다. 별도 테스트 환경·계정으로 진행한다.
- [x] React·TypeScript·Vite UI 구현: 전체 메뉴, 회원가입·로그인·프로필, 주문 O-01~O-06, 배송 D-01·D-02·D-05, 기준 정보 조회
- [x] 별도 logistics-ui-test 프로젝트·전용 비밀값·공개 API 시드 구성. 원래 컨테이너 9개·.env의 ID·시작 시각·해시 유지
- [x] build·lint 통과, 프런트 단위 40개·실제 API 브라우저 E2E 14개 통과. 대시보드·모바일 주문·회원가입 폼 axe 위반 0개
- [x] Gradle 전체 build 통과. 테스트 보고서 689개, 실패·오류·skip 0개. docs-check·에러 코드 검사 통과
- [x] 사용자의 추가 요청으로 회원가입 화면·입력/중복 검증·가입 후 로그인 연결 구현. 실제 신규 USER 가입·로그인·프로필·미연결 업체 안내 확인
- [x] 기본 시드 4계정·3업체·4상품·12주문, 실제 취소·배송 완료·권한·멱등 재요청 확인
- [x] [실행 안내](../../frontend/README.md), [ADR 0015](../decisions/0015-logistics-test-ui.md), [검증 기록](../reviews/logistics-test-ui.md), TS-018·019 기록
- [x] 업체 C-01·C-04, 상품 P-01, 배송담당자 U-07 연결. 신규 USER의 가입→본인 업체 2곳→상품→주문 생성, MASTER 담당자 이전→새 담당자 주문, 허브 지정→실제 자동 배정·배송 완료·해제까지 검증
- [x] Slack 이메일 조회·Webhook/DM 차이 [학습 노트](../learning/junhyun/qna/2026-10-15-slack-webhook-email-and-dm.md), 실행 안내·검증 기록 갱신. Slack 설정 변경·실제 발송 없음
- [x] UI 작업을 5aaf586으로 커밋했다. 기존 별도 학습 문서 5개는 제외했다. 원격 푸시는 하지 않았다.
- [x] Slack DM: user slackEmail·V2·I-16, delivery I-15, order 별도 AFTER_COMMIT·비동기 DM 송신, 가입·MASTER 이메일 UI·설정 구현
- [x] 전체 build 727개(user 86·delivery 39·order 451 포함), 프런트 단위 41개·실제 API E2E 15개·build·lint 통과. 새 통합 테스트 풀 제한은 TS-020 기록
- [x] 기존 Slack 앱에 users:read·users:read.email·im:write·chat:write를 사용자 승인으로 재설치. 본인 이메일만 허용하고 실제 주문 201→I-15→I-16→Slack 접수 1건 확인, 테스트 주문 취소·논리 삭제
- [x] 증거 캡처 오류로 Bot Token 노출. 사용자 승인 후 해당 Bot Token만 auth.revoke로 폐기했고 이전 토큰 인증 거절을 확인했다. 새 토큰·동일 권한·수신자 조회 검증 후 전용 서버에 교체. 추가 DM은 보내지 않음
- [x] 기존 개발 Webhook이 no_service인 것을 확인. 사용자 승인으로 `.env`의 URL 한 줄을 같은 채널의 새 URL로 교체하고 기존 이미지 버전의 order-service만 재생성. 나머지 8개 컨테이너·DB 보존, 채널 메시지 테스트 없음
- [x] 실행 안내·검증·SA의 로컬 확장·서비스 노트·배송 T06·USER-002·DELIVERY-003 인계. 제공 담당자의 팀 리뷰는 OPEN 유지
- [x] 사용자 후속 요청으로 가입의 Slack 멤버 ID 입력을 제거했다. U-01 필수 이메일·V3 nullable slackId·기존 값 보존, 입력/요청에 ID 없는 실제 가입→주문→배송 E2E 확인
- [x] PR 제출 전 user 88개 실행·전체 build 729개(실패·오류·skip 0), 프런트 build·lint·format·단위 41개, 브라우저 E2E 15개(43.5초) 통과. 실제 Slack 발송 없음
- [x] AGENTS·CLAUDE·품질 기준·PR 템플릿에 백엔드/프런트 검증 구분과 [전체 빌드 정본](../guides/full-build.md) 연결
- [x] `e9b3bca feat(order): 이메일 가입과 배송담당자 Slack DM 구현` 커밋·브랜치 푸시. 이전 UI `5aaf586`과 함께 dev 대상 [PR #7](https://github.com/logistics-group3/logistics-management/pull/7) 생성, OPEN·충돌 없음 확인. 제출 시 docs-guard 검사 실행을 확인했고 최종 상태는 PR에서 확인한다.
- 면접 포인트: USER 본인 담당 연결과 MASTER 재배정의 요청 차이, 큰 정수 JSON 전송, 멱등키 없는 생성의 결과 미확정 처리, Webhook과 봇 DM의 차이

## 다음 단계

1. http://127.0.0.1:5174/signup 에서 회원가입부터 테스트한다. 계정·시나리오는 [frontend README](../../frontend/README.md)에 있다.
2. [PR #7](https://github.com/logistics-group3/logistics-management/pull/7)에서 user·delivery 계약과 공용 compose 설정을 리뷰한다. UI 커밋은 5aaf586, 이메일 가입·DM·AI 빌드 지침은 e9b3bca다. [실행 안내](../guides/slack-dm.md)·[검증 기록](../reviews/slack-dm.md)이 근거다. 병합은 수행하지 않았다.
3. 후속 범위는 업체·상품 삭제, 상품 수정, 계정 역할 변경·비활성화, 배송 수정/재배정·cross-hub 연동이다. I-03 준비 전에는 다른 허브 간 주문 제한을 유지한다.

아래는 구현 때 사용한 최초 계획이다. 최종 구현·검증 사실은 위 완료 항목과 검증 기록이 기준이다.

### 1. 화면 방향

서비스 가칭은 `SPARTA LOGISTICS`다. 한국어 물류 운영 담당자가 주문과 배송을 처리하는 데스크톱 중심 관리 화면으로 구성한다.
짙은 남색 사이드바, 밝은 회색 작업 영역, 흰색 표·카드, 청록색 주요 버튼을 사용한다. 상태는 색상과 텍스트를 함께 표시하고 키보드 초점·대비를 확인한다.

- 왼쪽 메뉴: 대시보드, 주문 관리, 배송 관리, 업체 관리, 상품 관리, 허브 관리, 사용자 관리
- 상단: 현재 화면·경로, 로그인 사용자·역할, 계정 메뉴
- 본문: 기간·업체·상태 필터, 결과 건수, 목록, 페이지 이동, 주요 업무 버튼
- 상세: 목록에서 오른쪽 패널로 주문·배송 요약을 확인하고 필요하면 상세 페이지로 이동
- 기준 화면: 1440px 데스크톱. 작은 화면에서는 메뉴를 접고 표를 스크롤할 수 있게 한다.
- 금액은 원 단위로 표시한다. 로딩·빈 결과·연동 오류를 구분하고 확인되지 않은 집계를 0이나 예시 숫자로 표시하지 않는다.

### 2. 메뉴별 1차 범위

| 메뉴 | 1차 구현 | 현재 API 근거 |
|---|---|---|
| 로그인·내 정보 | 로그인, 로그아웃, 세션 만료 처리, 실제 계정의 역할 표시 | U-02·U-03 |
| 대시보드 | 오늘 주문·취소 주문 등 조회 API로 산출 가능한 건수, 최근 주문, 최근 배송 | O-02·D-01의 필터·totalElements |
| 주문 관리 | 검색·필터·페이지, 생성, 상세, 요청사항 수정, 취소, MASTER 삭제 | O-01~O-06 |
| 배송 관리 | 검색·상세, 구간별 진행 상황·담당자, 권한에 맞는 출발/도착 처리 | D-01·D-02·D-05 |
| 업체 관리 | 목록·상세, 주문 폼의 공급/수령업체 선택 | C-02·C-03 |
| 상품 관리 | 목록·상세, 공급업체별 상품 선택 | P-02·P-03 |
| 허브 관리 | 목록·상세, 업체·배송의 허브 정보 표시 | H-01·H-02 |
| 사용자 관리 | MASTER 사용자 목록·상세, 본인 정보 | U-03~U-05 |

업체·상품의 전체 CRUD, 사용자 권한 변경·비활성화, 배송지 수정·담당자 재배정은 2차로 둔다. 1차 주문 테스트에 필요한 실제 업체·상품·배송담당자는 별도 테스트 환경에서 준비한다.
배송 구간은 서버가 반환한 목록을 그대로 표시한다. 같은 허브 주문은 허브 이동 구간이 생략될 수 있으므로 화면에 항상 3개 구간을 고정하지 않는다.
사용자 역할은 MASTER·USER와 배송담당자 여부·업체 담당 관계를 함께 본다. 조회 범위와 수정 권한은 현재 서버 정책을 따르고 UI에서 새로운 역할을 만들지 않는다.

### 3. 제안하는 기술 구성

새 `frontend/` 디렉터리에 독립된 프런트엔드 앱을 둔다. 기존 Gradle 모듈과 백엔드 API는 유지한다. 아래는 구현 전 제안이며 실제 설치 버전은 착수 시 확인하고 lockfile에 고정한다.

| 구성 | 이유 |
|---|---|
| React + TypeScript + Vite | 화면 구성·입출력 타입을 관리하고 기존 Spring API와 연결한다. Vite가 React TypeScript 템플릿을 제공한다([공식 가이드](https://vite.dev/guide/)). |
| Tailwind CSS + shadcn/ui | 표·폼·다이얼로그·패널의 기본 동작을 재사용하고 서비스 색상·간격을 맞춘다([Vite 설치 가이드](https://ui.shadcn.com/docs/installation/vite)). |
| TanStack Query | 조회 캐시, 로딩·오류, 수정 후 관련 목록 갱신을 관리한다([공식 개요](https://tanstack.com/query/latest/docs/framework/react/overview)). |
| Gateway 프록시 + 공통 API 모듈 | 브라우저는 `/api/v1/**`만 사용한다. 로컬 프록시 대상은 실제 Gateway 포트로 설정한다([Vite server.proxy](https://vite.dev/config/server-options.html#server-proxy)). |

구성 예: `app/`(라우팅·레이아웃·인증), `features/orders/`, `features/deliveries/`, `features/catalog/`, `shared/api/`, `shared/ui/`.
브라우저에서 `/internal/**`를 직접 호출하지 않는다. 업체·상품 선택은 공개 검색 API를 사용하며 UUID를 직접 입력하는 폼 대신 이름으로 검색해 선택한다.

### 4. 주문·배송의 핵심 동작

1. 주문 생성은 공급업체 → 해당 업체 상품 → 수령업체 → 수량·요청사항 순서다. 한 주문에 한 품목만 담는다. 단가·주소는 조회 결과를 보여주고 최종 가격은 생성 응답의 서버 값을 사용한다.
2. 최초 제출에 Idempotency-Key를 발급한다. 키·원래 본문·요청 상태를 사용자별로 보존해 중복 클릭·응답 유실·새로고침 후에도 같은 요청을 확인할 수 있게 한다.
3. 쓰기 요청을 자동 재전송하지 않는다. 처리 중·복구 대기는 새 주문으로 우회하지 않고 기존 요청의 결과 확인을 안내한다. 성공 응답 후 관련 주문·배송 캐시를 갱신한다.
4. 최초 생성 응답과 현재 주문 상태를 구분한다. 수정·취소·삭제 이후 현재 화면은 조회 API의 값을 사용한다.
5. 취소·삭제는 대상 주문과 영향을 확인하는 다이얼로그를 거친다. USER에게 MASTER 삭제 버튼을 제공하지 않고 서버의 최종 인가·상태 거절도 표시한다.
6. 배송 상태는 조회 결과로 표시하고 구간별 허용된 다음 동작만 제공한다. 허브 이동 구간 도착 시 필요한 실제 거리·소요 시간 입력을 받는다.
7. 오류는 사용자에게 의미가 있는 메시지와 다음 행동을 보여준다. 개발용 상세에는 HTTP 상태·오류 코드·멱등키를 표시할 수 있지만 인증 토큰·비밀값은 기록하지 않는다.

추가 구현 기준:

- JWT는 메모리에 보관하고 로그아웃 시 인증 상태와 사용자별 조회 캐시를 정리한다. 새로고침 후에는 재로그인하고 보존된 미완료 주문 요청을 확인할 수 있게 한다.
- Java long 금액의 큰 정수 값이 브라우저의 Number 변환으로 손실되지 않도록 API 파싱·표시 경계에서 검증한다.
- 서버가 제공하지 않는 주문 자유 검색·임의 정렬·복구 상태 조회를 UI에서 가능한 기능처럼 만들지 않는다. 필터는 실제 계약에 맞춘다.
- 주문 목록의 이름 조회는 업체·상품 캐시를 사용한다. 배송 상세는 필요할 때 읽어 목록의 모든 행에서 반복 호출하지 않는다.

### 5. 구현 순서와 각 단계의 완료 기준

| 단계 | 작업 | 완료 기준 |
|---|---|---|
| A. 실행 기반·공통 화면 | frontend 구성, Gateway 주소 확인, 로그인, 메뉴·레이아웃·공통 표·상태 UI | 실제 로그인 후 내 정보와 메뉴가 표시되고 로딩·인증 오류가 구분됨 |
| B. 주문 핵심 흐름 | 업체·상품 선택, 생성·조회·검색·수정·취소·삭제, 멱등키 유지 | 브라우저에서 O-01~O-06 정상·권한·실패 흐름을 수행할 수 있음 |
| C. 배송·전체 메뉴 | 배송 목록·상세·구간 전이, 주문과 연결, 나머지 메뉴 조회, 대시보드 | 같은 허브 주문의 생성→배송 진행→완료→삭제 및 생성→취소→삭제를 확인 |
| D. 통합 검증·마감 | 실제 API E2E, 반응형·키보드·빈 화면·오류 화면, 실행 가이드 | 화면에서 주요 기능을 재현하고 검증 결과·남은 연동 사항을 기록 |

### 6. 현재 연동 조건

소스 기준은 dev b25e2d8이다. user·company·delivery와 허브 조회·I-02가 있으며 I-14로 배송담당자를 조회할 수 있다.
hub I-03의 Controller는 아직 없다. 같은 허브 주문은 해당 호출을 생략하므로 1차 실연동 경로로 사용하고, 다른 허브 간 주문은 I-03·경로 데이터가 준비된 뒤 확장한다.

현재 실행 중인 컨테이너가 최신 dev 소스와 같은 버전인지는 UI 착수 시 별도로 확인한다. 테스트 데이터는 별도 테스트용 Compose 프로젝트에서 준비하고 Slack은 비활성화해 실제 채널로 테스트 알림이 나가지 않게 한다.
배송의 취소·집하 시작 동시성 등 기존 연동 검토 항목은 [주문 연동 계약](../services/order/integration-contract.md)을 따른다. 이 계획만으로 해결된 것으로 표시하지 않는다.

## 직접 해보기

1. `git branch --show-current`에서 `codex/logistics-test-ui`를 확인한다. IntelliJ 브랜치 메뉴에서도 같은 이름이 보인다.
2. http://127.0.0.1:5174/signup 에서 가입→로그인을 확인한다. 준비된 주문 흐름은 `uimaster` / `UiLocal!2026`로 로그인한다. 현재 서버와 UI는 실행 중이다.
3. 새 USER는 업체 관리 → 업체 등록에서 서울특별시 센터의 본인 업체를 등록하고, 주문에서 공급업체 스파르타 서플라이와 본인 수령업체를 선택한다. 상품까지 새로 만들 때는 같은 허브의 공급업체를 한 곳 더 등록한다. 다른 주문은 배송 상세에서 2구간 출발·도착 후 삭제한다.
4. MASTER는 업체 상세에서 담당자를 변경하고, 사용자 상세에서 배송담당자·허브를 지정한다. `uiuser`·`uidriver` 계정(같은 테스트 비밀번호)으로 조회 범위와 담당 구간 버튼을 비교한다.
5. 재실행은 루트 터미널에서 `script/ui-test.sh up`, `script/ui-test.sh frontend`다. 초기 데이터가 없을 때만 DM 비활성화 상태에서 `script/ui-test.sh seed`를 실행한다.
6. 현재 DM은 승인한 본인 주소에만 활성화돼 있다. 자동 E2E·시드는 차단되므로 `.env.ui-test`에서 DM을 끄고 `up`으로 반영한 뒤 `npm --prefix frontend run test:e2e`를 실행한다. 수동 주문 DM은 `uidriver`가 배정되는 서울 허브로 테스트한다.

## 메모

- 작업일: 2026-10-15~16 (Asia/Seoul). 계획 제시 후 사용자가 전체 1차 구현과 서버·화면 실행, 이어서 이메일 DM을 요청했다.
- UI 커밋과 승인된 DM 1건의 실연동을 수행했다. 토큰 교체 후 사용자 승인으로 기존 개발 주문 서비스의 Webhook만 복구했다. 이번 PR 준비에서는 기본 개발 환경과 별도 작업 문서 5개를 보존하며 추가 실제 Slack 메시지를 보내지 않는다.
- 브랜치 정리 복구 묶음: `/Users/alloy/.codex/backups/logistics-management/local-branches-2026-10-15-5f0b0df7`. branches.bundle·원래 ref 목록·미커밋 문서·보존 확인 결과가 있다.
- 별도 검토 worktree `/Users/alloy/.codex/worktrees/dev-merge-review/logistics-management`는 파일을 유지한 채 1384a5d의 detached HEAD다.
- 기존 `docs/learning/junhyun/README.md`, Q&A 3개와 주문 progress의 GitHub 학습 기록은 별도 작업이며 보존한다.
- 최초 UI 작업에서는 `.env`·IDE 파일 해시를 유지했다. 이후 사용자 승인에 따른 Webhook 복구로 `.env`의 해당 URL 한 줄만 변경했다. 기존 README 줄바꿈 stash(8b7392d…)는 보존했다.

- 이번 로컬 프런트 실행 세션은 81164이며 전용 Compose 프로젝트는 logistics-ui-test다. 생성한 앱 브라우저 탭에 회원가입 화면을 열어 두었다.

- Slack 앱 확인: Chrome의 `Logistics Order Noti`(A0C986356H1), 디스이스스파르타(T0C98T2SSGM). 사용자 로그인 완료 확인. 앱 브라우저는 로그인 전이라 Chrome을 사용한다.
- 승인 권한은 기존 incoming-webhook과 추가 4개(users:read, users:read.email, im:write, chat:write)다. chat:write.customize는 추가하지 않았다. Bot Token은 교체된 값으로 `.env.ui-test`에만 저장하며 파일 권한은 600이다.
