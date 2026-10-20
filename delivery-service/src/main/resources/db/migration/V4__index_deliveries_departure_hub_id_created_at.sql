-- SA 4.6, 5.6 p_deliveries. 결정 로그 28. D-01 허브별 목록(departureHubId 조건 + createdAt 최신순 + 전체 건수)의 복합 부분 인덱스.
-- 과거 배송 30만 건(허브당 약 2.4만 건)에서 요청마다 count 18ms + 정렬 15ms를 DB에서 써, 생성+조회 초당 160건이면
-- postgres CPU 500%대로 서비스 전체가 무너졌다. 이 인덱스로 count는 Index Only Scan 4.3ms, 목록 10건은 정렬 없이 0.06ms다.
-- - deleted_at IS NULL: 조회는 논리 삭제를 빼고 보므로 삭제된 배송은 담지 않는다(BaseEntity).
-- - INCLUDE (delivery_id): count(delivery_id)가 테이블을 읽지 않고 인덱스만으로 끝나게 한다.
-- 기존 단일 인덱스 idx_p_deliveries_departure_hub_id는 다른 조건 조합(departureHubId만, 다른 정렬)이 쓰므로 남긴다.
CREATE INDEX idx_p_deliveries_departure_hub_id_created_at ON p_deliveries (departure_hub_id, created_at DESC)
    INCLUDE (delivery_id) WHERE deleted_at IS NULL;
