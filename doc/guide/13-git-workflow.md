# 13. Git 작업 규칙 — 브랜치 · 커밋 · PR

> 최종 수정: 2026-10-12 · 대상: **전원**
> ⚠ 팀 합의 전 **제안**입니다. 수요일 리뷰에서 확정되면 이 줄을 지우고 CHANGELOG에 남깁니다.

## 1. 브랜치

```
main      제출본. 직접 push 금지
└─ dev    통합 브랜치. PR로만 머지 (docs-guard 체크 통과 필수)
   └─ {이름}/{작업}   개인 작업 브랜치 (예: haneol/base-setting, minsu/hub-crud)
```

- 작업 브랜치는 `dev`에서 따고 `dev`로 PR
- 브랜치 하나 = 기능 하나. 다른 서비스 모듈을 함께 고치면 PR 설명에 이유를 쓴다
- `common`, `config/application*.yml`, `build.gradle`, `docker-compose.yml`은 전 서비스 영향 → PR에 팀원 전체 리뷰 요청

## 2. 커밋 메시지

```
{type}({범위}): {요약}

- 상세 (선택)
```

| type | 언제 |
|---|---|
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변화 없는 구조 변경 |
| `docs` | 문서만 |
| `test` | 테스트만 |
| `chore` | 빌드·설정·의존성 |

범위는 모듈 이름: `feat(hub): 허브 재고 차감 내부 API (I-05)`, `chore(config): order Feign 타임아웃 조정`

## 3. PR

- 제목은 커밋 형식과 같게. 본문: 무엇을 / 왜 / 확인 방법(curl, 테스트) / 문서 갱신 위치
- 리뷰어 1명 이상 승인 후 머지 (공통 영역은 전체)
- `.github/workflows/docs-guard.yml`이 문서 누락을 검사한다. 문서 영향이 없으면 `skip-docs` 라벨
- 머지 방식: Squash merge (dev 이력을 기능 단위로)

## 4. 올리면 안 되는 것

`.env`, IDE 설정(`.idea/`), 빌드 결과(`build/`, `.gradle/`), `.DS_Store`, 로그 — `.gitignore`에 등록되어 있다.
실수로 비밀값을 커밋했다면 되돌리는 것으로 끝나지 않는다 → 값을 즉시 교체하고 인프라 담당에게 알린다.
