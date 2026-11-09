#!/usr/bin/env bash
# 모놀리식 DB(bready.places)의 장소를 Catalog 전용 DB(catalog.places)로 옮긴다. 한 번만 실행
# id는 place_candidates.place_id가 가리키므로 그대로 보존
set -euo pipefail

SOURCE_CONTAINER="${SOURCE_CONTAINER:-bready-mysql}"
TARGET_CONTAINER="${TARGET_CONTAINER:-bready-catalog-mysql}"
TARGET_DATABASE="${TARGET_DATABASE:-catalog}"

# 비밀번호는 각 컨테이너의 환경 변수에서 읽는다
source_sql() {
    docker exec -i "$SOURCE_CONTAINER" sh -c \
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B --default-character-set=utf8mb4 "$MYSQL_DATABASE"'
}

target_sql() {
    docker exec -i "$TARGET_CONTAINER" sh -c \
        "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot -N -B --default-character-set=utf8mb4 $TARGET_DATABASE"
}

fail() {
    echo "중단: $1" >&2
    exit 1
}

# 행 내용 전체의 지문
FINGERPRINT_SQL="SET SESSION group_concat_max_len = 1048576;
SELECT CONCAT(COUNT(*), ' rows / md5 ', IFNULL(MD5(GROUP_CONCAT(
    CONCAT_WS('|', id, external_id, name, IFNULL(address, '-'), latitude, longitude,
              IFNULL(is_indoor + 0, '-'), IFNULL(created_at, '-'), IFNULL(updated_at, '-'))
    ORDER BY id SEPARATOR '\n')), '-'))
FROM places;"

echo "[1/5] 실행 전 조건 확인"
for container in "$SOURCE_CONTAINER" "$TARGET_CONTAINER"; do
    [ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null)" = "true" ] \
        || fail "$container 컨테이너가 켜져 있지 않다"
done
# 옮기는 도중 쓰기가 생기면 빠지는 행이나 id 충돌이 생긴다
for app in bready-monolith bready-place-catalog; do
    [ "$(docker inspect -f '{{.State.Running}}' "$app" 2>/dev/null)" = "true" ] \
        && fail "$app 컨테이너가 켜져 있다. 먼저 끈다"
done
for port in 8080 8081; do
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 \
        && fail "호스트 ${port} 포트에서 앱이 돌고 있다(bootRun). 먼저 끈다"
done
[ "$(echo "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1;" | target_sql)" = "1" ] \
    || fail "Catalog DB에 Flyway V1이 적용돼 있지 않다. Catalog를 새 DB로 한 번 기동한다"
[ "$(echo "SELECT COUNT(*) FROM places;" | target_sql)" = "0" ] \
    || fail "Catalog DB의 places가 비어 있지 않다. 이미 옮겼거나 Catalog가 새로 등록했다"
[ "$(echo "SELECT COUNT(*) FROM places WHERE deleted_at IS NOT NULL;" | source_sql)" = "0" ] \
    || fail "지워진(deleted_at) 장소가 있다. Catalog에는 지운 상태가 없으니 어떻게 할지 먼저 정한다"
echo "  통과"

echo "[2/5] 원본을 통째로 덤프해 Catalog DB의 임시 테이블 places_import로 적재"
docker exec "$SOURCE_CONTAINER" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --default-character-set=utf8mb4 --single-transaction --no-tablespaces --skip-add-locks --skip-triggers --set-gtid-purged=OFF "$MYSQL_DATABASE" places' \
    | sed 's/`places`/`places_import`/g' \
    | target_sql

echo "[3/5] 필요한 컬럼만 id 그대로 places에 넣고 임시 테이블 삭제"
target_sql <<'SQL'
START TRANSACTION;
INSERT INTO places (id, external_id, name, address, latitude, longitude, is_indoor, created_at, updated_at)
SELECT id, external_id, name, address, latitude, longitude, is_indoor, created_at, updated_at
FROM places_import;
COMMIT;
DROP TABLE places_import;
SQL

echo "[4/5] 원본과 결과 비교"
source_fingerprint="$(echo "$FINGERPRINT_SQL" | source_sql)"
target_fingerprint="$(echo "$FINGERPRINT_SQL" | target_sql)"
echo "  원본 bready.places : $source_fingerprint"
echo "  결과 catalog.places: $target_fingerprint"
[ "$source_fingerprint" = "$target_fingerprint" ] || fail "원본과 결과가 다르다"
echo "  id 목록: $(echo "SELECT GROUP_CONCAT(id ORDER BY id) FROM places;" | target_sql)"

echo "[5/5] 다음 등록 id 확인(가장 큰 id + 1 이어야 id가 겹치지 않는다)"
next_id="$(echo "SHOW CREATE TABLE places;" | target_sql | grep -o 'AUTO_INCREMENT=[0-9]*' || echo 'AUTO_INCREMENT=1')"
max_id="$(echo "SELECT IFNULL(MAX(id), 0) FROM places;" | target_sql)"
echo "  가장 큰 id: $max_id / $next_id"
[ "$next_id" = "AUTO_INCREMENT=$((max_id + 1))" ] || fail "다음 등록 id가 가장 큰 id + 1이 아니다"

echo "완료"
