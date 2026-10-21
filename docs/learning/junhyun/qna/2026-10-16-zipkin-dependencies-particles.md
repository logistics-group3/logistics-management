---
title: Zipkin Dependencies 화면에서 서비스 사이를 흐르는 점의 개수는 무엇을 뜻하나
date: 2026-10-16
updated: 2026-10-16
asker: Junhyun An
level: 중급
tags: [zipkin, distributed-tracing, dependencies, vizceral, observability]
---

# Zipkin Dependencies 화면에서 서비스 사이를 흐르는 점의 개수는 무엇을 뜻하나

## 질문
Zipkin의 Dependencies 탭에서 서비스 사이로 흘러가는 점의 개수는 무엇을 의미하나.

## 짧은 답
- 점은 **조회 기간 동안 두 서비스 사이에 오간 호출 수(`callCount`)를 상대적인 밀도로 보여 주는 애니메이션**이다. 점 하나가 요청 하나는 아니고, 지금 들어오는 실시간 요청도 아니다.
- 기본 색(옅은 파랑) 점은 전체 호출 수, 빨간 점은 에러 호출 수(`errorCount`)다.
- 밀도는 호출 수에 비례하지 않는다. 10건 이하는 5~10초에 한 개쯤으로 최소한만 보이고, 10건을 넘으면 가장 많은 간선 기준의 비율로 빠르게 늘어난다.
- 정확한 숫자는 노드를 클릭했을 때 나오는 옆 패널이나 `/api/v2/dependencies` 응답에서 본다.

## 자세히

### 1. 화면 뒤의 데이터: dependency link
Dependencies 탭은 저장된 span을 부모 서비스 → 자식 서비스 쌍으로 묶은 **dependency link** 목록을 그린다. link 하나는 다음 값을 가진다.

| 필드 | 뜻 |
|---|---|
| `parent`, `child` | 호출한 서비스, 호출받은 서비스 |
| `callCount` | 조회 기간 동안 parent가 child를 부른 횟수 |
| `errorCount` | 그중 `error` 태그가 붙은 호출 수. 0이면 응답에서 빠진다 |

화면 위쪽에서 고른 기간(`endTs`, `lookback`)으로 한 번 조회한 집계다. 그래서 트래픽이 끊긴 뒤에도 점은 계속 흐른다. 새 요청을 반영하려면 다시 조회해야 한다.

### 2. 점을 그리는 쪽: Vizceral
Zipkin의 웹 UI(Zipkin Lens)는 Netflix가 만든 트래픽 시각화 라이브러리 **Vizceral**로 이 그래프를 그린다. Lens는 link를 Vizceral 형식으로 바꿔 넘긴다(`DependenciesGraph.tsx`).

```ts
// 간선 하나
metrics: { normal: edge.callCount || 0, danger: edge.errorCount || 0 }
// 그래프 전체
maxVolume: maxVolume * 50   // maxVolume = 간선별 (normal + danger) 중 최댓값
```

Vizceral은 간선마다 `metrics`의 값(volume)을 "한 프레임(tick)에 몇 개를 내보낼지"로 바꾼다(`connectionView.js`의 `rateMap`).

| volume | 점 방출 빈도 |
|---|---|
| 0 | 없음 |
| 0보다 조금 큼 | 10초에 1개 |
| 1 | 7초에 1개 |
| 10 | 5초에 1개 |
| 100 | 프레임당 `100 × 19 / maxVolume`개 |
| `maxVolume` | 프레임당 19개(최대) |

사이 값은 직선으로 이어(선형 보간) 구한다. 정리하면 다음과 같다.
- **10건 아래는 "살아 있다"는 표시만 한다.** 1건이든 6건이든 5~7초에 한 개쯤이라 눈으로 구분하기 어렵다.
- **10건을 넘으면 `maxVolume` 기준 비율로 커진다.** Lens가 `maxVolume`을 실제 최댓값의 50배로 넘기므로 가장 바쁜 간선도 최대 밀도(프레임당 19개)의 일부만 쓴다.
- 비교 기준이 화면 안의 최댓값이라 **같은 호출 수라도 조회 기간이나 다른 간선의 트래픽이 바뀌면 점 밀도가 달라진다.** 서로 다른 화면의 밀도를 비교하지 않는다.

### 3. 빨간 점과 에러 판정
`danger` 값이 있으면 같은 규칙으로 빨간 점(`rgb(184, 36, 36)`)을 따로 내보낸다. 기본 점은 `callCount` 전체로 계산하므로 에러 호출은 기본 점과 빨간 점 양쪽에 반영된다.

에러로 세는 기준은 span의 `error` 태그다. HTTP 상태 코드만으로 정해지지 않는다(2026-10-16 이 프로젝트 Zipkin에서 확인).
- `404 PRODUCT_NOT_FOUND`로 끝난 요청의 span에는 `status=404`, `outcome=CLIENT_ERROR`만 있고 `error` 태그가 없었다. 4xx는 빨간 점이 되지 않는다.
- user-service가 Eureka에 아직 등록되지 않아 Gateway가 `503 "Unable to find instance for user-service"`를 낸 요청에는 `error` 태그가 붙었다. 그러나 user-service까지 간 span이 없어 gateway → user link에 들어가지 않았다. link는 부모와 자식 span이 모두 있어야 생기기 때문이다.

## 이 프로젝트에서는
2026-10-16에 로컬 Zipkin(http://localhost:9411)의 최근 24시간 link를 조회했다.

```bash
curl -s "http://localhost:9411/api/v2/dependencies?endTs=$(($(date +%s)*1000))&lookback=86400000"
```

위 규칙으로 계산한 점 밀도는 다음과 같다(`maxVolume` = 23 × 50 = 1150, 초당 개수는 Vizceral의 1초 = 60프레임 가정 기준).

| 간선 | callCount | 점 방출 |
|---|---|---|
| gateway → user | 23 | 초당 약 14개 |
| gateway → order, gateway → company | 6 | 약 5.7초에 1개 |
| gateway → delivery, gateway → hub | 5 | 약 5.9초에 1개 |
| order → company | 3 | 약 6.4초에 1개 |
| user → hub | 1 | 7초에 1개 |

호출 수는 23 대 6으로 4배 차이지만 점은 80배 넘게 차이 난다. gateway → user가 유독 많은 것은 Gateway가 모든 인증 요청마다 user-service에 사용자 권한을 다시 묻기 때문이다(SA 8.3). `errorCount`가 있는 link는 없어서 빨간 점은 보이지 않는다.

## 면접 포인트
- **Zipkin 의존성 그래프는 어떻게 만들어지나**: 저장된 span의 부모·자식 관계에서 서비스 쌍을 뽑아 호출 수와 에러 수를 집계한다. 메모리 저장소는 조회할 때 계산하고, Elasticsearch·Cassandra 같은 저장소는 별도 배치 작업(zipkin-dependencies)으로 미리 계산한다.
- **이 그래프로 무엇을 알 수 있고 무엇을 모르나**: 어떤 서비스가 어떤 서비스를 부르는지, 어디에 호출이 몰리는지, 어느 간선에서 에러가 나는지는 보인다. 지연 시간은 보이지 않으므로 느린 구간은 개별 trace에서 찾는다.

## 참고
- Zipkin Lens 소스: `zipkin-lens/src/components/DependenciesPage/DependenciesGraph.tsx`(openzipkin/zipkin master)
- Vizceral 소스: `src/base/connectionView.js`의 `setParticleLevels`, `src/base/trafficGraph.js`의 `maxVolume` 처리, `src/globalStyles.js`의 `colorTraffic`(Netflix/vizceral master)
- [Vizceral 그래프 데이터 형식](https://github.com/Netflix/Vizceral/wiki/How-to-Use#graph-data-format): `maxVolume`은 상대적 점 밀도를 계산하는 기준이다
- 관련 노트: [여러 서비스를 거치는 요청 흐름을 한 번에 보려면](2026-10-16-distributed-tracing-local.md)
