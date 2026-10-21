# feat/ci-docs-check-pipe

## 지금 하는 일
`script/docs-check.sh`가 변경 파일이 많은 PR에서 있는 문서를 누락으로 오판하는 문제를 고친다. 구현·검증 완료, PR 리뷰 대기.

## 진행 상태
- [x] 원인: `printf '%s\n' "$changed" | grep -Fxq "$doc"`에서 `grep -q`가 첫 일치에서 먼저 끝나 `printf`가 SIGPIPE를 받고, `set -o pipefail` 때문에 파이프가 실패로 판정돼 누락 처리된다. 변경 파일 1,057개인 `origin/main` 범위에서 실행마다 다른 문서가 누락으로 나왔다.
- [x] 수정: here-string(`grep -Fxq -- "$doc" <<< "$changed"`)으로 바꿔 파이프를 없앴다. 훅 입력 검사(22행)도 같은 형태라 함께 바꿨다.
- [x] 검증: 수정 전 6회 중 3회 가짜 누락, 수정 후 같은 범위 15회 모두 `OK`. 매핑된 코드만 바꾼 작업 트리에서는 `frontend/README.md` 누락을 그대로 잡고, `--hook` 모드도 exit 2로 알린다.

## 다음 단계
1. 병합 뒤 dev→main PR(#35)의 `docs-check`를 다시 실행한다.
