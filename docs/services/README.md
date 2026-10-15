# 서비스 협업 창구

서비스 담당자끼리 API 계약을 알리고 요청을 주고받는 곳이다. 서비스마다 `{service}/README.md`가 있다.

- **여기에 쓰는 것**: 제공하는 API의 계약(요청·응답 필드, 에러 코드), 호출하는 다른 서비스 API, 구현 현황, 다른 서비스와의 요청·질문 기록
- **여기에 쓰지 않는 것**: 그 서비스 코드를 고칠 때 지킬 규칙은 모듈 폴더의 `AGENTS.md`에 쓴다. 설계 원본은 SA다.
- 구현하며 남기는 설명은 `{service}/notes.md`, 서비스 결정은 `{service}/decisions/`에 둔다.

## 서비스 간 계약 요약

| 제공자 | 제공하는 것 | 의존하는 쪽·전제 |
|---|---|---|
| Config Server | 서비스 설정(`config-repo/`) | 클라이언트는 config import 실패 시 기동 실패 |
| [gateway](gateway/README.md) | JWT·사용자 재확인, `X-User-*` 헤더 정리·주입, 내부 API 외부 차단 | MVC 서비스는 외부에 직접 노출하지 않는다 |
| [user](user/README.md) | 가입·로그인·내부 사용자 조회(I-01, I-14) | Gateway가 실제 role·탈퇴 상태를 재확인한다 |
| [common](common/README.md) | 응답·오류 계약, Audit, 현재 사용자·역할·소유자 검사, Feign 공통 처리, 페이지·정렬 규칙 | 업무 서비스는 API별 비즈니스 인가와 허용 정렬 필드를 정한다 |
| [hub](hub/README.md) | 허브·허브 간 경로 조회(I-02, I-03) | company, user, delivery가 호출한다 |
| [company](company/README.md) | 업체·상품 조회(I-07, I-08) | order가 호출한다 |
| [order](order/README.md) | 주문 생성 Saga 오케스트레이션 | company, hub, delivery를 호출한다 |
| [delivery](delivery/README.md) | 배송 생성·취소·삭제(I-09~I-13) | order가 호출한다 |
| PostgreSQL 초기화 | 스키마·전용 계정 | 빈 볼륨에서 한 번, 테이블 변경은 Flyway |

## 협업 로그 규칙

다른 서비스에 요청하거나 질문할 때는 **요청받는 서비스의** `README.md` 협업 로그 맨 아래에 남긴다. ID는 `{서비스}-{번호}`다(예: `HUB-001`, `COMMON-001`).

| 상태 | 뜻 |
|---|---|
| `OPEN` | 요청·질문을 남겼고 답을 기다린다 |
| `DECIDED` | 받는 서비스 담당자가 어떻게 할지 정했다. 결정한 사람과 근거를 적는다 |
| `DONE` | 구현하고 검증까지 끝났다. 관련 커밋이나 PR을 적는다 |

- 작성자가 상태만 바꾼 기록은 합의로 보지 않는다. `DECIDED`에는 받는 쪽 담당자의 결정이 있어야 한다.
- 계약(요청·응답 필드)이 바뀌면 같은 PR에서 `README.md`의 제공 API 표도 고친다.

```markdown
### HUB-001 I-03 응답에 소요 시간 추가 요청
- 상태: OPEN
- 작성: delivery / 이름 / 2026-10-15
- 관련: I-03, delivery 구간 생성
- 내용: 구간 예상 도착 시간을 계산하려면 `durationMinutes`가 필요하다.
- 결정:
```
