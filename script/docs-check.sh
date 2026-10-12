#!/usr/bin/env bash
# 코드 변경에 맞춰 doc/ 문서가 함께 수정됐는지 점검합니다.
#   ./script/docs-check.sh              # 작업 트리(커밋 전 변경) 점검
#   ./script/docs-check.sh origin/dev   # base...HEAD 범위 점검 (CI)
#   ./script/docs-check.sh --hook       # Claude Code Stop 훅 모드
# 매핑 규칙: doc/etc/doc-map.tsv
set -uo pipefail

MODE="local"; BASE=""
case "${1:-}" in
  --hook) MODE="hook" ;;
  "") ;;
  *) MODE="ci"; BASE="$1" ;;
esac

if [ "$MODE" = "hook" ]; then
  INPUT="$(cat 2>/dev/null || true)"
  # 훅이 이미 한 번 Claude를 이어서 실행시켰다면 무한 루프 방지
  if printf '%s' "$INPUT" | grep -q '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
    exit 0
  fi
  cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
fi

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0
ROOT="$(git rev-parse --show-toplevel)"; cd "$ROOT" || exit 0
MAP="doc/etc/doc-map.tsv"
CHANGELOG="doc/etc/07-changelog.md"
[ -f "$MAP" ] || exit 0

if [ "$MODE" = "ci" ]; then
  CHANGED="$(git diff --name-only "$BASE"...HEAD)"
else
  if git rev-parse --verify HEAD >/dev/null 2>&1; then
    CHANGED="$(git diff --name-only HEAD; git ls-files --others --exclude-standard)"
  else
    CHANGED="$(git ls-files --cached --others --exclude-standard)"
  fi
fi
CHANGED="$(printf '%s\n' "$CHANGED" | sed '/^$/d' | sort -u)"
[ -z "$CHANGED" ] && exit 0

# 문서 갱신이 필요한 "코드" 경로
is_code() {
  case "$1" in
    doc/*|.github/*|.claude/*|script/*|CLAUDE.md|README.md) return 1 ;;
    */src/test/*) return 1 ;;
    backend/*|config/*) return 0 ;;
    docker-compose*|settings.gradle*|build.gradle*) return 0 ;;
  esac
  return 1
}

REQUIRED=""; REASONS=""; CODE_CHANGED=0
while IFS= read -r f; do
  is_code "$f" || continue
  CODE_CHANGED=1
  while IFS=$'\t' read -r pattern docs hint; do
    case "$pattern" in ''|\#*) continue ;; esac
    # shellcheck disable=SC2053
    if [[ "$f" == $pattern ]]; then
      for d in ${docs//,/ }; do
        REQUIRED="$REQUIRED"$'\n'"$d"
        REASONS="$REASONS"$'\n'"  - $f → $d${hint:+ ($hint)}"
      done
    fi
  done < "$MAP"
done <<< "$CHANGED"

[ "$CODE_CHANGED" -eq 0 ] && exit 0
REQUIRED="$(printf '%s\n' "$REQUIRED" "$CHANGELOG" | sed '/^$/d' | sort -u)"

MISSING=""
while IFS= read -r d; do
  printf '%s\n' "$CHANGED" | grep -Fxq "$d" || MISSING="$MISSING"$'\n'"  - $d"
done <<< "$REQUIRED"

[ -z "$MISSING" ] && { echo "docs-check: OK"; exit 0; }

# CHANGELOG에 '문서 영향 없음' 줄을 새로 추가했다면 통과
if [ "$MODE" = "ci" ]; then
  CL_DIFF="$(git diff "$BASE"...HEAD -- "$CHANGELOG")"
else
  CL_DIFF="$(git diff HEAD -- "$CHANGELOG" 2>/dev/null)"
fi
if printf '%s\n' "$CL_DIFF" | grep -q '^+.*문서 영향 없음'; then
  echo "docs-check: OK (CHANGELOG에 '문서 영향 없음' 기록됨)"; exit 0
fi
REASONS="$(printf '%s\n' "$REASONS" | sed '/^$/d' | sort -u)"
[ -n "$REASONS" ] && REASONS=$'\n'"$REASONS"

MSG="docs-check: 코드가 변경됐지만 아래 문서가 갱신되지 않았습니다.${MISSING}
매핑 근거:${REASONS:-
  - (매핑 없음, CHANGELOG만 필요)}
→ 실제 코드 내용에 맞게 문서를 수정하고 doc/etc/07-changelog.md 맨 위 날짜 섹션에 한 줄 추가하세요.
  문서 변경이 정말 불필요하다면 CHANGELOG에 '- [규칙] 문서 영향 없음: <이유>' 를 남기세요."

if [ "$MODE" = "hook" ]; then
  echo "$MSG" >&2; exit 2   # Claude에게 전달되어 이어서 문서를 갱신
fi
echo "$MSG"; exit 1
