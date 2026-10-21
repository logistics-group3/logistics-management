# feat/docs-readme-required

## 지금 하는 일
제출용 README에 발제 필수 항목(목적, 팀원 역할분담, 서비스 구성·실행 방법, ERD, 기술 스택)을 채운다. 구현·검증 완료, PR 리뷰 대기.

## 진행 상태
- [x] 목적, 팀원과 역할, 기술 스택, 서비스 구성도(mermaid)와 모듈 표를 README 위쪽에 추가했다. 기존 "구성" 표는 이 자리로 옮기며 hub·company·order·delivery 설명을 채웠다.
- [x] ERD(mermaid)는 SA 4.1을 옮겼고 컬럼 상세·DBML은 SA 4장·부록 10.2로 연결했다.
- [x] 화면 3장은 `docs/reviews/ui-redesign/2026-10-20/`의 기존 캡처를 참조한다. 새로 찍은 화면은 테스트 데이터에 부하 시험 잔여분이 보여 쓰지 않았다.
- [x] "설계에서 신경 쓴 부분"은 ADR·학습 노트·감사 기록에서 뽑아 링크로 연결했다.
- [x] 검증: 두 mermaid 다이어그램을 브라우저에서 렌더링해 확인했고, 상대 경로 링크가 모두 존재하는지 확인했다.
- [x] 아키텍처 이미지(`docs/images/architecture.svg`)에 기술 스택 아이콘을 넣었다. 아이콘은 simple-icons 14.15.0(CC0)에서 받은 SVG 15개이며 `docs/images/icons/`에 두고 경로를 SVG에 직접 넣었다. GitHub README의 mermaid는 외부 아이콘을 지원하지 않아 이미지로 만들었다. Zipkin·OpenFeign·Caffeine은 simple-icons에 없어 글자 배지로 표기했다.
- [x] 공개 저장소 기준으로 '멘토·튜터' 표기를 '시나리오 테스트'로 바꿨다(README, 가이드 문서 `scenario-test.md`로 이름 변경, 운영 콘솔 문구). 과거 기록과 SA의 '멘토 피드백' 출처 표기는 그대로 둔다.
- [x] ERD를 컬럼까지 보이는 서비스별 SVG 4장(`docs/images/erd-*.svg`)으로 교체했다. 마이그레이션을 임시 PostgreSQL 16에 모두 적용해 16개 테이블의 컬럼·타입·PK·FK·UK를 뽑았다(전용 테스트 DB는 dev보다 오래된 스키마라 쓰지 않았다). 서비스 사이 논리 참조 표시는 SA 설계 기준으로 직접 적었다. mermaid 전체 관계도는 새 테이블(재고, 진행 기록, 복구, 종료 표시)을 포함하도록 갱신했다.
- [ ] 역할 표는 PR·커밋 이력 기준이다. hub·delivery 담당자는 서비스 README에서 아직 "미정"이라 팀 확인이 필요하다. 김진우는 사용자 지정("공통 인프라 설계 참여")을 따랐다.

## 다음 단계
1. 팀원이 자기 역할 문구를 확인한다.
2. 병합 뒤 제출용으로 dev→main PR을 올린다.
