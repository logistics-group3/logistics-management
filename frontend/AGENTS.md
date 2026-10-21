# frontend 작업 지침

루트 AGENTS.md를 따른다. 이 디렉터리는 별도 npm 프로젝트이며 Gradle 모듈이 아니다.

- API와 실행 환경은 README.md, 기술 선택은 docs/decisions/0015-logistics-test-ui.md를 읽는다.
- 빌드·검증 정본은 `../docs/guides/full-build.md`다. Gradle은 이 디렉터리를 빌드하지 않는다. 이 폴더 밖에서 API 계약을 바꾼 경우도 프런트 검증 범위를 확인한다.
- 백엔드 상태·권한·계약을 UI에서 새로 정의하지 않는다. 실제 서비스 Controller와 DTO를 확인한다.
- 인증 토큰은 메모리에만 보관하고 내부 API를 호출하지 않는다.
- 금액은 Number로 변환하지 않는다. 주문 생성 키는 결과 미확정일 때 새로 발급하지 않는다.
- 변경에는 build·lint·관련 단위 테스트와 실제 브라우저 검증을 적용한다. E2E는 logistics-ui-test 환경에서만 실행한다.
- 다른 작업의 기존 문서·.env·실행 서버를 변경하지 않는다. 자동 테스트는 Slack 알림을 비활성화한다. 명시적으로 승인된 실제 DM 검증은 수신자 허용 목록을 설정하고 별도로 진행한다(`docs/guides/slack-dm.md`).
