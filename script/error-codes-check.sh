#!/usr/bin/env bash
# 서비스 에러 코드가 모듈 사이에서 겹치지 않는지, docs/error-codes.md에 모두 적혀 있는지 확인한다.
# 사용: script/error-codes-check.sh   (빌드 없이 소스만 읽는다. 실패하면 종료 코드 1)
# macOS 기본 bash 3.2에서도 돌도록 연관 배열과 mapfile을 쓰지 않는다.
set -euo pipefail
cd "$(dirname "$0")/.."

DOC=docs/error-codes.md
fail=0

# 저장소 루트 모듈의 main 소스 중 ErrorCode를 구현한 enum만 대상이다. 결과: "코드 모듈" 한 줄씩
# 추적 중인 파일과 아직 add하지 않은 새 파일을 함께 본다. .gitignore된 경로와 .claude/worktrees 같은 다른 사본은 제외한다.
files=$(git ls-files --cached --others --exclude-standard -- '*ErrorCode.java' | grep -E '^[^/]+/src/main/java/' | sort -u || true)
pairs=$(for file in $files; do
  grep -qE 'enum [A-Za-z]+ implements ErrorCode' "$file" 2>/dev/null || continue
  module="${file%%/*}"
  # 상수는 들여쓰기(공백·탭 모두) 뒤 "NAME(" 로 시작하는 줄이다
  { grep -E '^[[:space:]]+[A-Z][A-Z0-9_]*[[:space:]]*\(' "$file" || true; } \
    | sed -E 's/^[[:space:]]+([A-Z][A-Z0-9_]*).*/\1/' | sed "s/\$/ $module/"
done)

for code in $(awk '{print $1}' <<<"$pairs" | sort | uniq -d); do
  printf '  FAIL  중복: %s (%s)\n' "$code" "$(awk -v c="$code" '$1 == c {print $2}' <<<"$pairs" | xargs)"
  fail=1
done

while read -r code module; do
  [[ -n "$code" ]] || continue
  if ! grep -q "\`$code\`" "$DOC"; then
    printf '  FAIL  문서 누락: %s (%s) → %s에 추가\n' "$code" "$module" "$DOC"
    fail=1
  fi
done <<<"$pairs"

if [[ $fail -eq 0 ]]; then
  printf 'PASS  에러 코드 %d개: 서비스 사이 중복과 문서 누락 없음\n' "$(grep -c . <<<"$pairs")"
fi
exit $fail
