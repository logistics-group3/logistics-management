# 0004. API 문서(springdoc)와 QueryDSL 라이브러리 선택

- 상태: 채택
- 날짜: 2026-10-13
- 결정자: junhyun (공통 인프라 개인 구현안)

## 맥락
- 범위 문서 3절 "시간이 남으면": QueryDSL 공통 설정, Swagger(springdoc) 공통 설정
- SA 3.5: 각 서비스 springdoc 문서를 Gateway Swagger UI에서 서비스별로 선택해 조회한다.
- 2026-10-13 Maven Central 기준: springdoc 최신은 3.1.1(Boot 4용), Boot 3.x용 최신은 2.8.17. `com.querydsl` 5.1.0은 2024-01 이후 릴리스가 없고, OpenFeign 포크 `io.github.openfeign.querydsl`은 7.7(2026-09)까지 이어진다.

## 결정
- springdoc 2.8.17: 서비스는 `springdoc-openapi-starter-webmvc-api`(common이 `api`로 노출), Gateway는 `springdoc-openapi-starter-webflux-ui`. Gateway가 `/{서비스}/v3/api-docs`를 각 서비스 `/v3/api-docs`로 라우팅한다.
- QueryDSL: `io.github.openfeign.querydsl` 7.7. `querydsl-jpa`는 common이 `api`로 노출하고, Q클래스 생성(apt)은 루트 build.gradle에서 common과 비즈니스 서비스에 한 번에 건다. `JPAQueryFactory` 빈은 common의 `QuerydslConfig`
- 버전은 `gradle.properties`(`springdocVersion`, `querydslVersion`)

## 검토한 대안
- `com.querydsl` 5.1.0: 자료가 많지만 관리가 멈췄고, 5.x에는 정렬 조건 처리 관련 취약점 보고(CVE-2024-49203)가 있다. 포크는 패키지명(`com.querydsl.*`)과 API가 같아 학습 자료를 그대로 쓸 수 있다.
- springdoc 3.x: Spring Boot 4 전용이다.
- 서비스마다 Swagger UI를 따로 띄우기: 서비스 포트를 외부에 열어야 하고 SA 3.5와 다르다.

## 결과
- 서비스 담당자는 Entity만 만들면 Q클래스가 생기고, `JPAQueryFactory`를 바로 주입받는다.
- 서비스 문서는 http://localhost:8080/swagger-ui.html 에서 서비스를 골라 본다. 내부 API(`/internal/**`)는 문서에서 뺐다.
