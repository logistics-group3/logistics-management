#!/usr/bin/env bash
# 로컬 실행 진입점. .env 준비 → 이미지 빌드·기동 → 상태와 접속 주소 출력.
# 사용: script/run.sh                            전체 기동
#      script/run.sh hub-service order-service  지정한 서비스와 그 의존 서비스만 기동
#      script/run.sh --scale hub-service=2      인스턴스 수 지정 (docker compose에 그대로 전달)
#      script/run.sh --reset                    DB 볼륨까지 지우고 새로 시작 (DB 데이터 삭제)
#      script/run.sh --check                    기동 후 완료 기준 확인 (script/e2e-check.sh, 전체 기동 기준)
#      script/run.sh --no-build                 이미지를 다시 빌드하지 않고 기동
# 옵션은 함께 쓸 수 있다. 예: script/run.sh --reset --check
set -euo pipefail
cd "$(dirname "$0")/.."

reset=false
check=false
build=true
services=()
scale_args=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --reset) reset=true ;;
    --check) check=true ;;
    --no-build) build=false ;;
    --scale)
      [[ $# -ge 2 ]] || { echo "--scale 다음에 서비스=개수를 적는다. 예: --scale hub-service=2" >&2; exit 2; }
      scale_args+=("$1" "$2")
      shift ;;
    --scale=*) scale_args+=("$1") ;;
    -h|--help) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "알 수 없는 옵션: $1 (script/run.sh --help)" >&2; exit 2 ;;
    *) services+=("$1") ;;
  esac
  shift
done

if ! docker info >/dev/null 2>&1; then
  echo "Docker에 연결할 수 없다. Docker Desktop을 켠 뒤 다시 실행한다." >&2
  exit 1
fi

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo ".env가 없어 .env.example을 복사했다. 로컬 전용 값이다."
fi

if [[ "$reset" == true ]]; then
  echo "DB 볼륨까지 지우고 새로 시작한다 (docker compose down -v)."
  docker compose down -v --remove-orphans
fi

# Note: macOS 기본 bash 3.2는 set -u에서 빈 배열을 펼치면 오류를 낸다. ${arr[@]+...} 형태로 비어 있을 때를 건너뛴다.
# Note: `up --build 서비스`는 의존 서비스(config-server, eureka-server)까지 다시 빌드한다. 빌드할 때마다 이미지 ID가 바뀌어
# 그 컨테이너도 다시 만들어진다. 그래서 지정한 서비스만 먼저 빌드하고, up은 빌드 없이 실행한다(이미지가 없는 서비스는 up이 빌드한다).
if [[ "$build" == true ]]; then
  docker compose build ${services[@]+"${services[@]}"}
fi
docker compose up -d ${scale_args[@]+"${scale_args[@]}"} ${services[@]+"${services[@]}"}

echo
docker compose ps --format 'table {{.Service}}\t{{.State}}\t{{.Status}}'

# .env를 셸 코드로 실행하지 않고, 이 Compose 프로젝트의 실제 공개 포트를 조회한다.
host_url() {
  local binding
  binding=$(docker compose port "$1" "$2" 2>/dev/null | head -n 1) || return 1
  [[ -n "$binding" ]] || return 1
  printf 'http://localhost:%s' "${binding##*:}"
}

echo
if gateway=$(host_url gateway-service 8080); then
  echo "Swagger      $gateway/swagger-ui.html"
  echo "API(Gateway) $gateway"
fi
if eureka=$(host_url eureka-server 8761); then echo "Eureka       $eureka"; fi
if config=$(host_url config-server 8888); then echo "Config       $config/user-service/default"; fi
if zipkin=$(host_url zipkin 9411); then echo "Zipkin       $zipkin  (분산 추적)"; fi
if prometheus=$(host_url prometheus 9090); then echo "Prometheus   $prometheus/targets  (메트릭 수집 대상)"; fi
if grafana=$(host_url grafana 3000); then echo "Grafana      $grafana  (메트릭 대시보드, 보기는 로그인 없음)"; fi
echo "서비스가 Eureka에 등록될 때까지 수십 초 걸린다. 로그: docker compose logs -f {서비스}"

if [[ "$check" == true ]]; then
  echo
  script/e2e-check.sh
fi
