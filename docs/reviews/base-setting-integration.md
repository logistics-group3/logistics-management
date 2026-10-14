# 공통 인프라 브랜치 선별 통합

- 날짜: 2026-10-14 (KST)
- 통합 브랜치: `codex/base-setting-integration`
- 시작점: `origin/main`, `993b4e12d5488dd1b53d039bc4212cc697f50553` (Initial commit)
- 범위: 공통 인프라·인증 기반 선별, 구현, 로컬 검증, 로컬 커밋, PR 초안. 원격 push·main 변경은 제외한다.

## 비교 대상과 출처

`git fetch origin` 후 아래 SHA를 기준으로 비교했다. 로컬 `junhyun/base-setting`과 원격 브랜치는 동일하다. fetch에서 추가 발견한 `feature/seungkeun`도 비교했다.

| 브랜치 | SHA | 주된 판단 |
|---|---|---|
| 로컬·origin/junhyun/base-setting | `8d3221411acd92a0515a8a83b9dda4f8c645beb8` | 현재 SA와 맞는 구현·회귀 테스트를 기반으로 채택 |
| origin/haneol/base-setting | `87a6c3fef233b2b62519554a960730463158c71b` | 공용 사용자 헤더 검증 방식 채택 |
| origin/kej/base-setting | `df6d412db4befd3b24b3d0b6baf3191f7af7e1c0` | 반복 논리 삭제 방어, 개발 환경 안내 채택 |
| origin/kmh/base-setting | `d6a1f4c701e070db3325ba4c031a03a1653a224f` | 환경 변수 DB 초기화, 문서 정본·계약 로그 형식 채택 |
| origin/feature/seungkeun | `cbcabcb918aec77a89f722827c0bb67098d487ad` | public 스키마 접근 제거 채택 |

main 대비 변경뿐 아니라 브랜치끼리의 중복·차이를 비교했다. 원본 작업 트리에는 `.env`, 캐시·빌드 산출물·IDE 설정이 남아 있었으며, 미커밋 신규 소스 차이는 없었다. 이 파일들을 통합 worktree로 복사하지 않았다.

## 채택·제외·보류 근거

| 영역 | 후보·출처 | 장단점 | 결정·의존성 |
|---|---|---|---|
| 빌드·모듈 | junhyun `build.gradle`, `settings.gradle`, `gradle.properties` | Java 17 toolchain이 컴파일·테스트·실행에 적용되고 버전이 중앙화됨 | 채택. root 모듈 구조와 wrapper를 함께 사용 |
| 서비스 설정·발견 | junhyun `config-server/`, `config-repo/`, `eureka-server/` | 설정 정본 분리, config import 실패 시 기동 실패 | 채택. Docker 건강 상태·서비스 이름·내부 주소를 함께 유지 |
| 웹 공통 계약 | junhyun `common-core/`, `common/` | WebFlux Gateway에 MVC/JPA가 섞이지 않고 응답·오류·헤더 계약은 공유 | 채택. 같은 패키지 계약·서비스 의존성과 기존 테스트 유지 |
| 인증·인가 | junhyun `gateway-service/`, `user-service/` | JWT 필수 claim, 헤더 정리, 내부 API 차단, 제한된 캐시, 동시 가입·PostgreSQL 테스트 존재 | 채택. 실제 Gateway→user 호출·캐시 만료로 검증 |
| 헤더 검증 공유 | haneol `backend/common/.../security/UserContextExtractor.java`, `RoleCheckInterceptor.java` | 역할 검사와 사용자 파라미터의 인증 기준을 통일 | 현재 `Role/UserContext/UserHeaders`에 맞춰 이식. junhyun의 username 필수 계약 유지 |
| 반복 논리 삭제 | KEJ `common/.../entity/BaseEntity.java:40-47` | 같은 Entity의 재호출에서 최초 삭제 시각·삭제자 보존 | guard 채택, 컬럼·JPA 매핑 유지. 동시 트랜잭션 보장까지 확대하지 않음 |
| DB 비밀번호 | KMH `infra/postgres/init.sh` | `.env`와 실제 계정 비밀번호를 같이 사용. 원본은 SQL에 문자열을 직접 삽입 | 방식 채택, quoted heredoc과 psql 변수 인용으로 수정. postgres 환경에 5개 비밀번호 전달 |
| DB 스키마 격리 | seungkeun `docker/postgres/init/01-schemas.sql:3` | public 접근을 명시적으로 제거 | `REVOKE ALL ON SCHEMA public FROM PUBLIC` 채택. 각 서비스는 자기 스키마 소유·접근 유지 |
| 스키마 변경 | junhyun Flyway 설정·user `V1__create_p_users.sql` | 변경 이력과 SA의 CHECK·DEFAULT·부분 인덱스를 관리 | 채택. Hibernate validate와 기존 통합 테스트 유지 |
| 페이징·정렬 | junhyun resolver, KEJ Pageables, haneol/seungkeun QueryDSL helper | 크기·기본 정렬은 중복. 허용 정렬 필드 검증은 유용하나 현재 검색 API가 없음 | 현재 resolver 유지. whitelist helper는 보류, 검색 API 구현 시 API별 검증 필요 |
| 공통 자동 설정 | KEJ MVC/JPA/Feign 분리, seungkeun 조건부 빈 | 라이브러리화 장점이 있으나 현재 스캔이 동작함 | 전면 개편 보류. 현재 필요보다 변경 범위가 큼 |
| DB 5개·설정 refresh | haneol compose·ADR-0002/0006 | 별도 DB·자동 reload 제안. SA 변경 또는 개인 결정으로 명시됨 | 제외. 단일 PostgreSQL+서비스별 스키마, 수동 설정 반영 유지 |
| MASTER 자동 생성 | haneol·KEJ·KMH initializer | 편리하지만 기동 시 계정 생성·기존 사용자 충돌 등의 별도 정책 필요 | 제외. 명시적 `script/promote-master.sh` 유지 |
| 생성 전용 Audit·비동기 알림 | KEJ/seungkeun BaseCreatedEntity, seungkeun AsyncConfig | 사용 대상은 고도화 범위. DiscardPolicy는 실패 로그 요구와 다름 | 제외. 업무 기능·미래 추상화는 추가하지 않음 |
| SA·모듈별 문서 | junhyun v2.0 SA, KMH 이전 SA·모듈 규칙 | 최신 기록은 재고 고도화 이동·배송 3구간·자동 배정. 이전 문서는 다름 | v2.0 유지. 오래된 SA와 선구현 U-03/U-08 제외 |
| 협업 문서 | KMH `Docs/README.md:56-67`, agent 계약 로그, KEJ IDE/Docker 안내 | 정보별 정본·상태와 개발 환경 안내는 유용 | `docs/agents/document-map.md`와 README에 현재 구현 기준으로 재작성 |
| 개인 환경 | junhyun `.idea/`, `.claude/`; haneol `org.gradle.java.home` | 개인 도구·학습 패널·타 PC 절대 경로는 공통 실행에 불필요 | 제외. 공통 `.agents` 스킬과 텍스트 규칙 유지. 외부 도구는 선택 사항 |

## 합의와 구현 상태 구분

저장소의 SA v2.0에는 팀 작성자·미팅·멘토 피드백 반영 이력이 명시돼 있다. 이번 작업은 이 기록을 설계 기준으로 삼았으며 외부 Notion·구두 합의를 별도로 확인하지 않았다. 후보 ADR의 `채택`·계약 로그의 `DECIDED` 표기만으로 팀 최종 승인을 추정하지 않는다. 가져온 ADR 0001~0007은 원작성 브랜치의 구현 결정이고, 통합 후보의 main 반영은 팀 리뷰 대상이다.

전체 브랜치를 merge하지 않고 origin/main에서 기반 파일을 선별 추출한 뒤 보완을 이식했다. `.idea`, 개인 `.claude` 설정·패널은 제외했다. 문서의 기존 설정 설명·플러그인 설치 의무를 실제 구성에 맞춰 정리했다. SA 본문은 바꾸지 않고 빈 인용 줄 12개의 후행 공백만 제거했다. Windows wrapper의 내용은 유지하며 `.gitattributes`로 Git LF 저장·checkout CRLF를 지정했다.

## 검증

| 검사 | 명령·방법 | 결과 |
|---|---|---|
| 기반 빌드 | 추출 직후 `./gradlew build --console=plain` | 성공, 48개 태스크 실행 |
| 회귀 재현 | 새 테스트를 기존 RequireRoleInterceptor·BaseEntity 구현으로 실행한 뒤 보완 복원 | 불완전 사용자 컨텍스트 3건·반복 삭제 1건이 예상대로 실패 |
| 최종 Java 빌드 | `./gradlew build --console=plain` | 성공, 테스트 70개·실패 0·오류 0·skip 0 |
| 모듈별 테스트 | JUnit XML 집계 | common 29, common-core 2, gateway 23, user 15, config-server 1 |
| 셸 문법 | `bash -n infra/postgres/init.sh script/db-check.sh script/e2e-check.sh script/promote-master.sh` | 성공 |
| Compose 구성 | `docker compose config -q` | 성공, 비밀값 출력 없음 |
| Docker 빌드·기동 | `docker compose up -d --build` | 8개 앱 이미지 빌드, 9개 컨테이너 기동, postgres/config-server healthy |
| 인증·통합 | `script/e2e-check.sh --with-cache-ttl` | 최종 스크립트에서도 PASS 17 / FAIL 0. 권한 캐시 만료 후 재조회 포함 |
| DB 인증·권한 | `script/db-check.sh` | 계정 5개 PASS. TCP 비밀번호 인증, 자기 스키마 접근, 다른 스키마·public 접근 차단 |
| 비밀번호 인용 | 별도 DB의 USER 비밀번호에 작은따옴표 포함 | 초기화·실제 인증·회원가입/로그인 성공 |
| 잘못된 검증 대상 | 존재하지 않는 COMPOSE_PROJECT_NAME으로 E2E 실행 | HTTP 요청 전 exit 1. 다른 프로젝트 기본 포트로 넘어가지 않음 |
| diff 공백 | `git diff --cached --check` | 성공 |

검증 프로젝트는 `logistics-integration-20261014`이며 포트는 PostgreSQL 15432, Config Server 18888, Eureka 18761, Gateway 18080이다. 새 named volume을 사용했고 기존 `logistics-management` 환경은 사용하지 않았다. E2E가 생성·승격한 검증 계정은 이 별도 DB에만 있다.

검증 후 별도 프로젝트의 컨테이너·네트워크만 종료·제거했고 검증용 DB 볼륨은 보존했다. 원본 checkout의 main SHA·작업 상태·.env 해시는 최초와 동일하고 기존 컨테이너 9개의 ID도 동일함을 확인했다.

독립 검토에서 E2E 포트 조회 실패 시 기본 포트로 넘어가는 문제를 발견해 즉시 종료하도록 수정했다. Java 보완과 DB quoting·권한·Compose 격리 검토에서 추가 수정 필요 항목은 발견하지 않았다.

## 미확인·후속 범위

- 기존 데이터가 있는 DB의 비밀번호 변경·Flyway 이전 스키마 이행은 검증하지 않았다. 초기화 스크립트는 빈 볼륨에만 적용된다.
- IDE 실제 import와 Windows wrapper 실행은 확인하지 않았다.
- hub/company/order/delivery의 업무 CRUD·검색·Saga는 이 통합의 구현 완료 범위가 아니다.
- API별 정렬 허용 목록은 실제 검색 API를 만들 때 구현해야 한다.
- 현재 가입의 DataIntegrityViolationException 매핑은 username 중복 중심이다. 향후 제약을 추가하면 원인별 분류를 검토한다.
- 원격 CI, PR 게시, main merge는 수행하지 않았다.

## main 반영 전 리뷰

1. 기반 구조·버전·common-core 분리·Flyway 선택과 최신 SA 기준을 팀이 확인한다.
2. 환경 변수 DB 초기화의 최초 실행 범위와 기존 DB 이행 방법을 확인한다.
3. 샘플 인증 API는 공통 인프라 검증용이다. 업무 API가 대체하면 E2E와 함께 정리한다.
4. PR 본문은 `docs/reviews/base-setting-integration-pr.md`를 사용한다.
