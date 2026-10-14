#!/usr/bin/env bash
# 공용 스킬(.agents/skills)과 레이어 규칙(docs/agents/rules)을 Claude Code가 찾는 위치(.claude/skills, .claude/rules)에 연결한다.
# 사용: script/setup-agents.sh   (clone한 뒤 한 번, 스킬이나 규칙 파일이 추가·삭제되면 다시 실행)
# 정본은 .agents/skills와 docs/agents/rules다. .claude/skills, .claude/rules는 이 스크립트가 만드는 결과물이라 Git에 올리지 않는다.
# 심볼릭 링크를 만들 수 없는 환경(Windows 기본 설정)에서는 복사한다. 복사본은 원본이 바뀔 때마다 다시 실행해야 한다.
# Codex는 .agents/skills를 직접 읽으므로 이 스크립트가 필요 없다.
set -euo pipefail
cd "$(dirname "$0")/.."

linked=0
copied=0

# $1: 연결할 원본(저장소 루트 기준), $2: 만들 위치. 링크 대상은 만들 위치에서 본 상대 경로로 둔다.
place() {
  local src="$1" dst="$2" rel="../../$1"
  rm -rf "$dst"
  if ln -s "$rel" "$dst" 2>/dev/null && [[ -L "$dst" && -e "$dst" ]]; then
    linked=$((linked + 1))
  else
    rm -rf "$dst"
    cp -R "$src" "$dst"
    copied=$((copied + 1))
  fi
}

mkdir -p .claude/skills .claude/rules

# 원본이 사라진 링크와 이전 실행의 복사본을 정리한다
find .claude/skills .claude/rules -mindepth 1 -maxdepth 1 -exec rm -rf {} +

for dir in .agents/skills/*/; do
  name=$(basename "$dir")
  [[ -f "$dir/SKILL.md" ]] || continue
  place ".agents/skills/$name" ".claude/skills/$name"
done

# README.md는 목차라 규칙으로 불러오지 않는다
for file in docs/agents/rules/*.md; do
  name=$(basename "$file")
  [[ "$name" == "README.md" ]] && continue
  place "docs/agents/rules/$name" ".claude/rules/$name"
done

printf '연결 %d개, 복사 %d개 (.claude/skills, .claude/rules)\n' "$linked" "$copied"
if [[ $copied -gt 0 ]]; then
  echo "복사본은 원본이 바뀌면 다시 실행해야 한다."
fi
