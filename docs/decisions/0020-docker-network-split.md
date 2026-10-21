# 0020. 로컬 Docker 네트워크를 앞단과 데이터 두 개로 나눈다

- 상태: 제안
- 날짜: 2026-10-21
- 결정자: kmh (팀 리뷰 대기)

## 맥락
- `docker-compose.yml`은 bridge 네트워크 `logistics-net` 하나에 컨테이너를 모두 붙였다. 외부 노출은 호스트 포트 공개 범위(Gateway 8080만 `0.0.0.0`, 관리 도구는 `127.0.0.1`)로 막았지만, 컨테이너끼리는 모두 서로 닿는다.
- 그래서 DB에 접근할 이유가 없는 Gateway, Zipkin, Prometheus, Grafana도 `postgres:5432`에 TCP로 닿는다. 막는 것은 서비스별 DB 계정·스키마 권한(`infra/postgres/init.sh`)뿐이다.
- SA 부록의 Compose 예시와 서버 설계(`docs/design/05-infra.md`)는 네트워크 하나(`logistics-net`)를 쓴다. 이 결정은 SA와 다르므로 기록한다. 사용자 확인: 2026-10-21, DB 호스트 포트(`127.0.0.1:5432`)는 유지.

## 결정
네트워크를 두 개로 나눈다.

| 네트워크 | 붙는 컨테이너 | 대역 |
|---|---|---|
| `logistics-net` (앞단) | gateway, config, eureka, zipkin, prometheus, grafana, 업무 서비스 5개, (측정) redis·k6, (측정) slack-stub | `LOGISTICS_NET_SUBNET`, 기본 `10.210.10.0/24` |
| `logistics-data` (데이터) | 업무 서비스 5개, postgres | Docker 자동 할당 |

- 업무 서비스만 두 네트워크에 함께 붙는다. 앞단 이름을 `logistics-net`으로 유지해 k6(`<프로젝트>_logistics-net`), 측정용 redis, slack-stub 설정은 바꾸지 않는다.
- Redis는 데이터 저장소지만 앞단에 둔다. Gateway가 권한 캐시를 읽고 user-service가 키를 지우므로 둘 다 닿아야 한다(ADR 0019 Redis 캐시).
- 업무 서비스는 IP가 둘이라 Eureka에 데이터 쪽 IP가 등록될 수 있다. 그러면 Gateway가 그 서비스에 연결하지 못한다. `SPRING_CLOUD_INETUTILS_PREFERREDNETWORKS`(접두어 일치)로 앞단 대역 IP만 등록하고, 그러려고 앞단 대역을 고정한다.
- 대역은 Docker 기본 풀(172.17~31, 192.168)과 겹치지 않는 `10.210.x`다. 같은 PC에서 함께 띄우는 스택은 대역을 나눈다: 개발 `10.210.10`, 성능 측정 `10.210.20`(`script/perf.sh`), UI 테스트 `10.210.30`(`script/ui-test.sh`).
- `logistics-data`는 `internal: true`로 만들지 않는다. internal 네트워크에만 붙은 컨테이너는 호스트 포트를 공개할 수 없어 `127.0.0.1:5432`(psql·IDE 접속)가 사라진다.

## 검토한 대안
- 네트워크 하나 유지: 변경이 없지만 Gateway·관측 도구가 침해되면 DB까지 네트워크로 닿는다.
- web(Gateway+서비스)/was(서비스+DB) 이름: 이 구조의 Gateway는 화면을 주는 web 서버가 아니라 API 진입점이라 앞단/데이터가 실제 역할에 맞다. 나누는 방식은 같다.
- `logistics-data`를 `internal: true`로: 바깥으로 나가는 연결까지 막아 더 단단하지만 DB 호스트 포트를 잃는다. 로컬 개발 편의를 택했다.
- Eureka에 IP 대신 호스트 이름 등록: 컨테이너 호스트 이름(컨테이너 ID)의 DNS 해석이 Docker 버전과 네트워크에 따라 달라 이미 IP 등록(`EUREKA_PREFER_IP=true`)으로 바꾼 이력이 있다. 대역 고정이 결정적이다.
- 네트워크 연결 순서(`priority`)로 첫 인터페이스를 정하기: Spring의 `InetUtils`는 인터페이스 인덱스가 가장 낮은 것을 고르는데, 컨테이너 안 인덱스가 연결 순서와 같다는 보장이 없다.

## 결과
- Gateway, config, eureka, zipkin, prometheus, grafana는 postgres에 네트워크로 닿지 않는다.
- 대역을 고정했으므로 PC의 다른 네트워크(VPN 등)와 `10.210.x`가 겹치면 `.env`의 `LOGISTICS_NET_SUBNET`·`LOGISTICS_NET_PREFIX`를 함께 바꿔야 한다.
- 이미 만든 `.env.perf`·`.env.ui-test`는 고칠 필요가 없다. 스크립트가 대역을 넣는다.
- 기존 스택은 `docker compose down` 뒤 다시 올려야 새 네트워크로 만들어진다. DB 볼륨은 그대로 쓴다.
- 서버(Swarm overlay) 설계에 같은 분리를 적용할지는 `docs/design/05-infra.md` 담당 범위에서 따로 정한다.
- SA 부록 Compose 예시는 팀 합의 문서라 고치지 않았다. 채택되면 SA 갱신을 함께 논의한다.
