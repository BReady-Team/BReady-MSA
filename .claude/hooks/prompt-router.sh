#!/usr/bin/env bash
# UserPromptSubmit — 요청마다 지금 지켜야 할 것을 짧게 상기시킨다.
#
# 1. 이해 게이트가 OPEN 이면 매 요청에 리마인더를 넣는다.
#    "다음/시작/넘어가" 류 요청이면 더 강하게: 한 번은 붙잡고, 그래도 넘어가면 SKIPPED 로 기록.
# 2. 커밋·푸시 요청이면 git 은 사용자 몫이라는 것과 /git-handoff 를 상기시킨다.
# 3. 요청 키워드에 맞는 rules/ 파일 경로를 최대 3개 넣는다.
# 본문이 아니라 경로·한 줄 지시만 넣는다. 컨텍스트를 아끼기 위해서다.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
read_hook_input
require_jq

PROMPT="$(field '.prompt')"
[ -z "$PROMPT" ] && exit 0

NOTES=()

if [ "$(gate_status)" = "OPEN" ]; then
  if printf '%s' "$PROMPT" | grep -Eiq '다음|넘어가|시작|진행|고고|가자|next|go[[:space:]]*$|skip|스킵'; then
    NOTES+=("이해 게이트 OPEN 인데 다음으로 가자는 요청이다. 한 번은 붙잡아라: 미답 질문을 다시 보여주고 짧게라도 답을 받아라. 사용자가 그래도 넘어가겠다고 하면 docs/private/gates.md 에 SKIPPED 로 기록하고 roadmap 상태를 SKIPPED 로 바꾸는 편집을 제안한다(승인 필요).")
  else
    NOTES+=("이해 게이트 OPEN — 사용자의 답이면 /gate-review 로 교정·기록하고, 아니면 다음 조각을 시작하지 않는다.")
  fi
fi

if printf '%s' "$PROMPT" | grep -Eiq '커밋|commit|푸시|push|PR[[:space:]]|pull request'; then
  NOTES+=("git 은 사용자가 직접 한다. 실행하지 말고 /git-handoff 로 커밋 단위 명령어를 정리해라(docs/private/ 제외).")
fi

# 요청 키워드 → 읽을 규칙 파일 (최대 3개, 본문이 아니라 경로만)
# 순서가 우선순위다. 경계 규칙이 가장 앞이다.
RULES=()
add_rule() {
  local f="$1"
  [ "${#RULES[@]}" -ge 3 ] && return 0
  for r in ${RULES[@]+"${RULES[@]}"}; do [ "$r" = "$f" ] && return 0; done
  RULES+=("$f")
}
p() { printf '%s' "$PROMPT" | grep -Eiq -- "$1"; }

p '경계|boundary|모듈|분리|추출|msa|공개[[:space:]]*api' && add_rule msa-boundary.md
p '엔드포인트|endpoint|컨트롤러|controller|swagger|api[[:space:]]*(추가|만들|수정)' && add_rule controller-api.md
p '엔티티|entity|도메인|연관관계|enum|불변식' && add_rule entity.md
p '서비스|service|트랜잭션|transaction|락|lock|동시성|이벤트|event' && add_rule service-transaction.md
p 'dto|요청|응답|record' && add_rule dto.md
p '예외|exception|에러[[:space:]]*코드|errorcase' && add_rule exception.md
p '쿼리|query|레포지토리|리포지토리|repository|jpql|fetch|n\+1|페이징' && add_rule repository-query.md
p '테스트|test|fixture|안전망' && add_rule testing.md
p '로그|logging|log\.' && add_rule logging.md
p '리팩터|refactor|정리|클린|스타일' && add_rule java-style.md

if [ "${#RULES[@]}" -gt 0 ]; then
  PATHS=""
  for r in "${RULES[@]}"; do PATHS+=".claude/rules/$r "; done
  NOTES+=("코드 작업이면 먼저 읽을 규칙: ${PATHS}(전체 지도: .claude/rules/00-map.md)")
fi

[ "${#NOTES[@]}" -eq 0 ] && exit 0

TEXT="[하네스 리마인더]"
for n in "${NOTES[@]}"; do TEXT+=$'\n'"- $n"; done
emit_context "UserPromptSubmit" "$TEXT"