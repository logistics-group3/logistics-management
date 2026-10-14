# integration/base-setting

## 지금 하는 일
Notion 팀 의견을 공통 인프라 통합 브랜치에 반영한다 (PR A, 코드)

## 진행 상태
- [x] 스텝 13 실행 환경 보강: 줄바꿈·인코딩·데몬 JDK·비밀값 무시 패턴 (compile 성공, 데몬 JVM 17 확인)
- [x] 스텝 14 Eureka 로컬 튜닝: 서버 자기 보호 해제, 갱신 5초·만료 15초·목록 조회 5초 (런타임 측정은 스텝 21)
- [x] 스텝 15 소유자 검증 헬퍼: `UserContextTest` 5개, `UserContextWebTest` 7개 통과
- [ ] 스텝 16 common 조건부 자동 설정
- [ ] 스텝 17 정렬 허용 목록·QueryDSL 도우미
- [ ] 스텝 18 내부 API DTO 반환
- [ ] 스텝 19 MASTER 계정 자동 생성
- [ ] 스텝 20 에러 코드 규칙·통합 문서
- [ ] 스텝 21 새로 시작 안내·잔여 수정, 전체 검증, PR
- 면접 포인트: Gradle toolchain과 데몬 JVM 기준의 차이

## 다음 단계
1. 계획 파일 순서대로 스텝 14부터 진행한다. 스텝마다 커밋하고 `docs/learning/steps/`에 설명을 남긴다.

## 직접 해보기
1. `./gradlew --version`을 실행해 `Daemon JVM` 줄에 `Compatible with Java 17`이 보이는지 확인한다.
2. `git ls-files --eol gradlew script/e2e-check.sh`로 `eol=lf` 속성이 붙었는지 확인한다.

## 메모
- 결정 근거: Notion 문서 세 개(main 통합 방안, 팀 합의 대조·추가 검토, docs·AI 지침 통합 방안)
- 문서·AI 지침 정비는 PR B로 분리한다(dev 대상).
