# 학습 노트

작업 중 나온 질문과 답을 날짜별로 남긴다. 블로그 초안과 복습 자료로 쓴다. 작성 방법은 `.agents/skills/learning-note/SKILL.md`, 운영 원칙은 `docs/agents/learning-guide.md`를 따른다.

성능·안정성 실험 후보와 진행 현황(이력서·포트폴리오용)은 [성능·안정성 실험 지도](performance-scenarios.md)에 모았다.

| 날짜 | 제목 | 난이도 | 태그 | 질문자 |
|---|---|---|---|---|
| 2026-10-19 | [배정에 잠금을 걸었더니 서비스가 멈췄다. 비동기로 바꾸면 같은 잠금인데 왜 괜찮나](qna/2026-10-19-sync-lock-vs-async-assignment.md) | 고급 | concurrency, advisory-lock, async, batch, message-queue | jihaneol |
| 2026-10-19 | [맥 한 대에서 부하 테스트를 할 때 무엇을 통제하고, 성능 개선을 어떤 숫자로 말해야 하나](qna/2026-10-19-local-load-test-methodology.md) | 중급 | load-test, jvm, jit, tps | jihaneol |
| 2026-10-16 | [회원가입 소속과 업체 관리자·배송담당자는 어떻게 정해지고, 배송 조회 권한은 어디서 판단하나](qna/2026-10-16-signup-affiliation-and-delivery-read-scope.md) | 중급 | auth, gateway, authorization, msa | jihaneol |
| 2026-10-16 | [k6는 무엇을 설치해야 하고, 부하 테스트 결과는 어떻게 읽나](qna/2026-10-16-k6-load-test-basics.md) | 기초 | k6, load-test, percentile | jihaneol |
| 2026-10-15 | [배송 서비스가 트래픽이 많을 때 먼저 막히는 곳은 어디인가](qna/2026-10-15-delivery-high-traffic-bottlenecks.md) | 고급 | performance, feign, cache, index, hikari | jihaneol |
| 2026-10-15 | [논리 삭제를 쓰는 애그리거트에서 orphanRemoval을 걸지 않는 이유](qna/2026-10-15-orphan-removal-and-soft-delete.md) | 중급 | jpa, cascade, orphan-removal, soft-delete | jihaneol |
| 2026-10-15 | [에이전트와 개발하면서 배우려면 일을 어떻게 나눠야 하나](qna/2026-10-15-learning-with-coding-agent.md) | 기초 | learning, ai-agent, workflow | jihaneol |
