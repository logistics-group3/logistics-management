# TS-025 기술 블로그 증거 (2026-10-19)

통합 테스트의 PostgreSQL 연결 고갈 사례. 글의 배경은 저장소의 `docs/reviews/2026-10-19-test-connection-exhaustion.md`(STAR 기록), 요약은 `docs/troubleshooting.md` TS-025다. 이 폴더는 처음 `.local/blog-evidence/ts-025/`에 있던 자료를 학습 기록으로 옮긴 것이다.

| 파일 | 내용 | 글에서 쓰는 곳 |
|---|---|---|
| `connections-before-after.png` (원본 `.svg`) | 캐시 컨텍스트 상한 없음(빨강)과 있음(파랑)의 연결 수 변화. 같은 코드·같은 명령에서 차이는 `spring.test.context.cache.maxSize=8` 유무 하나 | 결과(Result)의 핵심 그림 |
| `connections-timeline.txt` | `script/test-db-connections.sh`가 출력한 막대그래프 원문(두 실행) | 코드 블록으로 인용, 그래프의 원자료 |
| `grafana-test-db-connections.png` (전체)
`grafana-test-db-connections-panel.png` (패널만) | **실제 Grafana 패널**로 본 테스트 중 연결 수. 빨강=캐시 상한 없음(최대 91, 실패), 파랑=상한 8(최대 80, 통과), 점선=일반 연결 한도 97 | **결과(Result)의 핵심 그림.** 직접 만든 비교 그래프와 같은 내용이다 |
| `grafana-overview-full.png` | Grafana `logistics-overview` 전체 | 모니터링 배경 설명용(보조) |
| `grafana-hikaricp-panel.png` | Grafana의 HikariCP 패널(`DB 커넥션 풀`) | 런타임 연결 모니터링 위치 설명용(보조) |

## 사용 시 주의
- **`grafana-overview-full.png`와 `grafana-hikaricp-panel.png`에는 테스트 중 연결이 나타나지 않는다(개발 스택의 서비스 런타임 패널).** 캡처 시점의 개발 스택은 유휴 상태라 HikariCP 패널이 전부 0이고 개요는 대부분 `No data`다. Grafana는 서비스 런타임용이고, 테스트 DB(Testcontainers가 잠깐 띄웠다 지움)는 수집하지 않는다. 글에서는 "서비스 런타임은 Grafana, 테스트 중에는 `pg_stat_activity` 샘플링"으로 구분해서 쓴다.
- Grafana 캡처는 실행 중인 창의 화면이 아니라 같은 대시보드를 헤드리스 브라우저로 새로 찍은 것이다(최근 30분, 2배 해상도).
- 그래프의 값은 측정 도구 출력 그대로이며 시간 축은 첫 샘플을 0초로 맞췄다. 값은 바뀐 시점부터 다음 변화까지 유지되는 계단으로 그렸다.
- 측정은 세 번 했다. 캐시 상한 없음: 최대 91, 96, 91(모두 BUILD FAILED). 캐시 상한 8: 최대 72, 80, 80(모두 BUILD SUCCESSFUL). `connections-before-after.png`는 두 번째 측정, `grafana-test-db-connections*.png`는 세 번째 측정이다. 실행마다 컨텍스트 생성 시점이 달라 최대치가 조금씩 다르다.
- 컨테이너의 일반 연결 한도는 97(`max_connections` 100 - 예약 3)이다. 같은 `postgres:16` 이미지를 기본 설정으로 띄워 조회한 값이다.
- `grafana-test-db-connections*.png`는 이 측정을 위해 임시로 띄운 격리된 Prometheus·Pushgateway·Grafana(포트 29090·29091·23000)의 화면이다. 개발 스택의 Grafana(3000)에는 테스트 DB가 수집되지 않기 때문이다. 측정 도구가 1초마다 `pg_stat_activity` 연결 수를 Pushgateway로 보내고 Prometheus가 1초 간격으로 수집했다. 그 임시 구성(설정 파일, 대시보드 JSON, 실행·정리 방법)은 [grafana-monitor/](grafana-monitor/README.md)에 있다.
- Grafana 개발 스택의 `DB 커넥션 풀 (HikariCP)` 패널은 `사용 중`·`대기`만 그린다. idle 연결과 풀 크기는 보이지 않아 이번 문제는 이 패널로 드러나지 않는다.
