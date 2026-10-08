#!/usr/bin/env bash
# PreToolUse(Bash) — 되돌릴 수 없는 명령과 "사용자 소유 영역"을 차단
# 차단당하면 우회하지 말고, 사용자에게 실행할 명령을 정리해 넘긴다.
#
# 결정 기준
#   deny : 되돌릴 수 없거나(이력·데이터 파괴) 팀/원격에 영향이 가는 것
#   ask  : 로컬 작업물·작업 트리를 바꾸지만 사용자가 판단하면 되는 것
#
# 이 훅이 못 잡는 것 (한계를 알고 쓴다)
# 스크립트 경유 실행:        ./some.sh (내부에서 무엇을 하든)
# 파일 안에 든 SQL:          mysql < x.sql
# 변수·서브셸로 조립한 명령:  c=push; git $c
# 최종 방어선: upstream push URL DISABLED(git remote -v 로 확인), GitHub 브랜치 보호.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
read_hook_input
require_jq

CMD="$(field '.tool_input.command')"
[ -z "$CMD" ] && exit 0
CMD_ALL="$(expand_wrappers "$CMD")"

GIT="${CMDPOS}git[[:space:]]+${GITOPT}"

# 1. git 기록·원격은 사용자 소유
match "${GIT}(commit|merge|rebase|cherry-pick|revert|am|pull)([[:space:]]|$)" &&
  deny "git 기록 변경(commit/merge/rebase/pull 등)은 사용자가 직접 한다. 변경 요약과 실행할 git 명령어만 정리해서 넘겨라(/git-handoff)."

match "${GIT}push([[:space:]]|$)" &&
  deny "푸시는 사용자가 직접 한다. 특히 upstream(BReady-Backend)에는 어떤 경우에도 닿으면 안 된다."

match "${GIT}tag[[:space:]]+([^-[:space:]]|-[^l-]|--(delete|annotate|sign|force|message|file))" &&
  deny "태그 생성·삭제는 사용자가 직접 한다."

match "${GIT}remote[[:space:]]+(add|set-url|remove|rm|rename|set-head|set-branches|prune)([[:space:]]|$)" &&
  deny "원격 설정 변경 금지. upstream push URL 이 DISABLED 인 것이 원본 레포 보호의 최종 방어선이다."

if match "${GIT}config([[:space:]]|$)" &&
   match "(remote\.|url\.|pushurl|pushdefault|--global|--system|core\.hookspath)" &&
   ! match "${GIT}config[^;|&]*(--get|--list|[[:space:]]-l([[:space:]]|$))"; then
  deny "git 원격·전역 설정 변경 금지(remote/url/pushurl/--global). 읽기는 git config --get 으로."
fi

match "${GIT}update-index([[:space:]]|$)" &&
  ask "인덱스 변경(git update-index)은 커밋에 들어갈 내용을 바꾼다. 사용자가 직접 실행하는 걸 권장한다. 그래도 실행할까?"

# 2. 이력·작업물 파괴 (복구 불가)
match "${GIT}clean([[:space:]]|$)" &&
  deny "금지: git clean. 추적되지 않는 파일(작성 중인 문서·코드)이 복구 불가능하게 사라진다."

match "${GIT}reset([[:space:]][^;|&]*)?[[:space:]]--(hard|merge|keep)([[:space:]]|$)" &&
  deny "금지: git reset --hard. 작업 중인 변경이 복구 불가능하게 사라진다."

match "${GIT}(checkout|restore)([[:space:]][^;|&]*)?[[:space:]](\.|:/|\*)([[:space:]]|$)" &&
  deny "금지: 작업 트리 전체 복원(git checkout . / git restore .). 되돌릴 파일을 하나씩 지정하고 사용자 확인을 받아라."

match "${GIT}(filter-branch|filter-repo|replace|update-ref)([[:space:]]|$)" &&
  deny "금지: git 히스토리·참조 재작성."

match "${GIT}branch([[:space:]][^;|&]*)?[[:space:]](-D|--delete[[:space:]]+--force|-f)([[:space:]]|$)" &&
  deny "금지: 강제 브랜치 삭제/덮어쓰기."

match "${GIT}stash[[:space:]]+(drop|clear)" &&
  deny "금지: git stash drop/clear. 보관된 작업이 사라진다."

# 작업 트리·인덱스를 바꾸는 git 조작 — 사용자 판단
match "${GIT}(switch|checkout|restore|stash|add|rm|mv)([[:space:]]|$)" &&
  ! match "${GIT}stash[[:space:]]+(list|show)" &&
  ask "작업 트리/인덱스를 바꾸는 git 명령이다. 이 레포에서 git 조작은 사용자 담당이다. 정말 여기서 실행할까?"

match "${GIT}branch[[:space:]]+[^-[:space:]]|${GIT}branch[[:space:]]+-(d|m|M|c|C|u)([[:space:]]|$)" &&
  ask "브랜치 생성·이름 변경·삭제는 사용자 담당이다. 정말 여기서 실행할까?"

# rm -r 로 레포 핵심 디렉터리 "전체"를 지우는 것 (build/ 같은 산출물, 개별 파일 삭제는 허용)
match "${CMDPOS}rm[[:space:]]+(-[a-zA-Z]*[rR][a-zA-Z]*[[:space:]]+)+(\./)?(/|~|\\\$HOME|\.git|\.claude|src|docs|gradle|db|deploy|nginx|monitoring|performance-test)/?([[:space:]]|$)" &&
  deny "금지: 레포 핵심 디렉터리 전체 삭제(rm -r). 지울 대상을 구체적으로 지정하고 사용자 확인을 받아라."

# 3. 원본 레포·팀 리소스 — 어떤 형태로도 닿지 않는다
match "${CMDPOS}gh([[:space:]]|$)" &&
  deny "gh CLI 는 사용하지 않는다(PR·이슈·릴리스·API 모두 원격 작업). 필요하면 사용자에게 실행할 명령을 넘겨라."

match "ghcr\.io|bready-team/bready-backend" &&
  deny "팀 컨테이너 레지스트리(ghcr.io/bready-team) 접근 금지. 이 레포는 로컬에서 직접 빌드한다."

match "bready\.site" &&
  deny "팀 운영 도메인(bready.site) 접근 금지. 운영 데이터에 영향이 갈 수 있다. 로컬(localhost)만 사용한다."

match "${CMDPOS}docker[[:space:]]+(push|login|logout)([[:space:]]|$)" &&
  deny "docker push/login 금지. 레지스트리에 올리거나 팀 자격증명을 쓰지 않는다."

match "${CMDPOS}(ssh|scp|rsync|sftp)([[:space:]]|$)" &&
  deny "원격 접속·파일 전송 금지(팀 EC2 등). 이 프로젝트는 로컬 전용이다."

match "${CMDPOS}aws([[:space:]]|$)" &&
  deny "AWS CLI 금지. 클라우드 리소스(팀 S3·EC2)는 이 프로젝트 범위 밖이다."

match "(deploy-blue-green|rollback-blue-green)\.sh|(^|[[:space:]/])deploy/[^[:space:]]+\.sh" &&
  deny "옛 블루그린 배포 스크립트는 실행하지 않는다."

match "docker-compose-(blue|green|prod)\.ya?ml" &&
  deny "옛 운영/블루그린 compose 는 실행하지 않는다. 로컬은 docker-compose-local.yml(이후 새 구성) 사용."

# 4. 로컬 컨테이너·볼륨 파괴
match "${CMDPOS}(docker[[:space:]]+compose|docker-compose)[^;|&]*[[:space:]]down[^;|&]*(-v([[:space:]]|$)|--volumes)" &&
  deny "금지: docker compose down -v. 로컬 DB 볼륨이 삭제된다. 필요하면 사용자가 직접 실행한다."

match "${CMDPOS}docker[[:space:]]+(volume[[:space:]]+(rm|prune)|system[[:space:]]+prune|container[[:space:]]+prune|image[[:space:]]+prune)" &&
  deny "금지: docker 볼륨·시스템 정리. 로컬 데이터가 사라질 수 있다."

# 5. DB — 조회·관찰은 Claude 담당, 파괴적 SQL·원격 접속은 차단
DBCLIENT='(^|[;&|([:space:]])(mysql|mysqlsh|mysqladmin|mysqldump|mariadb|redis-cli)([[:space:]]|$)'
SQLQ='["'"'"'][^"'"'"']*'

while IFS= read -r seg; do
  printf '%s' "$seg" | grep -Eiq -- "$DBCLIENT" || continue

  printf '%s' "$seg" | grep -Eiq -- "${SQLQ}(drop[[:space:]]+(table|database|schema|user)|truncate[[:space:]])" &&
    deny "금지: DROP/TRUNCATE. 스키마·데이터 파괴는 사용자가 검토 후 직접 실행한다."

  printf '%s' "$seg" | grep -Eiq -- "${SQLQ}(delete[[:space:]]+from|update[[:space:]]+[a-z_]+[[:space:]]+set)" &&
    ! printf '%s' "$seg" | grep -Eiq -- "where" &&
    deny "금지: WHERE 없는 DELETE/UPDATE."

  printf '%s' "$seg" | grep -Eiq -- "(flushall|flushdb)" &&
    deny "금지: Redis FLUSHALL/FLUSHDB."

  printf '%s' "$seg" | grep -Eiq -- "mysqladmin[^;|&]*(drop|shutdown)" &&
    deny "금지: mysqladmin drop/shutdown."

  DBHOST="$(printf '%s' "$seg" | grep -oEi -- '(-h|--host=?)[[:space:]]*[A-Za-z0-9._-]+' | head -1 | sed -E 's/^(-h|--host=?)[[:space:]]*//')"
  case "$DBHOST" in
    ""|localhost|127.*|0.0.0.0|mysql|redis|host.docker.internal|bready-mysql|bready-redis) : ;;
    *) deny "원격 DB(${DBHOST}) 접속 금지. 이 프로젝트의 DB는 로컬 컨테이너뿐이다." ;;
  esac
done < <(segments "$CMD_ALL")

# 6. 시크릿 노출 — 값이 대화·로그에 남는다
match "(cat|less|more|head|tail|bat|strings|xxd|od|nl|grep|rg|awk|sed|cp|base64)[[:space:]]([^;|&]*[[:space:]/])?(\.env(\.[a-z]+)?|[^[:space:]]*\.pem|[^[:space:]]*id_rsa[^[:space:]]*)([[:space:]]|$)" &&
  ! match "\.env\.example" &&
  deny "금지: 시크릿 파일(.env / *.pem / id_rsa) 내용 출력·복사. 값이 대화와 로그에 남는다. 필요한 키 이름만 사용자에게 물어라."

match "${CMDPOS}(printenv|env)[[:space:]]*($|[|;&>])|${CMDPOS}set[[:space:]]*($|[|;&>])" &&
  deny "금지: 환경변수 전체 출력. API 키 같은 시크릿이 대화에 남는다. 특정 변수의 존재만 확인해라: [ -n \"\$VAR\" ] && echo set"

exit 0