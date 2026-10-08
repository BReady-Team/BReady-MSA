#!/usr/bin/env bash
# PostToolUse(Write|Edit|MultiEdit) — 방금 쓴 Java 코드의 규칙 위반을 바로 알려준다. 막지는 않는다.
#
# 파일 전체가 아니라 이번에 새로 쓴 내용만 본다. 옛 위반은 ArchUnit 기준선이 관리하고,
# 여기서 매번 다시 알리면 결국 아무도 읽지 않는다.
# ArchUnit 이 못 보는 소스 수준 규칙(import, Lombok, 테스트 스타일)이 주 대상이다.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
read_hook_input
require_jq

FILE="$(field '.tool_input.file_path')"
case "$FILE" in *.java) ;; *) exit 0 ;; esac

REL="${FILE#"$PROJECT_DIR"/}"
NEW="$(printf '%s' "$HOOK_INPUT" | jq -r '[.tool_input.content, .tool_input.new_string, (.tool_input.edits // [] | .[].new_string)] | map(select(. != null)) | join("\n")' 2>/dev/null)"
[ -z "$NEW" ] && exit 0

# 파일 전체 문맥이 필요한 판단(엔티티인가)은 디스크의 현재 파일로 한다.
FULL="$(cat "$FILE" 2>/dev/null)"
IS_TEST=0
case "$REL" in src/test/*) IS_TEST=1 ;; esac
IS_ENTITY=0
printf '%s' "$FULL" | grep -q '^@Entity' && IS_ENTITY=1

MODULE="$(printf '%s' "$REL" | sed -nE 's#^src/(main|test)/java/com/bready/server/([a-z0-9]+)/.*#\2#p')"

HITS=()
hit() { HITS+=("$1"); }
has() { printf '%s' "$NEW" | grep -Eq -- "$1"; }
show() { printf '%s' "$NEW" | grep -En -- "$1" | head -3 | sed -E 's/^([0-9]+):[[:space:]]*/  \1: /'; }

if has '^import [^s][^;]*\.\*;'; then
  hit "와일드카드 import (java-style.md 7번)"$'\n'"$(show '^import [^s][^;]*\.\*;')"
fi

if has 'new ApplicationException\('; then
  hit "new ApplicationException(...) 대신 ApplicationException.from(...) (exception.md 3번)"$'\n'"$(show 'new ApplicationException\(')"
fi

if has 'System\.(out|err)\.|\.printStackTrace\(\)'; then
  hit "표준 출력·printStackTrace 대신 로거 (logging.md 4번)"$'\n'"$(show 'System\.(out|err)\.|\.printStackTrace\(\)')"
fi

if has '@Autowired'; then
  hit "@Autowired 대신 생성자 주입(@RequiredArgsConstructor) (java-style.md 5번)"$'\n'"$(show '@Autowired')"
fi

if has 'log\.(trace|debug|info|warn|error)\([^;]*,[[:space:]]*(request|response|dto|[a-z]+Request|[a-z]+Response)\)'; then
  hit "DTO를 통째로 로그에 남기지 않는다. 필요한 ID만 (logging.md 2번)"$'\n'"$(show 'log\.(trace|debug|info|warn|error)\([^;]*,[[:space:]]*(request|response|dto|[a-z]+Request|[a-z]+Response)\)')"
fi

if [ "$IS_TEST" -eq 0 ] && [ -n "$MODULE" ]; then
  CROSS='^import com\.bready\.server\.([a-z0-9]+)\.(repository|domain)\.'
  OTHERS="$(printf '%s' "$NEW" | grep -Eo -- "$CROSS" | sed -E 's/^import com\.bready\.server\.([a-z0-9]+)\..*/\1/' | grep -vx "$MODULE" | sort -u | paste -sd ',' -)"
  if [ -n "$OTHERS" ]; then
    hit "다른 모듈(${OTHERS})의 repository·domain import. ID와 공개 API로 (msa-boundary.md 2번). ArchUnit 경계 규칙에서 빌드가 실패한다"
  fi

  if has 'LocalDate(Time)?\.now\(\)'; then
    hit "LocalDateTime.now() 대신 Clock 주입, 엔티티는 시각을 파라미터로 (service-transaction.md 5번)"$'\n'"$(show 'LocalDate(Time)?\.now\(\)')"
  fi
fi

if [ "$IS_ENTITY" -eq 1 ]; then
  has '@(Setter|Data)\b' && hit "엔티티에 @Setter/@Data 금지 (entity.md 3번)"
  has '@NoArgsConstructor[[:space:]]*$' && hit "엔티티는 @NoArgsConstructor(access = AccessLevel.PROTECTED) (entity.md 1번)"
  has '@(ToString|EqualsAndHashCode|Builder|AllArgsConstructor)\b' && hit "엔티티에 @ToString/@EqualsAndHashCode/@Builder/@AllArgsConstructor 금지 (entity.md 9번, java-style.md 5번)"
  has 'new Illegal(Argument|State)Exception\(' && hit "도메인 규칙 위반은 ErrorCase로 던진다 (entity.md 2번)"
  has 'EnumType\.ORDINAL' && hit "EnumType.ORDINAL 금지 (entity.md 5번)"
fi

if [ "$IS_TEST" -eq 1 ]; then
  has '@MockBean\b' && hit "@MockBean 대신 @MockitoBean (testing.md 6번)"
  has '(^|[^A-Za-z_.])when\(.*\)[[:space:]]*\.thenReturn' && hit "when().thenReturn() 대신 given().willReturn() (testing.md 3번)"
  has 'org\.junit\.jupiter\.api\.Assertions|AssertionsForClassTypes' && hit "JUnit 단언·AssertionsForClassTypes 대신 org.assertj.core.api.Assertions (testing.md 4번)"
  has 'Strictness\.LENIENT' && hit "LENIENT 대신 쓰지 않는 stub을 지운다 (testing.md 3번)"
  has 'standaloneSetup\(' && hit "standaloneSetup 대신 @WebMvcTest(controllers = …) (testing.md 6번)"

  ENTITIES="$(grep -rl '^@Entity' "$PROJECT_DIR/src/main/java" 2>/dev/null | xargs -n1 basename 2>/dev/null | sed 's/\.java$//' | paste -sd '|' -)"
  if [ -n "$ENTITIES" ] && has "mock\((${ENTITIES})\.class\)"; then
    hit "엔티티를 mock하지 않는다. fixture로 진짜 객체를 만든다 (testing.md 5번)"$'\n'"$(show "mock\((${ENTITIES})\.class\)")"
  fi
fi

[ "${#HITS[@]}" -eq 0 ] && exit 0

TEXT="[규칙 린트] ${REL} — 이번에 쓴 코드에서:"
for h in "${HITS[@]}"; do TEXT+=$'\n'"- $h"; done
TEXT+=$'\n'"의도한 예외가 아니라면 지금 고친다."
emit_context "PostToolUse" "$TEXT"