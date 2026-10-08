#!/usr/bin/env bash
# SessionStart — 새 세션이 "지금 어디까지 왔는지"를 알고 시작하게 한다.
#
# 주입하는 것
# - 브랜치와 미커밋 변경 수
# - 로드맵 상태 블록(현재 조각 · 단계 · 게이트) — docs/private/roadmap.md 가 단일 진실
# - 최근 학습일지 항목, 미해결 게이트 수
# 경고하는 것 (원본 레포 보호와 공개 레포 보호가 깨진 상태)
# - upstream push URL 이 DISABLED 가 아님
# - docs/private/ 가 gitignore 되지 않음
# - JDK 메이저 버전이 21 이 아님
# 1초 안에 끝나야 한다. 느린 명령(gradle, docker)은 넣지 않는다.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
read_hook_input
require_jq

cd "$PROJECT_DIR" 2>/dev/null || exit 0

LINES=()
WARN=()

BRANCH="$(git branch --show-current 2>/dev/null)"
DIRTY="$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
LINES+=("브랜치: ${BRANCH:-?} · 미커밋 변경: ${DIRTY}개")

STATE="$(roadmap_state)"
if [ -n "$STATE" ]; then
  LINES+=("로드맵 상태 (docs/private/roadmap.md):")
  while IFS= read -r l; do [ -n "$l" ] && LINES+=("  · $l"); done <<< "$STATE"
else
  WARN+=("docs/private/roadmap.md 상태 블록이 없다. 현재 위치를 사용자에게 확인해라.")
fi

if [ "$(gate_status)" = "OPEN" ]; then
  WARN+=("이해 게이트 OPEN — 사용자의 답을 받아 교정(/gate-review)하기 전에는 다음 조각을 시작하지 않는다.")
fi

if [ -f docs/LEARNING_JOURNEY.md ]; then
  LAST="$(grep -E '^## ' docs/LEARNING_JOURNEY.md | tail -1 | sed 's/^## //')"
  [ -n "$LAST" ] && LINES+=("최근 학습일지: ${LAST}")
fi

if [ -f docs/private/gates.md ]; then
  OPEN_GATES="$(grep -cE '^## .*(미해결|OPEN)' docs/private/gates.md)"
  [ "$OPEN_GATES" -gt 0 ] && LINES+=("미해결 게이트: ${OPEN_GATES}건 (docs/private/gates.md) — 해당 단계 시작 시 다시 묻는다")
fi

PUSH_URL="$(git remote get-url --push upstream 2>/dev/null)"
if [ -n "$PUSH_URL" ] && [ "$PUSH_URL" != "DISABLED" ]; then
  WARN+=("upstream push URL 이 DISABLED 가 아니다(${PUSH_URL}). 원본 레포 보호의 최종 방어선이 꺼져 있다. 사용자에게 즉시 알려라: git remote set-url --push upstream DISABLED")
fi

if ! git check-ignore -q docs/private/roadmap.md 2>/dev/null; then
  WARN+=("docs/private/ 가 gitignore 되어 있지 않다. 개인 문서가 공개 레포에 올라갈 수 있다. 사용자에게 알려라.")
fi

JAVA_MAJOR="$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')"
if [ -n "$JAVA_MAJOR" ] && [ "$JAVA_MAJOR" != "21" ]; then
  WARN+=("로컬 JDK 가 ${JAVA_MAJOR} 이다. 이 프로젝트는 Java 21 툴체인을 쓴다.")
fi

TEXT="[BReady-MSA 세션 컨텍스트]"
for l in "${LINES[@]}"; do
  case "$l" in
    "  · "*) TEXT+=$'\n'"$l" ;;
    *) TEXT+=$'\n'"- $l" ;;
  esac
done
if [ "${#WARN[@]}" -gt 0 ]; then
  TEXT+=$'\n'"[경고]"
  for w in "${WARN[@]}"; do TEXT+=$'\n'"- $w"; done
fi
TEXT+=$'\n'"규칙: CLAUDE.md · 조각 절차: /piece-start → 구현 → /piece-close → /gate-review"

emit_context "SessionStart" "$TEXT"