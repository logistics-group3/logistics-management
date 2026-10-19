# Slack DM 구현 검증

- 날짜: 2026-10-16 (Asia/Seoul)
- 브랜치: `codex/logistics-test-ui`
- 기준 커밋: `5aaf586 feat(ui): 회원가입부터 주문·배송 운영 화면 구현`
- user·delivery 계약과 compose 설정은 팀 리뷰 대상이다. 커밋·PR 현황은 [브랜치 인계](../progress/codex-logistics-test-ui.md)에 기록한다.

## 자동 검증

| 명령·범위 | 결과 |
|---|---|
| `./gradlew build` | 729개, 실패·오류·skip 0. user 88, delivery 39, order 451 포함. 이메일 전용 가입 변경 후 user 88개를 실행했고 전체 build는 캐시 결과를 재사용해 통과 |
| `npm --prefix frontend run test` | 41개 통과 |
| `npm --prefix frontend run build`·`lint` | 통과 |
| `npm --prefix frontend run format:check`·`script/docs-check.sh`·`script/error-codes-check.sh` | 통과. 에러 코드 29개, 중복·문서 누락 없음 |
| `npm --prefix frontend run test:e2e` | 이메일 전용 가입 변경 후 실제 격리 서버에서 15개 통과, 43.5초. 실제 Slack은 비활성화한 상태 |
| DM 활성화 상태에서 E2E globalSetup만 호출 | 주문 생성 전에 차단되는 것을 확인. 자동 주문 발송 없음 |

Slack HTTP 대역은 이메일 인코딩, Bot 인증, 워크스페이스·탈퇴·봇 확인, DM 채널 구조, postMessage의 ok·channel·ts, 429·5xx·timeout·잘못된 JSON·ok=false를 검증한다.
주문 통합 테스트는 DB 커밋 이후에만 발송, 응답 비차단, 멱등 재응답의 미발송, Slack 장애에도 201 유지, 롤백·트랜잭션 밖 이벤트 미발송을 확인한다.
user는 이메일 가입·수정·생략·해제·권한·형식·DB 정규화·탈퇴 404를, delivery는 중복 담당자·완료 구간·취소·삭제를 검증한다.
신규 가입의 이메일 필수·ID 미입력 성공을 추가 검증했다. 처음에는 이메일만 보낸 가입이 400으로 실패하는 것을 재현했고, V3·Request 변경 뒤 통과했다. 실제 V2→V3 적용 후 기존 4계정의 연락처 해시가 유지됐다. 브라우저 E2E는 ID 입력칸과 요청 필드가 없으며 이메일만으로 가입·로그인·업체·상품·주문 생성·배송 배정이 되는 것을 확인했다.
새 통합 테스트의 Hikari 풀을 각각 최대 2개·최소 유휴 0개로 제한한 이유는 [TS-020](../troubleshooting.md#ts-020-spring-통합-테스트를-추가하자-postgresql-연결-수가-초과됨)에 있다.

## 실제 Slack 연동

1. 사용자가 기존 앱에 추가 4개 권한을 승인해 재설치했다. auth.test로 워크스페이스 일치와 권한을, users.lookupByEmail로 승인한 본인 수신자를 확인했다. 이 단계에서는 메시지를 보내지 않았다.
2. Git 제외·0600인 `.env.ui-test`에 Bot Token을 저장했다. 허용 이메일은 1개이고 Webhook은 비활성화했다. 기존 개발 `.env`는 변경하지 않았다.
3. 전용 DB의 `uidriver`를 수신자로 설정했다. 같은 서울 허브의 유일한 배송담당자인 것을 확인했다.
4. 요청사항이 `[연동 테스트] 실제 배송 요청이 아닙니다`인 주문을 공개 Gateway O-01로 한 번만 생성했다. 201 응답을 확인했다.
5. 실제 I-15·I-16 응답으로 담당자 1명과 연락처를 확인했다. 2026-10-16 00:28:17 KST에 해당 orderId의 `SLACK_DM_SENT` 1건을 확인했다. 이 로그는 Slack의 ok=true·일치하는 DM 채널·ts 검사 뒤에만 남는다.
6. 생성한 테스트 주문만 O-05 취소·O-06 논리 삭제했다. 테스트 배송담당자 이메일은 사용자가 화면에서 수동 테스트할 수 있게 유지했다. 승인한 1건 외에 DM·채널 메시지를 보내지 않았다.
7. 증거 캡처의 좌표 오류로 Bot Token이 대화 이미지에 노출됐다. 사용자 승인 후 auth.revoke로 해당 Bot Token만 폐기하고 이전 토큰의 인증 거절을 확인했다. 동일 권한으로 재설치해 다른 토큰을 발급받았고 인증·권한·수신자 조회를 확인했다. 재발송은 하지 않았다. 잘못된 로컬 이미지는 삭제하고 토큰 없는 App Manifest 화면으로 교체했다.
8. 기존 `.env`의 Webhook이 앱 목록에 없고 GET에 404 no_service를 반환했다. 토큰 폐기 직전 유효성을 따로 조회하지 않아 정확한 무효화 시점은 미확인이지만, 폐기와 연관된 영향으로 보고 복구했다. 사용자 승인 후 같은 채널의 새 URL 한 줄만 `.env`에 반영하고, 기존 이미지 ID를 고정해 기본 개발 order-service만 다시 만들었다. 새 Webhook은 메시지 없는 GET에서 400 invalid_payload를 반환했다. 실제 채널 발송 테스트는 하지 않았다.

기본 개발 환경의 나머지 8개 컨테이너 ID·시작 시각과 주문 이미지 ID가 유지된 것을 확인했다. `.env`는 Webhook 줄을 제외하면 백업과 동일하다. 개발·테스트 주문 서버의 최종 health는 UP이고 UI는 HTTP 200이다. 새 DM은 격리 테스트 스택에만 활성화한다.
복구 직전 기존 주문 컨테이너에 재시작 1회와 달라진 시작 시각이 관찰됐고 원인은 확인하지 못했다. 이 관찰은 사용자 승인 후 수행한 Webhook 복구·컨테이너 재생성과 구분한다.

메시지 읽음은 확인하지 않았다. 실제 연동은 같은 허브 2구간·동일 담당자 경로다. 다른 허브 I-03·다중 실제 수신자·Slack 장애 시 운영 복구를 실제 외부 서비스로 시험한 것은 아니다.

## 인계와 증거

- 실행 설정·UI 조작·실패 판단: [실행 안내](../guides/slack-dm.md)
- 설계·팀 리뷰: [ADR 0016](../decisions/0016-delivery-manager-slack-dm.md), USER-002, DELIVERY-003
- 로컬 증거(커밋 제외): `.local/slack-dm-live-check.json`에 주문·배송 ID, 한 번의 생성 키, 접수·정리 상태. 토큰·수신 이메일은 넣지 않았다.
- 토큰 폐기·교체 및 Webhook 복구의 비밀값 없는 검증 결과는 `.local/slack-dm-token-replacement.json`, `.local/slack-webhook-restoration.json`에 있다.
- 화면 증거(커밋 제외): `.local/ui-test-evidence/slack-dm-scopes.png`는 토큰 없는 App Manifest의 권한 설정, `slack-email-settings.png`는 테스트용 이메일 설정 UI다.
- 전체 빌드 로그: `/tmp/logistics-slack-dm-build.log`, 브라우저 보고서: `frontend/playwright-report/index.html`
- PR 제출 전 이메일 전용 가입 검증 로그: `/tmp/logistics-ui-pr-build.log`, `/tmp/logistics-email-only-user.log`, `/tmp/logistics-ui-pr-e2e.log`. AI 공통 빌드 기준은 [full-build.md](../guides/full-build.md)에 있다.

현재 UI·백엔드는 실행 중이며 DM은 승인된 본인 주소에만 활성화돼 있다. 자동 검증을 재실행할 때는 [실행 안내](../guides/slack-dm.md)의 비활성화 절차를 먼저 따른다.
