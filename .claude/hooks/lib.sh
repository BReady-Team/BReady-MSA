#!/usr/bin/env bash
# 훅 공통 유틸. 각 훅이 `source` 해서 쓴다.
#
# 설계 원칙
# 1. fail-open 이되 조용하지 않게: jq 가 없거나 입력이 비면 통과시키되 경고를 띄운다.
#    조용히 통과하면 보호가 사라진 걸 아무도 모른다.
# 2. 훅은 1차 방어선이지 보증이 아니다. 최종 방어선은 upstream push URL DISABLED 와 GitHub 브랜치 보호다.
# 3. 오탐이 나면 훅을 우회하지 말고 이 파일(또는 해당 훅)의 패턴을 고치고 test-hooks.sh 에 케이스를 추가한다.

set -uo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
HOOK_INPUT=""

# stdin 의 hook JSON 을 읽어 둔다.
read_hook_input() {
  HOOK_INPUT="$(cat)"
}

# jq 없으면 경고 후 통과. (설치: brew install jq)
require_jq() {
  if ! command -v jq >/dev/null 2>&1; then
    printf '{"systemMessage":"[경고] .claude 훅 비활성: jq 가 없습니다. `brew install jq` 로 설치하세요."}\n'
    exit 0
  fi
}

# JSON 필드 추출. 없으면 빈 문자열.
field() {
  printf '%s' "$HOOK_INPUT" | jq -r "$1 // empty" 2>/dev/null
}

# PreToolUse 결정
deny() {
  jq -n --arg reason "$1" '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: $reason
    }
  }'
  exit 0
}

ask() {
  jq -n --arg reason "$1" '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "ask",
      permissionDecisionReason: $reason
    }
  }'
  exit 0
}

# 명령어 매칭 헬퍼
# 명령어 "위치"를 고정한다: 줄 시작 또는 셸 연산자 직후 + (선택) 래퍼/환경변수 접두.
# 이게 없으면 echo 'do not run git push' 같은 인용문 안의 단어를 실행으로 오인한다.
# 래퍼 접두를 허용하는 이유: `sudo git push`, `env X=1 git push`, `GIT_DIR=x git push` 도 실행이다.
CMDPOS='(^|[;&|(`]|&&|\|\||\$\()[[:space:]]*((sudo|command|exec|nohup|time|env)[[:space:]]+|[A-Za-z_][A-Za-z0-9_]*=[^[:space:]]*[[:space:]]+)*'
# git 서브커맨드 앞에 올 수 있는 전역 옵션들 (git -C dir push, git --no-pager log ...)
GITOPT='((-[cC][[:space:]]+[^[:space:]]+|--(git-dir|work-tree|exec-path|namespace)=[^[:space:]]+|--no-pager|--bare|-p)[[:space:]]+)*'

# 셸 래퍼 안의 명령을 꺼내 별도 줄로 붙인다.
#   bash -c "git push"  /  sh -lc 'git commit -m x'  /  eval "git push"
# grep 은 줄 단위로 동작하므로, 꺼낸 명령은 새 줄의 시작(^)에서 CMDPOS 에 걸린다.
expand_wrappers() {
  local cmd="$1" inner
  inner="$(printf '%s' "$cmd" \
    | grep -oE "((ba|z)?sh[[:space:]]+-[a-zA-Z]*c|eval)[[:space:]]+(\"[^\"]*\"|'[^']*')" \
    | sed -E "s/^((ba|z)?sh[[:space:]]+-[a-zA-Z]*c|eval)[[:space:]]+//; s/^[\"']//; s/[\"']$//")"
  printf '%s' "$cmd"
  [ -n "$inner" ] && printf '\n%s' "$inner"
}

# 대상 문자열(기본: $CMD_ALL)에서 확장 정규식 매칭 (대소문자 무시)
match() {
  printf '%s' "${2:-$CMD_ALL}" | grep -Eiq -- "$1"
}

# 명령을 셸 연산자 기준으로 세그먼트 분해해 표준출력으로 넘긴다.
# 세그먼트 단위로 봐야 `mysql --version; echo "DROP TABLE 금지"` 같은 오탐을 막는다.
segments() {
  printf '%s' "$1" | awk '{gsub(/\|\||&&|;|\||&/, "\n"); print}'
}