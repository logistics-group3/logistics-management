# 05. 인프라 · 배포 설계

> 최종 수정: 2026-10-14 · 그림: [`05-infra-architecture.pdf`](05-infra-architecture.pdf) (01~06장)
> 이 문서는 PDF의 텍스트판이자 **결정 기록**이다. 그림은 PDF, 결정과 절차는 이 문서가 기준.
>
> 상태 — ✅ 결정됨 · 📝 설계(미구현) · ❓ 결정 필요 · 구현하면 📝 → ✅로 바꾸고 CHANGELOG에 남긴다.

## 0. 요약

| 항목 | 선택 | 상태 |
|---|---|---|
| 클라우드 | GCP Compute Engine, asia-northeast3(서울) | 📝 |
| 오케스트레이션 | Docker Swarm (VM 4대: edge · app · data · monitor) | 📝 |
| 진입점 | Caddy :443 (TLS) — `/` → frontend, `/api/*` → gateway | 📝 |
| 이미지 저장소 | GCP Artifact Registry, tag = commit SHA | 📝 |
| CI/CD | GitHub Actions 1개 워크플로 — 바뀐 서비스만 빌드·배포 | 📝 |
| 무중단 배포 | Swarm rolling update `start-first` + healthcheck + 실패 시 자동 rollback | 📝 |
| 설정 | 로컬 native / 서버 Git 백엔드(브랜치별) + 자동 refresh | 로컬 ✅ · 서버 📝 |
| DB | 서비스별 Postgres 컨테이너, data 노드 고정 | 로컬 ✅ · 서버 📝 |
| DB 스키마 변경 | ❓ Flyway vs 직접 DDL (§7) | ❓ |
| 백업 | 매일 pg_dump → Cloud Storage 7일 + PD 스냅샷 | 📝 |
| 관측성 | Prometheus · Grafana · Loki · Tempo → Slack 알림 | 📝 |

---

## 1. 환경

| 환경 | 어디서 | 실행 | 설정(config) | 이미지 |
|---|---|---|---|---|
| **local** | 각자 PC | `docker-compose.yml` (`script/run.sh`) | native — 내 PC `config/` 폴더, 저장 즉시 refresh | 로컬 빌드 |
| **dev** | ❓ (§9) | Swarm stack | Git 백엔드 — `dev` 브랜치 | Artifact Registry `:<sha>` |
| **prod** | GCP 4노드 | Swarm stack | Git 백엔드 — `main` 브랜치 | Artifact Registry `:<sha>` |

- 로컬용 `docker-compose.yml`은 **로컬 전용**으로 유지한다 (소스 빌드, config 볼륨, DB 포트 공개, 로컬 기본 비밀번호)
- 서버는 별도 **stack 파일**(`deploy/stack.yml`)을 쓴다 (§4)

## 2. 노드 구성 (PDF 01 · 06)

```
                 ┌────────────── VPC logistics-vpc 10.0.1.0/24 ──────────────┐
Browser ─443─▶   │ edge     Caddy · frontend · gateway · eureka · config     │  공인 IP 1개
                 │          (Swarm manager)                                  │
                 │ app      user · hub · company · order · delivery         │  내부 전용, stateless
                 │ data     user-db · hub-db · company-db · order-db · delivery-db │ 내부 전용, PD 볼륨
                 │ monitor  Prometheus · Grafana · Loki · Tempo              │  내부 전용, IAP 접속
                 └───────────────── Docker Swarm overlay: logistics-net ───────┘
```

| 노드 | 머신 | 이유 |
|---|---|---|
| edge | e2-medium · 4GB | 외부와 닿는 유일한 노드. 진입점·인프라 서비스 |
| app | e2-standard-2 · 8GB | 상태 없는 서비스만 → 재배포·메모리 폭주가 DB에 영향 없음 |
| data | e2-medium · 4GB | 상태(DB)만. `placement: node.labels.role == data`로 고정 |
| monitor | e2-medium · 4GB | 감시하는 쪽을 감시받는 쪽과 분리 |

메모리: JVM `-XX:MaxRAMPercentage=75 -XX:+UseSerialGC`, Swarm `deploy.resources.limits.memory`로 제한. 수치는 `docker stats` 실측 후 조정.

## 3. 네트워크 · 보안 (PDF 06)

| 규칙 | 출발지 | 대상 · 포트 |
|---|---|---|
| allow-https | 0.0.0.0/0 | edge · tcp 80, 443 |
| allow-iap-ssh | 35.235.240.0/20 (IAP) | 전체 · tcp 22 |
| allow-swarm | 10.0.1.0/24 | 전체 · tcp 2377, 7946 · udp 7946, 4789 |
| allow-internal | 10.0.1.0/24 | overlay 내부 서비스 · DB |
| 그 외 | - | 인바운드 전부 차단 |

- 외부에 열린 것은 **edge의 443(80→443 리다이렉트)** 뿐. gateway 8080도 호스트에 publish하지 않고 Caddy만 접근
- **actuator 노출 문제 해소**: Caddy는 `/api/*`만 gateway로 넘기므로 gateway의 `/actuator/**`는 외부에서 닿지 않는다 (로컬에서 남았던 "gateway 관리 포트 분리" 과제는 서버에서는 Caddy 라우팅으로 대체, `12-config-ops` §3.3)
- DB·Grafana 접속은 IAP 터널. SSH도 IAP로만
- 비밀값(DB 비밀번호, `JWT_SECRET`, Slack webhook, MASTER 계정)은 **Swarm secret**. 이미지·레포·stack 파일에 넣지 않는다

## 4. 배포 파일 (`deploy/stack.yml`) 📝

로컬 `docker-compose.yml`과 다른 점:

| 항목 | 로컬 compose | 서버 stack |
|---|---|---|
| 이미지 | `build:` | `image: <registry>/<service>:${TAG}` |
| config-server | native + `./config` 볼륨 | Git 백엔드, 볼륨 없음 (§6) |
| 외부 포트 | gateway·eureka·config·DB | Caddy 443만 |
| 도메인 서비스 | `profiles: [domain]` 선택 | 항상 전부 |
| 비밀값 | `.env` + 로컬 기본값 | Swarm secret, 기본값 없음 |
| 기동 순서 | `depends_on` | **Swarm은 depends_on을 무시** → config client `fail-fast` + retry, `restart_policy` |
| 재시작·배포 | 없음 | `restart_policy`, `update_config: order: start-first, failure_action: rollback` |
| 배치 | - | `placement` (DB → data, 서비스 → app, 모니터링 → monitor) |
| DB DDL | `doc/sql` 자동 마운트 | **마운트 안 함** (§7) |
| DB 볼륨 | compose 볼륨 | 이름 고정 볼륨 (`external: true`, §8) |
| Spring 프로파일 | `docker` | `docker,dev` / `docker,prod` |

Swarm 운영 시 주의 (PDF 06):

| 문제 | 대응 |
|---|---|
| depends_on 무시 | config client `fail-fast: true` + retry, `restart_policy` |
| Eureka에 엉뚱한 IP 등록 | `spring.cloud.inetutils.preferred-networks` = overlay 대역 |
| DB 컨테이너가 다른 노드로 이동 | `placement: node.labels.role == data` |
| 종료 시 요청 유실 | `server.shutdown=graceful` + LoadBalancer retry |

## 5. CI/CD (PDF 04) 📝

```
PR            → 빌드 · 테스트만
머지(push)    → ① changes  ② build(matrix)  ③ push  ④ deploy  ⑤ Swarm rolling update
```

| 단계 | 내용 |
|---|---|
| ① changes | paths-filter로 재빌드 대상 계산 (아래 표) |
| ② build | 백엔드 `bootJar`, 프론트 `npm run build`, `docker build` 병렬 |
| ③ push | Artifact Registry, tag = commit SHA (롤백 = 이전 SHA) |
| ④ deploy | IAP SSH → Swarm manager(edge) → `docker service update` (대상만) |
| ⑤ Swarm | `start-first`: v2 기동 → health OK → Eureka 등록 → v1 종료. health 실패 시 v1 유지 + 자동 rollback |

| 바뀐 경로 | 재빌드 · 재배포 | 이유 |
|---|---|---|
| `backend/{service}/**` | 그 서비스 1개 | 독립 배포 |
| `backend/common/**` | 비즈니스 서비스 5개 | common.jar가 각 jar에 포함 |
| `build.gradle`, `gradle/**` | 전체 | 버전·플러그인 공통 |
| `frontend/**` | frontend 1개 | 정적 파일만 교체 |
| `config/**` | **없음** | config-server가 Git에서 읽고 자동 refresh (§6) |

배포 전 체크: ① 스키마 변경이 먼저 적용됐나 (§7) ② API 변경 시 호출받는 쪽 먼저 배포, 필드는 추가만 ③ common 변경은 하위 호환 ④ 배포 후 5분 Grafana 에러율·p95 확인, 이상하면 이전 SHA로 rollback

❓ 브랜치별 배포 대상 — PDF는 `main` → 운영만. dev 환경을 둘지에 따라 `dev` 브랜치 → dev 배포 추가 (§9)

## 6. 설정 관리 (Config Server)

| 환경 | 읽는 곳 | 바꾸는 법 | 반영 |
|---|---|---|---|
| local | 내 PC `config/` (native) | 파일 저장 | 2~4초, 자동 refresh ✅ |
| dev | GitHub `dev` 브랜치 `config/` | PR을 dev에 머지 | 30초~1분 📝 |
| prod | GitHub `main` 브랜치 `config/` | dev → main 머지 | 30초~1분 📝 |

```
config-server (같은 이미지)
  CONFIG_BACKEND=native (기본)  → search-locations=file:/config/
  CONFIG_BACKEND=git            → uri=<이 레포>, search-paths=config,
                                  default-label=${CONFIG_GIT_BRANCH}, clone-on-start, basedir=/tmp/config-repo
변경 감지
  native: config/ 폴더 해시 비교 (구현됨)
  git:    30초마다 fetch → basedir의 config/ 해시 비교 → 같은 refresh 로직 (📝)
```

- 별도 설정 레포를 두지 않고 **이 레포의 `config/`**를 읽는다. 퍼블릭이라 토큰 불필요 (private 전환 시 토큰을 secret으로)
- 브랜치 = "어느 시점의 설정"(검증 중 / 확정), 프로파일 파일(`application-dev.yml` / `-prod.yml`) = "어느 환경의 값"
- refresh로 안 바뀌는 설정(포트·DB·JWT 키·springdoc)은 서버에서도 `docker service update --force {service}`로 재시작 (`12-config-ops` §3.2)
- PDF 04의 "config/** → 파일 동기화"는 이 Git 백엔드 방식으로 대체

## 7. DB 스키마 변경 ❓

| | A. Flyway (PDF 04) | B. 직접 DDL (2026-10-14 논의) |
|---|---|---|
| 방식 | 서비스 기동 시 `db/migration/V*__*.sql` 자동 적용 | 사람이 서버 DB에 SQL 실행 |
| 장점 | 배포와 스키마가 항상 같이 감. 적용 이력이 DB에 남음. expand/contract와 자연스럽게 맞음 | 도입 비용 없음. 실행 전 눈으로 확인 |
| 단점 | 도입 작업 (서비스별 의존성·폴더), 잘못된 마이그레이션도 자동 실행 | 순서 실수 시 서비스 기동 실패(`ddl-auto: validate`), 서버별 적용 여부 추적이 수동 |

**공통 원칙 (어느 쪽이든)**
- **expand → 코드 배포 → contract**: 컬럼은 nullable로 추가 → 새 코드 배포 → 옛 컬럼 삭제는 다음 배포
- dev DB에 먼저 적용, 확인 후 prod
- 적용 전 수동 백업 (§8)

**B를 택할 경우 절차**
```bash
# 최초: DB만 띄우고 → DDL → 나머지
docker service ... (DB만)
docker exec -i <user-db 컨테이너> psql -U user_svc -d user_db < 01-user.sql
# 변경: ALTER를 먼저 실행 → 그다음 코드 머지
```
- `doc/sql/{NN}-{service}.sql` = 현재 전체 모습 (로컬 자동 실행용)
- `doc/sql/changes/YYYY-MM-DD-{service}-{내용}.sql` = 서버에 적용할 변경분 + 상단 주석에 dev/prod 적용 일시
- ALTER는 `BEGIN; ... COMMIT;`으로 감싸 확인 후 커밋

## 8. 데이터 보호 · 백업 📝

**데이터가 날아갈 수 있는 경우와 대응**

| 경우 | 대응 |
|---|---|
| 볼륨 삭제 (`down -v`, `volume prune`) | 볼륨을 `external: true` + 고정 이름으로 → stack/compose가 지우지 못함. 서버 금지 명령 지정 |
| 스택·프로젝트 이름 변경으로 빈 볼륨 생성 | 고정 이름 볼륨 |
| 잘못된 DDL | 적용 전 수동 백업, 트랜잭션, dev 선적용 |
| VM·디스크 장애 | Cloud Storage 백업 + PD 스냅샷 |

**백업 정책**

| 항목 | 정책 |
|---|---|
| 대상 | DB 5개 |
| 주기 | 매일 03:00 자동 + DDL 적용 직전 수동 (이름표) |
| 형식 | `pg_dump -Fc` (압축, 테이블 단위 복구 가능) |
| 저장 | data 노드 디스크(빠른 복구) + Cloud Storage(노드 장애 대비) |
| 보관 | 노드 7일 · Cloud Storage 7일(수명 주기 규칙) · PD 스냅샷 일 1회 |
| 실패 알림 | Slack |
| 복구 리허설 | 월 1회 dev(또는 새 볼륨)에 복원해 보고 문서화 |
| 보안 | 버킷 비공개·암호화 (백업에 개인정보 포함) |

만들 것: `script/db-backup.sh` (5개 덤프 → 업로드 → 오래된 것 정리 → 실패 알림), `script/db-restore.sh` (복원 전 현재 상태 백업), data 노드 cron.

**서버 금지 명령**: `docker compose down -v`, `docker volume prune`, `script/run.sh --reset`, `docker stack rm` 후 볼륨 정리

## 9. 결정 필요 ❓

| # | 항목 | 선택지 | 비고 |
|---|---|---|---|
| 1 | DB 스키마 변경 | Flyway / 직접 DDL | §7. PDF는 Flyway, 10/14 논의는 직접 DDL |
| 2 | dev 환경 | 별도 VM / 같은 Swarm에 `-dev` 스택 / 두지 않음(main만) | 같은 Swarm이면 Caddy에서 `dev.도메인` 분기, 자원 2배 |
| 3 | 비밀값 주입 | Swarm secret / `.env` | Swarm secret은 파일로 마운트 → Spring `configtree:` 또는 `_FILE` 처리 필요 |
| 4 | config 브랜치 반영 주기 | 30초 / 1분 | fetch 주기, GitHub API 제한 고려 |

## 10. 진행 순서 (PDF 06, ~10/22)

| 순서 | 작업 | 상태 |
|---|---|---|
| ① | Terraform: VPC · 방화벽 · VM 4대 · 버킷 | 📝 |
| ② | Swarm 구성 · `deploy/stack.yml` 배포 | 📝 |
| ③ | GitHub Actions 파이프라인 | 📝 |
| ④ | start-first · healthcheck · graceful shutdown | 📝 |
| ⑤ | Prometheus + Grafana + Slack 알림 | 📝 |
| ⑥ | 스키마 변경 방식 확정 (§7) | ❓ |
| ⑦ | config-server Git 백엔드 + 브랜치 선택 (§6) | 📝 |
| ⑧ | 백업 스크립트 · cron · 복구 리허설 (§8) | 📝 |
| ⑨ | 프론트 MVP 화면 4개 | 📝 |
| 여유 | JWT → httpOnly 쿠키, Loki · Tempo, 배포 중 k6 무중단 검증 | |
| 안 함 | GKE / k8s 전환 → ADR에 다음 단계로 기록 | |
