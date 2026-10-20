# feat/docs-learning-evidence

## 지금 하는 일
학습 문서·검증 자료 정리와 커밋·푸시 완료, dev 대상 PR #24 리뷰 대기다.

## 진행 상태
- [x] 2026-10-20 dev `e55b2a3`에서 브랜치를 만들고 origin/dev와 동일한 시작점임을 확인했다.
- [x] 기존 변경을 확인했다. 학습 문서·측정 원문·이미지·임시 모니터링 구성과 관련 인계·리뷰 문서를 포함한다.
- [x] Eclipse 개인 설정(`.project`, `.settings/`, `.classpath`, `.factorypath`)과 `bin/` 빌드 산출물은 커밋 대상에서 제외하고 로컬에 보존한다.
- [x] `./gradlew build` 통과(39초, 58개 작업 중 9개 실행·49개 UP-TO-DATE). order 테스트 469개 재실행, 실패·오류·skip 0이다. 다른 모듈 테스트는 기존 결과를 재사용했다.
- [x] `script/docs-check.sh`, `script/error-codes-check.sh`(31개), `git diff --cached --check` 통과. Markdown 12개 상대 경로·JSON 1개·SVG 1개 검사와 스테이징 비밀키 패턴 검사도 통과했다.
- [x] 자료 커밋 `9c750a8`을 origin/feat/docs-learning-evidence에 푸시했다. [PR #24](https://github.com/logistics-group3/logistics-management/pull/24)를 dev 대상으로 만들고 OPEN·head와 원격 커밋 일치를 확인했다.
- [x] PR 범위의 `script/docs-check.sh origin/dev`, `git diff --check origin/dev...HEAD`도 통과했다. YAML 자료 3개 파싱을 추가로 확인했다.
- 면접 포인트: 과거 측정 증거와 현재 재검증 결과를 어떻게 구분하는가. 테스트 DB 연결과 서비스 런타임 HikariCP 메트릭은 왜 별도로 보는가.

## 다음 단계
1. PR #24의 GitHub 검사와 리뷰 의견을 확인한다. 병합은 별도 판단으로 남긴다.
2. Eclipse 개인 설정과 빌드 산출물은 로컬에 남아 있다. 후속 작업에서도 커밋 대상과 구분한다.

## 직접 해보기
1. `git diff --stat origin/dev...HEAD`로 PR에 포함된 파일을 확인한다. IntelliJ에서는 Git 로그에서 origin/dev와 현재 브랜치를 비교한다. 문서와 자료만 보이면 범위가 맞다.
2. `script/docs-check.sh origin/dev`, `script/error-codes-check.sh`를 실행해 두 검사 모두 통과하는지 확인한다.
3. `gh pr view --json url,baseRefName,headRefName,state`로 대상 dev, 현재 브랜치, OPEN 상태를 확인한다.

## 메모
- 기존 학습 문서의 2026-10-16·19 검증 결과는 당시 기록이다. 이번 빌드·정적 검사 결과와 구분한다.
- 프런트·소비 API 계약 변경이 없어 프런트 검증과 E2E는 이번 작업에서 실행하지 않는다.
- 임시 Grafana 구성은 정적 자료로 검토한다. 이번 작업에서 모니터링 스택을 띄우거나 DB 연결 고갈 실험을 재실행하지 않는다.
