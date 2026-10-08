#!/usr/bin/env bash
# 검증 한 번에 돌리기. 조각의 "검증" 단계와 커밋 전에 쓴다.
#
#   .claude/scripts/verify.sh          포맷 → 컴파일 → 전체 테스트
#   .claude/scripts/verify.sh --fast   포맷 → 컴파일 (테스트 생략)
#
# 단계마다 통과/실패를 표로 보여주고, 하나라도 실패하면 exit 1.
# 실패해도 다음 단계를 계속 돌린다. 어디가 깨졌는지 한 번에 보기 위해서다.

set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 1

FAST=0
[ "${1:-}" = "--fast" ] && FAST=1

LOG_DIR="build/verify"
mkdir -p "$LOG_DIR"
RESULTS=()
FAILED=0

run_step() {
  local name="$1"; shift
  local log="$LOG_DIR/${name}.log"
  local start end
  start=$(date +%s)
  if "$@" > "$log" 2>&1; then
    end=$(date +%s)
    RESULTS+=("통과  ${name} ($((end - start))s)")
  else
    end=$(date +%s)
    RESULTS+=("실패  ${name} ($((end - start))s) → ${log}")
    FAILED=1
  fi
}

run_step format  ./gradlew spotlessCheck --console=plain
run_step compile ./gradlew compileJava compileTestJava --console=plain

if [ "$FAST" -eq 0 ]; then
  run_step test ./gradlew test --console=plain
fi

printf '\n검증 결과\n'
for r in "${RESULTS[@]}"; do printf '  %s\n' "$r"; done

if [ "$FAST" -eq 0 ] && ls build/test-results/test/*.xml > /dev/null 2>&1; then
  grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' build/test-results/test/*.xml \
    | awk -F'"' '{t+=$2; s+=$4; f+=$6; e+=$8} END {printf "  테스트: 전체 %d · 실패 %d · 오류 %d · 건너뜀 %d\n", t, f, e, s}'
  FAILED_TESTS="$(grep -l '<failure\|<error' build/test-results/test/*.xml 2>/dev/null | sed -E 's|.*/TEST-||; s|\.xml$||')"
  if [ -n "$FAILED_TESTS" ]; then
    printf '  실패한 테스트 클래스:\n'
    printf '%s\n' "$FAILED_TESTS" | sed 's/^/    - /'
  fi
fi

exit "$FAILED"