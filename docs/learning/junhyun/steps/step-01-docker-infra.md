# 스텝 1. Docker 기반 로컬 인프라

- 날짜: 2026-10-13
- 커밋: feat: Docker 기반 로컬 인프라 구성
- 결과: `docker compose up -d --build` 한 번으로 postgres가 healthy, 서비스 6개(gateway + 비즈니스 5개)가 Eureka에 UP으로 등록 (완료 기준 1)

## 만든 것
| 파일 | 역할 |
|---|---|
| `docker-compose.yml` | postgres, eureka, gateway, 서비스 5개. 공통 설정은 YAML 앵커(`x-app`, `x-db-service`)로 묶어 반복을 줄였다 |
| `infra/postgres/init.sql` | 스키마 5개, 서비스별 전용 계정, 계정별 `search_path` 고정 |
| `Dockerfile` | 모든 모듈 공용 템플릿. `ARG MODULE`로 모듈 jar를 고른다 |
| `.dockerignore` | jar만 빌드 컨텍스트로 보내 빌드를 빠르게 한다 |
| `.env.example` | DB 비밀번호, JWT secret. 실제 값은 `.env`(Git 제외) |

## 리뷰 포인트
1. **기동 순서**: `depends_on`에 `condition: service_healthy`를 써서 postgres가 실제로 접속을 받을 수 있을 때 서비스가 뜬다. `service_started`는 컨테이너가 시작만 됐다는 뜻이라 DB 준비를 보장하지 않는다.
2. **IP로 Eureka 등록**: 컨테이너 호스트명(컨테이너 ID)으로 등록하면 Gateway가 다른 컨테이너를 찾지 못할 수 있다. 그래서 IP로 등록한다(스텝 2부터는 `EUREKA_PREFER_IP` 자리표시자).
3. **같은 jar, 다른 환경**: `application.yml`의 로컬 기본값을 compose 환경 변수가 덮어쓴다. 그래서 같은 jar가 로컬과 컨테이너에서 모두 동작한다. 스텝 2에서 이 방식은 자리표시자 방식으로 바뀌었다.
4. **init.sql은 한 번만 실행**: postgres 이미지는 데이터 볼륨이 비어 있을 때만 초기화 SQL을 실행한다. 고친 뒤에는 `docker compose down -v`로 볼륨을 지우고 다시 띄워야 반영된다.

## 측정
- 전체 컨테이너 메모리 약 3.1GB(eureka 495MB, 서비스 각 405~450MB, gateway 373MB, postgres 118MB)

## 관련
- 질문 노트: [Eureka 설정](../qna/2026-10-13-eureka-server-setup.md), [작업 환경 무게](../qna/2026-10-13-multimodule-workspace-weight.md)
