#!/usr/bin/env bash
# 4-3에서 백업으로 남긴 모놀리식 DB의 places_legacy를 지운다. (한 번만 실행)
set -euo pipefail

MONOLITH_CONTAINER="${MONOLITH_CONTAINER:-bready-mysql}"
CATALOG_CONTAINER="${CATALOG_CONTAINER:-bready-catalog-mysql}"
CATALOG_DATABASE="${CATALOG_DATABASE:-catalog}"

# 비밀번호는 각 컨테이너의 환경 변수에서 읽는다
monolith_sql() {
    docker exec -i "$MONOLITH_CONTAINER" sh -c \
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B --default-character-set=utf8mb4 "$MYSQL_DATABASE"'
}

catalog_sql() {
    docker exec -i "$CATALOG_CONTAINER" sh -c \
        "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot -N -B --default-character-set=utf8mb4 $CATALOG_DATABASE"
}

fail() {
    echo "중단: $1" >&2
    exit 1
}

# 4-3 이관 스크립트와 같은 컬럼·같은 형식의 지문
fingerprint_sql() {
    local table="$1" condition="$2"
    cat <<SQL
SET SESSION group_concat_max_len = 1048576;
SELECT CONCAT(COUNT(*), ' rows / md5 ', IFNULL(MD5(GROUP_CONCAT(
    CONCAT_WS('|', id, external_id, name, IFNULL(address, '-'), latitude, longitude,
              IFNULL(is_indoor + 0, '-'), IFNULL(created_at, '-'), IFNULL(updated_at, '-'))
    ORDER BY id SEPARATOR '\n')), '-'))
FROM $table
WHERE $condition;
SQL
}

table_exists() {
    echo "SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '$1';" | monolith_sql
}

echo "[1/4] 실행 전 조건 확인"
for container in "$MONOLITH_CONTAINER" "$CATALOG_CONTAINER"; do
    [ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null)" = "true" ] \
        || fail "$container 컨테이너가 켜져 있지 않다"
done
if [ "$(table_exists places_legacy)" = "0" ]; then
    echo "  places_legacy가 없다. 이미 지웠으므로 할 일이 없다"
    exit 0
fi
[ "$(table_exists places)" = "0" ] \
    || fail "모놀리식 DB에 places가 있다. 누가 다시 만들었는지 먼저 확인한다"
echo "  통과"

echo "[2/4] places_legacy와 Catalog DB의 같은 id 행 비교"
legacy_ids="$(echo "SELECT IFNULL(GROUP_CONCAT(id ORDER BY id), '') FROM places_legacy;" | monolith_sql)"
[ -n "$legacy_ids" ] || fail "places_legacy가 비어 있다. 4-3 이관 기록과 다르니 먼저 확인한다"
legacy_fingerprint="$(fingerprint_sql places_legacy "TRUE" | monolith_sql)"
catalog_fingerprint="$(fingerprint_sql places "id IN ($legacy_ids)" | catalog_sql)"
echo "  id 목록           : $legacy_ids"
echo "  bready.places_legacy: $legacy_fingerprint"
echo "  catalog.places      : $catalog_fingerprint"
[ "$legacy_fingerprint" = "$catalog_fingerprint" ] \
    || fail "백업과 Catalog의 내용이 다르다. 어느 쪽이 맞는지 정하기 전에는 지우지 않는다"

echo "[3/4] places_legacy 삭제(되돌릴 수 없다)"
echo "DROP TABLE places_legacy;" | monolith_sql

echo "[4/4] 확인"
[ "$(table_exists places_legacy)" = "0" ] || fail "places_legacy가 아직 있다"
echo "  places_legacy 없음, 후보 $(echo "SELECT COUNT(*) FROM place_candidates;" | monolith_sql)행 그대로"

echo "완료"
