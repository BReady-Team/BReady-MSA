#!/usr/bin/env bash
# 검증 한 번에 돌리기. 조각의 "검증" 단계와 커밋 전에 쓴다.
#
#   .claude/scripts/verify.sh          서비스마다 포맷 → 컴파일 → 전체 테스트
#   .claude/scripts/verify.sh --fast   서비스마다 포맷 → 컴파일 (테스트 생략)
#
# 단계마다 통과/실패를 표로 보여주고, 하나라도 실패하면 exit 1.
# 실패해도 다음 단계·다음 서비스를 계속 돌린다. 어디가 깨졌는지 한 번에 보기 위해서다.

set -uo pipefail
ROOT="$(git rev-parse --show-toplevel)" || exit 1
cd "$ROOT" || exit 1

FAST=0
[ "${1:-}" = "--fast" ] && FAST=1

# gradlew가 있는 최상위 디렉터리가 서비스다
SERVICES=()
MISSING=()
for dir in */; do
  dir="${dir%/}"
  if [ -x "$dir/gradlew" ]; then
    SERVICES+=("$dir")
  elif [ -f "$dir/build.gradle" ] || [ -d "$dir/src" ]; then
    MISSING+=("$dir")
  fi
done
# 빌드 파일·src는 있는데 gradlew가 없으면 조용히 빠지지 않게 실패
if [ ${#MISSING[@]} -gt 0 ]; then
  printf 'gradlew가 없어 검증할 수 없는 서비스: %s\n' "${MISSING[*]}"
  exit 1
fi
if [ ${#SERVICES[@]} -eq 0 ]; then
  printf '검증할 서비스가 없다(gradlew가 있는 디렉터리 0개). 레포 구조를 확인하라.\n'
  exit 1
fi

FAILED=0

run_step() {
  local service="$1" name="$2"; shift 2
  local log="$service/build/verify/${name}.log"
  local start end
  start=$(date +%s)
  if (cd "$service" && "$@") > "$log" 2>&1; then
    end=$(date +%s)
    printf '    통과  %s (%ss)\n' "$name" "$((end - start))"
  else
    end=$(date +%s)
    printf '    실패  %s (%ss) → %s\n' "$name" "$((end - start))" "$log"
    FAILED=1
  fi
}

print_test_summary() {
  local results="$1/build/test-results/test"
  ls "$results"/*.xml > /dev/null 2>&1 || return
  grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$results"/*.xml \
    | awk -F'"' '{t+=$2; s+=$4; f+=$6; e+=$8} END {printf "    테스트: 전체 %d · 실패 %d · 오류 %d · 건너뜀 %d\n", t, f, e, s}'
  local failed_tests
  failed_tests="$(grep -l '<failure\|<error' "$results"/*.xml 2>/dev/null | sed -E 's|.*/TEST-||; s|\.xml$||')"
  if [ -n "$failed_tests" ]; then
    printf '    실패한 테스트 클래스:\n'
    printf '%s\n' "$failed_tests" | sed 's/^/      - /'
  fi
}

printf '검증 결과 (서비스 %d개)\n' "${#SERVICES[@]}"
for service in "${SERVICES[@]}"; do
  mkdir -p "$service/build/verify"
  printf '  [%s]\n' "$service"
  run_step "$service" format  ./gradlew spotlessCheck --console=plain
  run_step "$service" compile ./gradlew compileJava compileTestJava --console=plain
  if [ "$FAST" -eq 0 ]; then
    run_step "$service" test ./gradlew test --console=plain
    print_test_summary "$service"
  fi
done

exit "$FAILED"