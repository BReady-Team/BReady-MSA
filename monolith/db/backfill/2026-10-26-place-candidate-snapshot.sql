-- ddl-auto가 추가한 스냅샷 컬럼에 ''·0으로 채워진 기존 후보를 places 값으로 채운다
-- 빈 행만 고르므로 다시 실행해도 새로 저장된 후보를 덮어쓰지 않는다
START TRANSACTION;

UPDATE place_candidates pc
    JOIN places p ON p.id = pc.place_id
SET pc.place_external_id = p.external_id,
    pc.place_name        = p.name,
    pc.place_address     = p.address,
    pc.place_latitude    = p.latitude,
    pc.place_longitude   = p.longitude,
    pc.place_is_indoor   = p.is_indoor
WHERE pc.place_external_id = '';

COMMIT;

-- 확인: 0이어야 한다
SELECT COUNT(*) AS unfilled
FROM place_candidates
WHERE place_external_id = '' OR place_name = '' OR (place_latitude = 0 AND place_longitude = 0);
