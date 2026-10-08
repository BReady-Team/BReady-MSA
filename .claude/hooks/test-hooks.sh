#!/usr/bin/env bash
# 훅 회귀 테스트 — 훅을 고친 뒤 반드시 돌린다.
#   .claude/hooks/test-hooks.sh
#
# 표 기반: "기대결과|입력" 한 줄이 케이스 하나
#   기대결과 = deny | ask | allow
# 오탐(막으면 안 되는 것)도 반드시 케이스로 남긴다.

set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 1
HOOKS=".claude/hooks"

PASS=0
FAIL=0
FAILED=""

decision_of() {
  local out="$1" d
  d="$(printf '%s' "$out" | jq -r '.hookSpecificOutput.permissionDecision // empty' 2>/dev/null)"
  printf '%s' "${d:-allow}"
}

check() {
  local name="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
    FAILED="${FAILED}\n  ✗ [${name}] 기대=${expected} 실제=${actual}"
  fi
}

# guard-bash.sh
BASH_CASES=$(cat <<'EOF'
deny|git commit -m "wip"
deny|git push
deny|git push upstream develop
deny|git -C /Users/yooseungin/BReady-MSA push origin develop
deny|cd src && git push
deny|sudo git push
deny|GIT_SSH_COMMAND=x git push
deny|bash -c "git push origin develop"
deny|sh -lc 'git commit -m x'
deny|eval "git push"
deny|git pull origin develop
deny|git merge feature/x
deny|git rebase develop
deny|git tag v1.0
deny|git tag -d v1.0
deny|git remote set-url --push upstream https://github.com/BReady-Team/BReady-Backend.git
deny|git remote add team https://github.com/BReady-Team/BReady-Backend.git
deny|git remote remove upstream
deny|git config remote.upstream.pushurl x
deny|git config --global user.name x
deny|git clean -fd
deny|git reset --hard HEAD~1
deny|git checkout .
deny|git checkout -- .
deny|git restore .
deny|git branch -D feature/x
deny|git stash drop
deny|git filter-branch --tree-filter x
deny|rm -rf src
deny|rm -rf .git
deny|rm -r docs/
deny|gh pr create --fill
deny|gh repo view BReady-Team/BReady-Backend
deny|docker pull ghcr.io/bready-team/bready-backend:latest
deny|docker push bready:latest
deny|docker login ghcr.io
deny|curl https://bready.site/api/v1/plans
deny|ssh ubuntu@1.2.3.4
deny|scp app.jar ec2:/home
deny|aws s3 ls
deny|./deploy/deploy-blue-green.sh
deny|docker compose -f docker-compose-prod.yml up -d
deny|docker compose -f docker-compose-local.yml down -v
deny|docker volume rm breadymsa_mysql-data
deny|docker system prune -af
deny|docker exec bready-mysql mysql -uroot -e "DROP DATABASE bready"
deny|mysql -h 127.0.0.1 -uroot -e "TRUNCATE plans"
deny|mysql -uroot -e "DELETE FROM plans"
deny|mysql -h bready-prod.xxxx.rds.amazonaws.com -uroot
deny|docker exec bready-redis redis-cli FLUSHALL
deny|cat .env
deny|head -5 ./.env
deny|grep OPENAI .env
deny|cat ~/.ssh/id_rsa
deny|printenv
deny|env | grep KEY
ask|git add docs/architecture/context-map.md
ask|git checkout -b feature/harness
ask|git switch develop
ask|git restore src/main/java/A.java
ask|git stash
ask|git branch feature/x
ask|git update-index --chmod=+x gradlew
allow|git status
allow|git diff --stat
allow|git log --oneline -10
allow|git show HEAD:README.md
allow|git branch
allow|git branch -a
allow|git remote -v
allow|git fetch upstream
allow|git tag
allow|git tag -l
allow|git stash list
allow|git config --get remote.upstream.pushurl
allow|echo "git push 는 사용자가 한다"
allow|grep -rn "git push" .claude/rules
allow|./gradlew test
allow|./gradlew compileJava --console=plain
allow|rm -rf build
allow|rm src/main/java/com/bready/server/place/service/PlaceService.java
allow|docker compose -f docker-compose-local.yml ps
allow|docker compose -f docker-compose-local.yml down
allow|docker compose logs --tail=50 app
allow|docker exec bready-mysql mysql -uroot -e "SELECT count(*) FROM plans"
allow|mysql -h 127.0.0.1 -uroot -e "UPDATE plans SET title='x' WHERE id=1"
allow|mysql --version; echo "DROP TABLE 금지"
allow|curl -s http://localhost:8080/actuator/health
allow|cat .env.example
allow|env SPRING_PROFILES_ACTIVE=test ./gradlew test
EOF
)

while IFS='|' read -r expected cmd; do
  [ -z "$expected" ] && continue
  out="$(jq -n --arg c "$cmd" '{tool_input:{command:$c}}' | "$HOOKS/guard-bash.sh")"
  check "bash: $cmd" "$expected" "$(decision_of "$out")"
done <<< "$BASH_CASES"

# guard-write.sh   형식: 기대결과|파일경로(레포 기준)|내용
WRITE_CASES=$(cat <<'EOF'
deny|.env|OPENAI_API_KEY=x
deny|src/main/resources/keys/server.pem|x
deny|deploy/deploy-blue-green.sh|echo
deny|nginx/bready.conf|server {}
deny|docker-compose-prod.yml|services: {}
deny|.coderabbit.yaml|language: ko
deny|src/main/resources/application-cd.yml|x: y
deny|src/main/java/com/bready/server/global/config/s3/S3Config.java|String key = "AKIAABCDEFGHIJKLMNOP";
deny|src/main/resources/application-local.yml|spring.ai.openai.api-key: sk-proj-abcdefghijklmnopqrstuvwxyz123456
deny|src/main/resources/application-local.yml|  password: mySuperSecret123
deny|docker-compose-local.yml|      MYSQL_ROOT_PASSWORD: realpassword123
deny|.env.example|JWT_SECRET=thisIsARealLookingSecretValue
ask|build.gradle|testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
ask|.claude/settings.json|{}
ask|.claude/hooks/guard-bash.sh|# x
ask|docs/private/roadmap.md|직전 게이트: PASSED
ask|CLAUDE.md|# CLAUDE.md
allow|docs/private/gates.md|## H2
allow|docs/LEARNING_JOURNEY.md|## 2026-10-09
ask|docker-compose-local.yml|services: {}
ask|.github/workflows/ci.yml|name: ci
allow|src/main/java/com/bready/server/plan/service/PlanService.java|public class PlanService {}
allow|docs/architecture/context-map.md|# 경계 지도
allow|src/main/resources/application-local.yml|  password: ${DB_PASSWORD:local}
allow|src/test/resources/application-test.yml|  secret: ${JWT_SECRET:test-jwt-secret-minimum-32-bytes-required}
allow|.env.example|OPENAI_API_KEY=
allow|src/test/resources/application-test.yml|    password:
EOF
)

while IFS='|' read -r expected path content; do
  [ -z "$expected" ] && continue
  out="$(jq -n --arg f "$PWD/$path" --arg c "$content" '{tool_input:{file_path:$f, content:$c}}' | "$HOOKS/guard-write.sh")"
  check "write: $path :: $content" "$expected" "$(decision_of "$out")"
done <<< "$WRITE_CASES"

# Edit(new_string) / MultiEdit(edits[]) 경로도 내용 검사를 타는지
out="$(jq -n --arg f "$PWD/src/main/java/A.java" '{tool_input:{file_path:$f, old_string:"a", new_string:"String k = \"AKIAABCDEFGHIJKLMNOP\";"}}' | "$HOOKS/guard-write.sh")"
check "edit: new_string 시크릿" deny "$(decision_of "$out")"
out="$(jq -n --arg f "$PWD/src/main/java/A.java" '{tool_input:{file_path:$f, edits:[{old_string:"a", new_string:"ok"},{old_string:"b", new_string:"-----BEGIN RSA PRIVATE KEY-----"}]}}' | "$HOOKS/guard-write.sh")"
check "multiedit: edits[] 시크릿" deny "$(decision_of "$out")"

# session-context.sh / prompt-router.sh
# 실제 roadmap 을 건드리지 않도록 임시 프로젝트 디렉터리에 상태 블록만 만들어 검증한다.
FAKE="$(mktemp -d)"
trap 'rm -rf "$FAKE"' EXIT
mkdir -p "$FAKE/docs/private"
write_state() {
  printf '<!-- STATE:BEGIN -->\n현재 조각: 테스트\n단계: 7/8\n직전 게이트: %s\n<!-- STATE:END -->\n' "$1" > "$FAKE/docs/private/roadmap.md"
}
context_of() {
  printf '%s' "$1" | jq -r '.hookSpecificOutput.additionalContext // empty' 2>/dev/null
}
contains() {
  local name="$1" haystack="$2" needle="$3" expected="$4" actual=no
  printf '%s' "$haystack" | grep -q -- "$needle" && actual=yes
  check "$name" "$expected" "$actual"
}

out="$(echo '{}' | "$HOOKS/session-context.sh")"
contains "session: 로드맵 상태 주입" "$(context_of "$out")" "로드맵 상태" yes
contains "session: 실제 레포는 upstream 경고 없음" "$(context_of "$out")" "upstream push URL" no
contains "session: 실제 레포는 private ignore 경고 없음" "$(context_of "$out")" "gitignore 되어 있지 않다" no

write_state OPEN
out="$(echo '{"prompt":"다음 가자"}' | CLAUDE_PROJECT_DIR="$FAKE" "$HOOKS/prompt-router.sh")"
contains "router: OPEN + 다음 → 붙잡기" "$(context_of "$out")" "한 번은 붙잡아라" yes
out="$(echo '{"prompt":"트리거는 계획과 같이 바뀌니까 같은 서비스에 둬야 해"}' | CLAUDE_PROJECT_DIR="$FAKE" "$HOOKS/prompt-router.sh")"
contains "router: OPEN + 답변 → gate-review" "$(context_of "$out")" "/gate-review" yes

write_state PASSED
out="$(echo '{"prompt":"다음 가자"}' | CLAUDE_PROJECT_DIR="$FAKE" "$HOOKS/prompt-router.sh")"
check "router: PASSED + 다음 → 출력 없음" "" "$out"
out="$(echo '{"prompt":"커밋 명령어 정리해줘"}' | CLAUDE_PROJECT_DIR="$FAKE" "$HOOKS/prompt-router.sh")"
contains "router: 커밋 요청 → git-handoff" "$(context_of "$out")" "/git-handoff" yes

out="$(echo '{"prompt":"플랜 복제 API 추가해줘"}' | CLAUDE_PROJECT_DIR="$FAKE" "$HOOKS/prompt-router.sh")"
contains "router: API 추가 → controller-api" "$(context_of "$out")" "controller-api.md" yes
out="$(echo '{"prompt":"trigger 모듈 경계 넘는 리포지토리 정리"}' | CLAUDE_PROJECT_DIR="$FAKE" "$HOOKS/prompt-router.sh")"
contains "router: 경계 → msa-boundary 우선" "$(context_of "$out")" "rules/msa-boundary.md .claude/rules/" yes
out="$(echo '{"prompt":"엔티티 서비스 dto 예외 쿼리 테스트 전부"}' | CLAUDE_PROJECT_DIR="$FAKE" "$HOOKS/prompt-router.sh")"
check "router: 규칙 경로 최대 3개" 3 "$(context_of "$out" | grep -o '\.claude/rules/[a-z-]*\.md' | grep -vc 00-map)"
out="$(echo '{"prompt":"고마워"}' | CLAUDE_PROJECT_DIR="$FAKE" "$HOOKS/prompt-router.sh")"
check "router: 무관한 요청 → 출력 없음" "" "$out"

write_state SKIPPED
out="$(echo '{}' | CLAUDE_PROJECT_DIR="$FAKE" "$HOOKS/session-context.sh")"
contains "session: SKIPPED 는 OPEN 경고 없음" "$(context_of "$out")" "이해 게이트 OPEN" no
write_state OPEN
out="$(echo '{}' | CLAUDE_PROJECT_DIR="$FAKE" "$HOOKS/session-context.sh")"
contains "session: OPEN 경고" "$(context_of "$out")" "이해 게이트 OPEN" yes

# stop-verify-gate.sh — gradle 을 돌리지 않는 경로만 (gradle 경로는 느려서 수동 시연)
out="$(echo '{"stop_hook_active":true}' | "$HOOKS/stop-verify-gate.sh")"
check "stop: 훅 재실행 중이면 통과" "" "$out"
out="$(echo '{}' | CLAUDE_SKIP_VERIFY_GATE=1 "$HOOKS/stop-verify-gate.sh")"
check "stop: 끄기 변수면 통과" "" "$out"
out="$(echo '{}' | CLAUDE_PROJECT_DIR="$FAKE" "$HOOKS/stop-verify-gate.sh")"
check "stop: gradlew 없는 곳이면 통과" "" "$out"

printf '\n훅 회귀 테스트: 통과 %d / 실패 %d\n' "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
  printf "%b\n" "$FAILED"
  exit 1
fi