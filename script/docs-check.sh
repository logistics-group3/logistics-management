#!/usr/bin/env bash
# 코드를 바꿀 때 함께 고쳐야 할 문서를 고쳤는지 확인한다. 매핑: docs/doc-map.tsv
# 사용: script/docs-check.sh               작업 트리 변경(커밋 전 변경, 새 파일) 검사
#      script/docs-check.sh origin/dev     기준 브랜치와 갈라진 뒤 HEAD까지의 변경 검사 (PR, CI)
#      script/docs-check.sh --hook         에이전트 종료 훅용. 누락이 있으면 exit 2로 에이전트에게 알린다
# 매핑된 코드만 검사한다. 문서 영향이 없는 변경이면 PR에 skip-docs 라벨을 붙이고 이유를 쓴다.
set -uo pipefail

mode=worktree
base=""
case "${1:-}" in
  "") ;;
  --hook) mode=hook ;;
  -h|--help) sed -n '2,6p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  -*) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
  *) mode=range; base="$1" ;;
esac

if [[ "$mode" == hook ]]; then
  input="$(cat 2>/dev/null || true)"
  # 훅이 이미 에이전트를 한 번 이어서 실행시켰다면 다시 막지 않는다(무한 반복 방지)
  if grep -q '"stop_hook_active"[[:space:]]*:[[:space:]]*true' <<< "$input"; then
    exit 0
  fi
fi

root="$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "git 저장소가 아니다" >&2; exit 2; }
cd "$root"
map=docs/doc-map.tsv
[[ -f "$map" ]] || { echo "${map}이 없다" >&2; exit 2; }

if [[ "$mode" == range ]]; then
  git rev-parse --verify --quiet "$base" >/dev/null || { echo "기준 ref를 찾을 수 없다: $base" >&2; exit 2; }
  changed="$(git diff --name-only "$base"...HEAD)"
else
  changed="$(git diff --name-only HEAD; git ls-files --others --exclude-standard)"
fi
changed="$(printf '%s\n' "$changed" | sed '/^$/d' | sort -u)"
[[ -n "$changed" ]] || { echo "docs-check: 변경 없음"; exit 0; }

required=""
reasons=""
while IFS= read -r file; do
  case "$file" in */src/test/*) continue ;; esac
  while IFS=$'\t' read -r pattern docs hint; do
    case "$pattern" in ''|\#*) continue ;; esac
    # shellcheck disable=SC2053 # 패턴을 glob으로 맞춘다
    [[ "$file" == $pattern ]] || continue
    for doc in ${docs//,/ }; do
      required+="$doc"$'\n'
      reasons+="  - $file → $doc${hint:+ ($hint)}"$'\n'
    done
  done < "$map"
done <<< "$changed"

[[ -n "$required" ]] || { echo "docs-check: OK (매핑된 코드 변경 없음)"; exit 0; }

missing=""
while IFS= read -r doc; do
  [[ -n "$doc" ]] || continue
  # Note: 파이프로 넘기면 grep -q가 첫 일치에서 먼저 끝나 printf가 SIGPIPE를 받고, pipefail 때문에 변경 파일이 많을 때(수백 개 이상) 있는 문서를 누락으로 오판한다.
  grep -Fxq -- "$doc" <<< "$changed" || missing+="  - $doc"$'\n'
done <<< "$(printf '%s' "$required" | sort -u)"

if [[ -z "$missing" ]]; then
  echo "docs-check: OK"
  exit 0
fi

message="docs-check: 코드가 바뀌었지만 아래 문서가 바뀌지 않았다.
${missing}근거(docs/doc-map.tsv):
$(printf '%s' "$reasons" | sort -u)
실제 코드에 맞게 문서를 고친다. 문서 영향이 없으면 PR에 skip-docs 라벨을 붙이고 이유를 쓴다."

if [[ "$mode" == hook ]]; then
  echo "$message" >&2
  exit 2
fi
echo "$message"
exit 1
