-- 카테고리를 만들 때 대표 상태 행을 함께 만든다. 그 전에 만든 카테고리 중 행이 없는 것을 채운다
-- 새 코드로 앱을 다시 띄운 뒤 한 번 실행
START TRANSACTION;

INSERT INTO category_states (category_id, current_candidate_id, updated_at)
SELECT c.id, NULL, NOW()
FROM plan_categories c
WHERE NOT EXISTS (SELECT 1 FROM category_states cs WHERE cs.category_id = c.id)
ON DUPLICATE KEY UPDATE category_id = category_id;

COMMIT;

-- 확인 1: 상태 행이 없는 카테고리. 0이어야 한다
SELECT COUNT(*) AS categories_without_state
FROM plan_categories c
    LEFT JOIN category_states cs ON cs.category_id = c.id
WHERE cs.id IS NULL;

-- 확인 2: 살아 있는 후보가 있는데 대표가 없는 카테고리. 0이어야 한다
SELECT COUNT(*) AS candidates_without_representative
FROM category_states cs
WHERE cs.current_candidate_id IS NULL
  AND EXISTS (SELECT 1
              FROM place_candidates pc
              WHERE pc.category_id = cs.category_id
                AND pc.deleted_at IS NULL);
