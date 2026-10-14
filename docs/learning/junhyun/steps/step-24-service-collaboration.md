# 스텝 24. 서비스 협업 창구

- 날짜: 2026-10-14
- 근거: Notion "docs·AI 지침 통합 방안" 6장 협업 창구 "서비스 간 규약을 공유할 창구는 있어야 한다. agent.md는 각 모듈 작업자를 위한 지침, 타 서비스에서 전달해 줄 창구는 별도로". 골격은 KMH `agent.md`(서비스 정보, 제공 API, 의존, 구현 현황, 커뮤니케이션 로그)를 따랐다.
- 결과: 서비스 7개(user, hub, company, order, delivery, gateway, common)의 `docs/services/{service}/README.md`

## 바꾼 것
| 파일 | 내용 |
|---|---|
| `docs/services/{user,gateway,common}/README.md` | 구현된 API·기능과 테스트 근거까지 채웠다 |
| `docs/services/{hub,company,order,delivery}/README.md` | SA 6.2·6.3 기준 API 목록과 호출 관계, 구현 현황은 모두 "예정". 담당자는 "미정" |
| `docs/services/README.md` | 협업 로그 ID 규칙(`{서비스}-{번호}`) |

## 리뷰 포인트
1. **SA를 복사하지 않는다**: 요청·응답 필드의 원본은 SA 6장이다. 협업 창구에는 API ID, 상태(구현·예정), SA와 다른 점만 적는다. 필드를 두 곳에 적으면 한쪽만 고쳐진다.
2. **재시도 열**: SA 3.5에 따라 GET과 orderId 기반 멱등 API만 재시도한다. 호출하는 쪽이 FeignClient에 `IdempotentRetryConfig`를 붙일지 이 열을 보고 정한다. I-11(배송 취소)은 재시도하지 않는다.
3. **KMH 원본과 다른 점**: 서비스 고유 규칙은 협업 창구가 아니라 모듈 `AGENTS.md`(스텝 27)에 둔다. 협업 창구는 다른 서비스 담당자가 읽는 문서다.

## 서비스 담당자가 할 일
- 담당자 칸을 채우고, 구현하면서 "예정"을 "구현"으로 바꾼다. SA와 다르게 구현한 점은 "SA와 다른 점" 열에 쓰고 서비스 결정(`decisions/`)으로 남긴다.
