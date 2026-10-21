# feat/infra-network-split

## 지금 하는 일
로컬 Docker 네트워크를 앞단(logistics-net)·데이터(logistics-data)로 분리, 커밋·PR 전

## 진행 상태
- [x] `docker-compose.yml`: postgres를 `logistics-data`에만, 업무 서비스는 두 네트워크에 붙임. 앞단 대역 `10.210.10.0/24` 고정, `SPRING_CLOUD_INETUTILS_PREFERREDNETWORKS`로 Eureka에 앞단 IP만 등록. DB 호스트 포트 `127.0.0.1:5432` 유지(사용자 결정)
- [x] `script/perf.sh`(10.210.20), `script/ui-test.sh`(10.210.30): 함께 띄우는 스택끼리 대역 분리. `.env.example`에 선택 값 주석 추가
- [x] ADR 0020, `docs/design/local-network.md`, `local-run.md`, `config-security.md`, `performance-test.md`, `frontend/README.md` 갱신. `script/docs-check.sh` OK
- [x] 개발 스택 기동 확인(2026-10-21): Eureka 6개 UP, 등록 IP 모두 10.210.10.x. 마스터 로그인 후 users/hubs/companies/products/orders/deliveries 조회 200, 토큰 없음 401, `/internal` 404. Gateway·Prometheus·Zipkin에서 `postgres` 이름 해석 안 됨(Gateway TCP 5432 BLOCKED), user-service는 해석됨. Prometheus 대상 7개 모두 up
- [ ] `script/run.sh --check`(e2e-check.sh) 통과 확인: 이 PC는 `python3`가 Windows 스토어 별칭이라 JSON 파싱이 비어 "등록: []"로 실패한다. 변경과 무관한 실행 환경 문제이며, 같은 항목을 curl로 직접 확인했다(위 항목). Python이 있는 환경에서 다시 돌린다
- [ ] 성능 측정(`script/perf.sh up`)·UI 테스트(`script/ui-test.sh up`) 스택 실제 기동: 미확인. Compose 설정 렌더링(대역 10.210.20, preferred 접두어)과 `bash -n`만 확인
- [ ] 팀 전체 리뷰(docker-compose.yml 변경), ADR 0020 채택 여부
- 면접 포인트: 네트워크 분리로 얻는 심층 방어, 다중 네트워크 컨테이너가 서비스 디스커버리에 잘못된 IP를 등록하는 문제와 해결, Docker internal 네트워크와 포트 공개의 관계

## 다음 단계
1. Python이 있는 환경에서 `script/run.sh --check` 실행해 완료 기준 6개 통과 확인
2. `script/perf.sh up`, `script/ui-test.sh up`을 개발 스택과 동시에 띄워 대역 충돌이 없는지, k6가 Gateway에 닿는지 확인
3. 커밋(`chore(infra): Docker 네트워크를 앞단과 데이터로 분리`)과 PR, 팀원 전체 리뷰 요청

## 직접 해보기
1. 기존 스택 내리기: `docker compose down` (DB 볼륨은 남는다)
2. 다시 올리기: `script/run.sh`
3. 네트워크 확인: `docker network inspect base-setting_logistics-data -f '{{range .Containers}}{{.Name}} {{end}}'` → 업무 서비스 5개와 postgres만 보이면 성공
4. 차단 확인: `docker compose exec gateway-service getent hosts postgres` → 아무것도 출력되지 않으면 성공. `docker compose exec user-service getent hosts postgres` → IP가 나오면 성공
5. Eureka 등록 IP 확인: 브라우저로 http://localhost:8761 → 인스턴스 링크의 IP가 모두 `10.210.10.`으로 시작하면 성공
6. DB 접속 유지 확인: IntelliJ Database 또는 `psql -h 127.0.0.1 -p 5432 -U postgres logistics`로 접속되면 성공

## 메모
- 결정: `docs/decisions/0020-docker-network-split.md`
- 학습 노트: `docs/learning/kmh/qna/2026-10-21-docker-network-split.md`
- SA 부록 Compose 예시(네트워크 하나)는 팀 합의 문서라 고치지 않았다. 채택되면 함께 논의한다
- 이 브랜치는 dev의 커밋되지 않은 문서 변경(학습 노트 등)을 그대로 가지고 나왔다. 커밋할 때 이 작업 파일만 골라 담는다
