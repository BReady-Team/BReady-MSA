#!/usr/bin/env bash
# Stop — Java 를 고쳐 놓고 포맷·컴파일이 깨진 채로 끝내지 못하게 한다.
#
# - Java 변경이 없으면 아무것도 하지 않는다.
# - 직전에 통과한 상태와 같으면 건너뛴다(변경 파일 지문 캐시).
# - 훅 때문에 다시 도는 중(stop_hook_active)이면 막지 않는다. 무한 루프 방지.
# - gradle 자체가 못 도는 환경 문제면 막지 않고 알린다.
# - 끄고 싶으면 CLAUDE_SKIP_VERIFY_GATE=1
# 테스트 전체는 여기서 돌리지 않는다. 느리다. 조각의 검증 단계에서 .claude/scripts/verify.sh 로 돌린다.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
read_hook_input
require_jq

[ "${CLAUDE_SKIP_VERIFY_GATE:-0}" = "1" ] && exit 0
[ "$(field '.stop_hook_active')" = "true" ] && exit 0

cd "$PROJECT_DIR" 2>/dev/null || exit 0
[ -x ./gradlew ] || exit 0

CHANGED_JAVA="$(git status --porcelain --untracked-files=all 2>/dev/null | awk '{print $NF}' | grep -E '\.java$')"
[ -z "$CHANGED_JAVA" ] && exit 0

CACHE_DIR="$PROJECT_DIR/.claude/.cache"
mkdir -p "$CACHE_DIR"
FINGERPRINT="$(printf '%s\n' "$CHANGED_JAVA" | while IFS= read -r f; do
  [ -f "$f" ] && stat -f '%N %m %z' "$f" 2>/dev/null || stat -c '%n %Y %s' "$f" 2>/dev/null || echo "$f deleted"
done | shasum | cut -d' ' -f1)"
[ "$FINGERPRINT" = "$(cat "$CACHE_DIR/verify-gate-ok" 2>/dev/null)" ] && exit 0

block() {
  jq -n --arg r "$1" '{decision: "block", reason: $r}'
  exit 0
}

OUT="$(./gradlew spotlessCheck compileJava compileTestJava -q --console=plain 2>&1)"
STATUS=$?

if [ $STATUS -ne 0 ]; then
  if printf '%s' "$OUT" | grep -Eq 'Could not resolve|Could not download|Timeout waiting|JAVA_HOME|Unable to start the daemon'; then
    printf '{"systemMessage":"[알림] 포맷·컴파일 검사를 실행하지 못했다(환경 문제). 직접 ./gradlew spotlessCheck compileJava 로 확인하세요."}\n'
    exit 0
  fi

  if printf '%s' "$OUT" | grep -q 'spotlessJavaCheck'; then
    FILES="$(printf '%s' "$OUT" | grep -oE 'src/[A-Za-z0-9_/.]+\.java' | sort -u | head -10 | paste -sd ',' -)"
    block "포맷 위반이 있다: ${FILES}. ./gradlew spotlessApply 로 고친 뒤 다시 끝내라. (포맷은 도구가 정한다: build.gradle spotless)"
  fi

  ERRORS="$(printf '%s' "$OUT" | grep -E 'error:' | sed -E "s|^[[:space:]]*||; s|${PROJECT_DIR}/||" | awk '!seen[$0]++' | head -10)"
  block "컴파일이 실패한다. 고친 뒤 다시 끝내라.
${ERRORS}"
fi

printf '%s' "$FINGERPRINT" > "$CACHE_DIR/verify-gate-ok"
exit 0