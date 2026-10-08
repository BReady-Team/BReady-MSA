#!/usr/bin/env bash
# PreToolUse(Write|Edit|MultiEdit|NotebookEdit) — 보호 대상 파일과 시크릿이 담긴 쓰기를 막는다.
#
# deny : 시크릿 파일, 옛 팀 배포 자산, 시크릿처럼 보이는 값이 담긴 내용
# ask  : 빌드·하네스·인프라 설정, 이해 게이트 상태 → 영향이 커서 사용자가 보고 승인한다

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
read_hook_input
require_jq

FILE="$(field '.tool_input.file_path')"
[ -z "$FILE" ] && FILE="$(field '.tool_input.notebook_path')"
[ -z "$FILE" ] && exit 0

# 프로젝트 기준 상대경로로 정규화
REL="${FILE#"$PROJECT_DIR"/}"

# 1. 경로 기준 — deny 는 즉시, ask 는 내용 검사(deny 우선) 뒤로 미룬다
ASK_REASON=""
case "$REL" in
  # 시크릿 파일: 절대 쓰지 않는다 (.env.example 은 아래 내용 검사만)
  .env|.env.local|.env.dev|.env.prod|*/.env|*.pem|*.key|*.p12|*.jks|*id_rsa*)
    deny "시크릿 파일은 수정하지 않는다. 필요한 키 이름과 형식만 사용자에게 안내하고, 값은 사용자가 직접 넣는다." ;;

  # 옛 팀 배포 자산: 두고 쓰기 차단
  deploy/*|nginx/*|docker-compose-blue.yml|docker-compose-green.yml|docker-compose-prod.yml|.coderabbit.yaml|src/main/resources/application-cd.yml)
    deny "옛 팀 배포·운영 자산이다(두고 쓰기 차단). Strangler facade(3단계)에서 로컬 구성을 새로 만들 때 함께 정리한다." ;;

  # 이해 게이트 상태: 통과 판정은 사용자 승인
  docs/private/roadmap.md)
    ASK_REASON="로드맵/이해 게이트 상태 변경이다. 게이트를 PASSED 로 바꾸는 편집이라면, 정말 내 말로 설명할 수 있는지 확인하고 승인해라." ;;

  # 하네스 자체: 규칙·강제 장치를 바꾸는 변경은 사용자가 본다
  CLAUDE.md|.claude/settings.json|.claude/hooks/*)
    ASK_REASON="하네스(권한·훅) 변경이다(${REL}). 보호 장치를 약하게 만드는 변경이 아닌지 확인하고 승인해라. 수정 후 .claude/hooks/test-hooks.sh 를 돌린다." ;;

  # 구조 규칙 기준선: 손으로 고쳐 새 위반을 "인정"하지 않는다. 위반을 고치면 ArchUnit이 알아서 줄인다
  src/test/resources/archunit_store/*|src/test/resources/archunit.properties)
    ASK_REASON="ArchUnit 기준선 변경(${REL})이다. 새 위반을 기준선에 넣어 통과시키는 편집이면 승인하지 말고 코드를 고쳐라." ;;

  # 빌드·인프라·CI
  build.gradle|settings.gradle|gradle.properties|gradle/*|gradlew|gradlew.bat)
    ASK_REASON="빌드 설정 변경(${REL})이다. 의존성·플러그인 추가는 조각의 설계 단계에서 합의된 것만 넣는다." ;;
  .github/*|Dockerfile|docker-compose*.yml|monitoring/*|src/main/resources/application-ci.yml)
    ASK_REASON="인프라/CI 파일 변경(${REL})이다. 이번 조각에서 합의된 변경인지 확인하고 승인해라." ;;
esac

# 2. 내용 기준 — 시크릿처럼 보이는 값이 들어가는 쓰기
#    Write: content / Edit: new_string / MultiEdit: edits[].new_string
ask_if_needed() { [ -n "$ASK_REASON" ] && ask "$ASK_REASON"; exit 0; }

CONTENT="$(printf '%s' "$HOOK_INPUT" | jq -r '[.tool_input.content, .tool_input.new_string, (.tool_input.edits // [] | .[].new_string)] | map(select(. != null)) | join("\n")' 2>/dev/null)"
[ -z "$CONTENT" ] && ask_if_needed

SECRET_PATTERNS='AKIA[0-9A-Z]{16}|sk-(proj-)?[A-Za-z0-9_-]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{20,}|xox[abprs]-[A-Za-z0-9-]{10,}'
if printf '%s' "$CONTENT" | grep -Eq -- "$SECRET_PATTERNS"; then
  deny "실제 시크릿처럼 보이는 값(AWS 키·OpenAI 키·개인키·GitHub 토큰 등)이 포함돼 있다. 코드·설정에는 \${ENV_VAR:로컬기본값} 만 두고 실제 값은 .env 에만 둔다."
fi

# 설정 파일에 리터럴 비밀값 (${...} 참조가 아닌 8자 이상 값)
case "$REL" in
  *.yml|*.yaml|*.properties|.env.example)
    if printf '%s' "$CONTENT" | grep -Eiq -- '(^|[[:space:]])[A-Za-z0-9_.-]*(password|secret|api-key|apikey|access-key|secret-key|client-secret|token)[[:space:]]*[:=][[:space:]]*["'"'"']?[^$[:space:]"'"'"'{][^[:space:]]{7,}'; then
      deny "설정 파일에 비밀값이 리터럴로 들어 있다(${REL}). \${ENV_VAR:로컬전용-더미값} 형태로 바꾸고 실제 값은 .env 로 분리한다."
    fi ;;
esac

ask_if_needed
