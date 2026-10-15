#!/usr/bin/env bash
# Stop — Java 를 고쳐 놓고 포맷·컴파일이 깨진 채로 끝내지 못하게 한다.
#
# - Java 변경이 없으면 아무것도 하지 않는다.
# - 고친 Java 가 속한 서비스(gradlew 가 있는 최상위 디렉터리)만 검사한다.
# - gradlew 가 없는 곳의 Java 는 검사하지 못한다는 사실을 알린다. 조용히 넘기지 않는다.
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

# git status 경로는 cwd와 상관없이 레포 루트 기준이다
CHANGED_JAVA="$(git status --porcelain --untracked-files=all 2>/dev/null | awk '{print $NF}' | grep -E '\.java$')"
[ -z "$CHANGED_JAVA" ] && exit 0

has_architecture_tests() {
  [ -n "$(find "$1/src/test/java" -type d -name architecture 2>/dev/null)" ]
}

SERVICES=()
UNCHECKED=()
NO_ARCHITECTURE=()
for dir in $(printf '%s\n' "$CHANGED_JAVA" | cut -d/ -f1 | sort -u); do
  if [ -x "$dir/gradlew" ]; then
    SERVICES+=("$dir")
    has_architecture_tests "$dir" || NO_ARCHITECTURE+=("$dir")
  else
    UNCHECKED+=("$dir")
  fi
done

# 건너뛴 검사를 말한다. 출력이 없으면 "통과"와 "검사 안 함"이 화면에서 같아진다
notify_skipped() {
  local messages=()
  [ ${#UNCHECKED[@]} -gt 0 ] && messages+=("gradlew 가 없어 포맷·컴파일을 검사하지 못한 Java 가 있다: ${UNCHECKED[*]}. 서비스라면 Gradle wrapper 를 추가하라.")
  [ ${#NO_ARCHITECTURE[@]} -gt 0 ] && messages+=("architecture 테스트가 없어 구조 규칙(ArchUnit)을 검사하지 않았다: ${NO_ARCHITECTURE[*]}. 도메인 코드가 있는 서비스라면 ArchUnit 을 추가하라.")
  [ ${#messages[@]} -eq 0 ] && return
  local text="[알림] ${messages[0]}"
  [ ${#messages[@]} -gt 1 ] && text+=" / ${messages[1]}"
  jq -n --arg m "$text" '{systemMessage: $m}'
}

block() {
  jq -n --arg r "$1" '{decision: "block", reason: $r}'
  exit 0
}

CACHE_DIR="$PROJECT_DIR/.claude/.cache"
mkdir -p "$CACHE_DIR"
FINGERPRINT="$(printf '%s\n' "$CHANGED_JAVA" | while IFS= read -r f; do
  [ -f "$f" ] && stat -f '%N %m %z' "$f" 2>/dev/null || stat -c '%n %Y %s' "$f" 2>/dev/null || echo "$f deleted"
done | shasum | cut -d' ' -f1)"
if [ "$FINGERPRINT" = "$(cat "$CACHE_DIR/verify-gate-ok" 2>/dev/null)" ]; then
  notify_skipped
  exit 0
fi

check_service() {
  local service="$1" out status
  local tasks=(spotlessCheck compileJava compileTestJava)
  # 구조 규칙(ArchUnit)은 도메인이 있는 서비스에만 있다. 없으면 notify_skipped 가 알린다
  if has_architecture_tests "$service"; then
    tasks+=(test --tests '*.architecture.*')
  fi

  out="$(cd "$service" && ./gradlew "${tasks[@]}" -q --console=plain 2>&1)"
  status=$?
  [ $status -eq 0 ] && return 0

  if printf '%s' "$out" | grep -Eq 'Could not resolve|Could not download|Timeout waiting|JAVA_HOME|Unable to start the daemon'; then
    printf '{"systemMessage":"[알림] 포맷·컴파일 검사를 실행하지 못했다(환경 문제). 직접 %s 에서 ./gradlew spotlessCheck compileJava 로 확인하세요."}\n' "$service"
    exit 0
  fi

  if printf '%s' "$out" | grep -q 'spotlessJavaCheck'; then
    local files
    files="$(printf '%s' "$out" | grep -oE 'src/[A-Za-z0-9_/.]+\.java' | sort -u | head -10 | paste -sd ',' -)"
    block "포맷 위반이 있다(${service}): ${files}. ${service} 에서 ./gradlew spotlessApply 로 고친 뒤 다시 끝내라. (포맷은 도구가 정한다: ${service}/build.gradle spotless)"
  fi

  local errors
  errors="$(printf '%s' "$out" | grep -E 'error:' | sed -E "s|^[[:space:]]*||; s|${PROJECT_DIR}/||" | awk '!seen[$0]++' | head -10)"
  if [ -n "$errors" ]; then
    block "컴파일이 실패한다(${service}). 고친 뒤 다시 끝내라.
${errors}"
  fi

  # 아키텍처 테스트 실패: 기준선에 없는 새 위반만 리포트에 나온다.
  local violations
  violations="$(grep -h -A20 '<failure' "$service"/build/test-results/test/TEST-*.architecture.*.xml 2>/dev/null \
    | sed -E "s/&lt;/</g; s/&gt;/>/g; s/&apos;/'/g; s/&quot;/\"/g" \
    | grep -E "^(Method|Field|Class|Constructor) <" \
    | sed -E 's/ in \([A-Za-z]+\.java:[0-9]+\)$//' \
    | awk '!seen[$0]++' | cut -c1-220 | head -12)"
  if [ -n "$violations" ]; then
    block "구조 규칙(ArchUnit)에 새 위반이 생겼다. 기준선(${service}/src/test/resources/archunit_store)을 고쳐 통과시키지 말고 코드를 고쳐라. 규칙 근거는 .claude/rules/.
${violations}"
  fi

  block "검사가 실패했다(${service}). 출력:
$(printf '%s' "$out" | tail -15)"
}

# macOS 기본 bash 3.2는 set -u에서 빈 배열을 펼치면 실패한다
for service in ${SERVICES[@]+"${SERVICES[@]}"}; do
  check_service "$service"
done

printf '%s' "$FINGERPRINT" > "$CACHE_DIR/verify-gate-ok"
notify_skipped
exit 0