# PR 제목

공통 인프라 브랜치의 검증된 구현과 보완 사항 선별 통합

## 작업 내용
팀 브랜치마다 다른 초기 설정·인증·DB 구성을 origin/main 기준으로 비교하고, 최신 SA와 맞는 공통 인프라를 선별했다. junhyun의 멀티 모듈·Config Server·Gateway 보안·인증·Flyway·테스트를 기반으로 haneol의 공용 헤더 검증, KEJ의 반복 논리 삭제 방어, KMH의 DB 비밀번호 환경 변수 주입, seungkeun의 public 스키마 격리를 적용했다.

## 변경 이유
사용자 컨텍스트가 불완전한 역할 검사 요청은 401로 거부하고, 같은 Entity의 반복 삭제는 최초 Audit을 유지한다. DB 계정 생성과 서비스 접속이 같은 환경 변수 값을 사용하며 작은따옴표가 든 비밀번호도 안전하게 처리한다. 기존 환경과 별도 Compose 프로젝트·호스트 포트로 검증할 수 있다.

## 확인 방법
- `./gradlew build`: 성공, 테스트 70개·실패 0·오류 0·skip 0.
- 기존 구현에서 새 회귀 테스트 4개 실패를 재현하고 보완 후 통과.
- `docker compose up -d --build`: 별도 빈 DB·9개 컨테이너 기동 성공.
- `script/e2e-check.sh --with-cache-ttl`: 최종 스크립트에서도 PASS 17 / FAIL 0, 권한 캐시 만료 후 재조회 포함.
- `script/db-check.sh`: 계정 5개 TCP 인증·스키마 격리 PASS, USER 비밀번호 작은따옴표 포함.
- 존재하지 않는 Compose 프로젝트는 HTTP 요청 전 종료한다.
- `bash -n` 및 `git diff --cached --check` 통과.

## 리뷰 포인트
- 브랜치별 출처·채택·제외·보류 판단을 통합 검토 문서에서 확인한다.
- DB 초기화는 빈 볼륨에만 적용된다. 기존 DB 비밀번호·Flyway 이전 스키마 이행은 별도 작업이다.
- 업무 CRUD·검색·Saga, API별 정렬 whitelist, IDE/Windows 실행은 이번 검증 완료 범위가 아니다.
- 기존 ADR의 채택 표기는 원작성 브랜치의 상태다. 이번 main 반영은 팀 리뷰를 거친다.

## 관련 문서
- `docs/reviews/base-setting-integration.md`
- `docs/progress/codex-base-setting-integration.md`
- `docs/decisions/0008-base-setting-integration.md`
- `docs/agents/document-map.md`
