-- 장소를 Catalog DB로 옮긴 뒤, 모놀리식 DB에서 places를 떼어 낸다.
ALTER TABLE place_candidates DROP FOREIGN KEY FK1gt3ynbaiix1ct00baajqtnug;

-- 이관이 틀렸을 때 되돌릴 원본
RENAME TABLE places TO places_legacy;

-- 확인: places를 가리키는 FK 0, places 없음, places_legacy 있음
SELECT COUNT(*) AS fk_to_places
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND REFERENCED_TABLE_NAME IN ('places', 'places_legacy');

SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('places', 'places_legacy');
