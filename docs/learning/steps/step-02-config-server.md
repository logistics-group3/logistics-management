# 스텝 2. config-server로 설정 중앙화

- 날짜: 2026-10-13
- 커밋: `9693c14 feat: config-server로 서비스 설정 중앙화`
- 결과: 서비스 6개가 config-server에서 설정을 받아 기동(로그 `Fetching config from server at : http://config-server:8888`, 포트 8081 반영). Gateway를 거쳐 user-service까지 라우팅 확인. config-server 테스트 통과

## 바뀐 구조
```
config-repo/
├── application.yml              모든 서비스 공통: eureka, jpa, feign 타임아웃(5초/20초), actuator
├── gateway-service.yml          포트, 라우팅, 타임아웃(60초)
└── {user,hub,...}-service.yml   포트, datasource, 스키마
config-server/                   포트 8888, config-repo를 jar 안 classpath:/config-repo 로 포함
{각 서비스}/application.yml      이름 + spring.config.import 두 줄만 남음
```

## 리뷰 포인트
1. **우선순위**: Config Server가 내려준 값은 기본적으로 클라이언트의 환경 변수보다 우선한다(`spring.cloud.config.override-system-properties` 기본값 true). config-repo에 `localhost` 같은 고정값을 쓰면 compose에서 넘긴 환경 변수가 무시된다. 그래서 환경마다 달라지는 값은 `${DB_HOST:localhost}`처럼 자리표시자로 두었고, 자리표시자는 클라이언트 쪽 환경 변수로 풀린다. compose는 `DB_HOST`, `EUREKA_URL`, `EUREKA_PREFER_IP`, `CONFIG_SERVER_URL`과 비밀번호만 넘긴다.
2. **native 백엔드, jar 포함**: 별도 Git 저장소 없이 루트 `config-repo/`를 `processResources`로 jar에 넣었다. 설정을 바꾸면 config-server만 다시 빌드한다. 재빌드 없이 실험하려면 `CONFIG_REPO_LOCATION=file:/절대경로/config-repo`로 덮어쓴다.
3. **`optional:configserver:`**: config-server가 없어도 서비스가 기동을 시도한다. 테스트에서 막히지 않게 하려는 선택이다. 대신 compose에서는 config-server healthcheck(actuator) 통과 후에 서비스가 뜨도록 순서를 걸었다.
4. **직후 503**: 서비스를 띄운 직후 Gateway 호출이 503이었다가 약 10초 뒤 정상이 됐다. Gateway가 Eureka 목록을 30초 주기로 받아 오기 때문이다. Eureka 동작 방식이 실제로 나타난 장면이다.
5. eureka-server는 config-server를 쓰지 않는다. 기동 순서를 단순하게 두기 위해서다.

## 면접 포인트
- Config Server를 쓰는 이유: 설정 중복 제거, 환경별 설정 분리, 재배포 없는 설정 변경(`@RefreshScope`, Spring Cloud Bus)
- 원격 설정과 환경 변수 중 무엇이 이기는가, 그리고 그 이유
