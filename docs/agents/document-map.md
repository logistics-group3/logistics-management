# 문서별 기준 위치

KMH의 문서 지도·계약 로그 아이디어와 KEJ의 IDE/Docker 병행 안내를 현재 구현에 맞게 적용한다. 원본 브랜치의 오래된 SA나 모듈별 개인 규칙은 가져오지 않는다.

| 정보 | 기준 위치 | 적용 범위 |
|---|---|---|
| 팀 설계·필수 요구 | `docs/SA/Sparta_logistics_SA_Master .md` | 브랜치에 포함된 v2.0, 멘토 피드백 반영본 |
| 서비스별 실제 설정 | `config-repo/` | 서비스 이름·DB·Gateway 라우팅·통신 timeout |
| 개발 환경·실행·검증 | `README.md`, `.env.example`, `script/` | Docker·IDE 병행과 공통 인프라 확인 |
| 구현 선택의 근거 | `docs/decisions/` | 원작성 브랜치 결정과 팀 리뷰 상태를 구분 |
| 작업 인계 | `docs/progress/<브랜치명에서 /를 -로 바꾼 이름>.md` | 해당 브랜치의 실제 수행·검증 |
| 통합 출처·제외 근거 | `docs/reviews/base-setting-integration.md` | 이번 통합 후보의 비교와 검증 |

## 서비스 간 계약

| 제공자 | 현재 제공 | 의존자·전제 |
|---|---|---|
| Config Server | 서비스 설정 | 클라이언트는 config import 실패 시 기동 실패 |
| Gateway | JWT·사용자 재확인, `X-User-*` 헤더 정리·주입, 내부 API 외부 차단 | MVC 서비스는 외부에 직접 노출하지 않는다 |
| user-service | 가입·로그인·내부 사용자 조회 | Gateway가 실제 role·탈퇴 상태를 재확인한다 |
| common-core | 역할·헤더 이름·응답·오류 계약 | Gateway(WebFlux)와 common(MVC)이 공유한다 |
| common | Audit·현재 사용자·역할 검사·Feign·페이지 규칙 | 업무 서비스는 API별 비즈니스 인가·정렬 허용 목록을 구현한다 |
| PostgreSQL 초기화 | 스키마·전용 계정 | 빈 볼륨에서 한 번, 테이블 변경은 Flyway |

## 계약 변경 기록

계약 변경 시 담당 브랜치의 progress 또는 별도 ADR에 상태와 근거를 기록한다. `OPEN`은 확인 필요, `DECIDED`는 결정자와 합의 근거가 있는 상태, `DONE`은 구현과 검증까지 확인한 상태다. 브랜치 작성자가 상태만 표시한 기록을 팀 합의 증거로 취급하지 않는다.

현재 통합 후보의 main 반영은 `OPEN`이다. 업무 서비스 CRUD·검색·Saga는 이 공통 인프라 통합의 구현 완료 범위에 포함하지 않는다.
