# 스텝 26. 팀원 ADR 이관

- 날짜: 2026-10-14
- 근거: Notion "팀 합의 대조·추가 검토" 3장 설계 기준 문서 "ADR 문서 자체는 넣는데 각 서비스에 위치시키기", "docs·AI 지침 통합 방안" 6장 ADR 위치 "한 결정에 하나의 파일, 내용이 많아지면 디렉터리로 분리"
- 결과: 최승근 Notion ADR 15개 전부 이관(새 파일 12개, 기존 ADR 보완 3건). 결정 문서 상대 링크 깨짐 0건

## 바꾼 것
| 원문 | 위치 | SA v2.0과의 차이 |
|---|---|---|
| ADR-01 서비스 경계 | `docs/decisions/0011` | 재고를 hub 소유 기본 범위로 둔 부분 |
| ADR-02 기술 스택 | `0001` 끝에 보완 절 | 없음 |
| ADR-03 데이터 분리 | `docs/decisions/0012` | 없음 |
| ADR-04 서비스 간 통신 | `docs/decisions/0013` | 재고 API를 재시도 대상에 넣은 부분 |
| ADR-05 인증·인가 | `0005` 끝에 보완 절 | 없음 |
| ADR-06 재고 모델 | `docs/services/hub/decisions/0001` | 재고를 기본 범위로 둠(SA는 고도화) |
| ADR-07 입고 진입점 | `docs/services/company/decisions/0001` | 입고를 company P-06으로(SA는 hub H-06, 고도화) |
| ADR-08 재고 동시성 | `docs/services/hub/decisions/0002` | 조건부 UPDATE(SA 7.4는 여러 허브 FOR UPDATE, 고도화) |
| ADR-09 주문 생성 Saga | `docs/services/order/decisions/0001` | 재고 차감·복원 단계 추가 |
| ADR-10 주문 취소 | `docs/services/order/decisions/0002` | HUB_WAITING 기준(SA는 PICKUP_WAITING), 재고 복원 |
| ADR-11 배송 구간·상태 | `docs/services/delivery/decisions/0001` | 2구간(SA는 집하 포함 3구간) |
| ADR-12 배송담당자 | `docs/services/delivery/decisions/0002` | 집하 구간 규칙 없음 |
| ADR-13 삭제와 재고 | `docs/services/company/decisions/0002` | hub 호출·롤백(SA는 로컬 트랜잭션) |
| ADR-14 Slack 알림 | `docs/services/order/decisions/0003` | 없음 |
| ADR-15 공통 모듈 | `0009` 끝에 보완 절 | gateway가 common-core를 공유하는 점(ADR 0006) |

## 리뷰 포인트
1. **상태를 모두 "제안"으로 둔 이유**: 원문은 "확정"으로 표시돼 있지만 팀 승인 기록이 없고, 문서 스스로 "작업본"이라고 적었다. SA v2.0과 다른 결정은 서비스 담당자가 채택 여부를 정한다.
2. **원문 문장 유지**: 서비스 ADR과 보완 절은 원문 문장을 그대로 옮겼다(존댓말 포함). 작성자의 근거와 면접 답변을 바꾸지 않기 위해서다. 0011~0013은 저장소 문체(평서체)로 옮겼다.
3. **변환 방법**: 원문 절(배경, 선택지, 이유, 트레이드오프, 면접 답변)을 저장소 ADR 형식(맥락, 결정, 검토한 대안, 결과, 면접 답변)으로 기계적으로 옮기고, SA와의 차이만 직접 썼다. Notion 다이어그램은 "(원문 Notion의 다이어그램 참고)"로 표시했다.
4. **원문의 팀 확인 항목 4개**: hub 0002(조건부 UPDATE와 비관적 락), 0005 보완 절(user-service 장애 시 Gateway 동작), 0001 보완 절(Boot 3.5 지원 종료일), 0013(중첩 호출 타임아웃)에 남아 있다.
