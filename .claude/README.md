# .claude — 하네스 설명서

이 디렉터리는 Claude Code가 이 레포에서 **어떻게 일하는지**를 정한다. 사람이 읽는 문서다.

이 레포는 팀 모놀리식 BReady를 혼자 Strangler Fig 방식으로 MSA로 바꾸는 학습 프로젝트다. 하네스가 지키려는 것은 세 가지다.

1. **원본 레포와 팀에 영향이 가지 않는다.** upstream, 팀 레지스트리·서버·도메인, 시크릿.
2. **코드 품질이 하나의 기준으로 모인다.** 옛 코드의 혼재가 새 코드로 복제되지 않는다.
3. **경계는 뒤로 가지 않는다.** 옛 위반은 기록하고, 새 위반은 빌드가 막는다.

## 구조

```
읽는 비용 낮음 ───────────────────────────────────────────► 높음
CLAUDE.md  →  rules/*.md (작업별)  →  skills/* (절차)  →  실제 코드
   ▲                ▲
 항상 로드      훅이 요청 키워드에 맞는 경로를 알려준다
```

| 위치 | 무엇 | 언제 쓰이나 |
|---|---|---|
| `../CLAUDE.md` | 정체성 · 역할 · 절대 규칙 · 조각 진행 방식 · 컨벤션 요약 | 매 세션 자동 |
| `rules/` | 규범: 무엇이 옳은가 (유일한 원본) | 작업에 맞는 것만 (`rules/00-map.md`) |
| `skills/` | 절차: 어떤 순서로 하는가 | `/이름`으로 호출, 또는 작업이 설명과 맞을 때 |
| `agents/` | 별도 컨텍스트의 감사자 | 경계 점검, 리뷰 |
| `hooks/` | 강제: 결정론적 차단·점검 | 이벤트마다 자동 |
| `scripts/` | 실행 도구 | 사람과 Claude 둘 다 |
| `settings.json` | 권한(allow/ask/deny) + 훅 등록 | 커밋 대상 |
| `settings.local.json` | 개인 허용 목록 | gitignore |

**규칙은 한 곳에만 쓴다.** 스킬·훅·에이전트는 규칙을 복사하지 않고 `rules/파일.md`의 항목 번호를 가리킨다.

## 강제 수준

규칙마다 어디서 막을지를 정했다. 아래로 갈수록 강하다.

| 수준 | 장치 | 담당하는 규칙 |
|---|---|---|
| 문서 | `rules/`, `skills/` | 왜 지금, 설계 선택지, 트랜잭션 경계 판단처럼 판단이 필요한 것 |
| 알림 | `post-edit-lint.sh`, `prompt-router.sh` | 방금 쓴 코드의 위반 힌트, 읽을 규칙, 이해 게이트 리마인더 |
| 리뷰 | `agents/` | 책임 위치, 중복, 테스트가 증명하는 것, 기계가 못 보는 경계 위반 |
| 끝내기 차단 | `stop-verify-gate.sh` | Java를 고쳤으면 포맷·컴파일·아키텍처 테스트 통과 |
| 빌드 실패 | Spotless, ArchUnit | 포맷, 레이어 방향, 엔티티 규약, 모듈 경계(새 위반만) |
| 실행 차단 | `guard-bash.sh`, `guard-write.sh`, 권한 deny | git 기록·원격, 팀 리소스, 시크릿, 이력·데이터 파괴 |

## 훅

| 이벤트 | 스크립트 | 동작 |
|---|---|---|
| SessionStart | `session-context.sh` | 브랜치·미커밋 수, 로드맵 상태(현재 조각·게이트), 최근 학습일지, 미해결 게이트 주입. upstream push URL·`docs/private` ignore·JDK 이상 경고 |
| UserPromptSubmit | `prompt-router.sh` | 게이트가 열려 있으면 리마인더, 커밋 요청이면 `/git-handoff` 안내, 키워드별 규칙 파일 경로(최대 3개) |
| PreToolUse(Bash) | `guard-bash.sh` | git commit/push/pull/merge·원격 설정·이력 파괴 deny, gh·ghcr·운영 도메인·ssh·aws·옛 배포 자산 deny, 볼륨 삭제·파괴적 SQL deny, 시크릿 출력 deny. 작업 트리를 바꾸는 git 조작은 ask |
| PreToolUse(Write\|Edit) | `guard-write.sh` | 시크릿 파일·옛 팀 배포 자산 deny, 시크릿처럼 보이는 값 deny. CLAUDE.md·하네스·빌드·인프라·게이트 상태·ArchUnit 기준선은 ask |
| PostToolUse(Write\|Edit) | `post-edit-lint.sh` | 이번에 새로 쓴 Java 코드만 검사해 위반을 알림 (막지 않음) |
| Stop | `stop-verify-gate.sh` | Java 변경이 있으면 Spotless·컴파일·아키텍처 테스트. 실패면 끝내지 못함. 같은 상태는 캐시로 건너뜀 |

### 훅을 만들며 지킨 것
- **fail-open이되 조용하지 않게.** jq가 없거나 gradle이 환경 문제로 못 돌면 통과시키되 알린다. 훅이 고장 나 모든 작업을 막으면 결국 훅을 끄게 된다.
- **명령 위치를 고정한다.** `echo "git push"`나 `grep "git push"`를 실행으로 오인하지 않는다. 대신 `sudo git push`, `bash -c "git push"`, `GIT_DIR=x git push`는 잡는다.
- **새로 쓴 것만 알린다.** 린트는 이번 편집의 내용만 본다. 옛 위반을 매번 다시 알리면 아무도 읽지 않는다.
- **macOS 기본 bash 3.2에서 돈다.** `set -u`에서 빈 배열을 `"${arr[@]}"`로 펼치면 죽는다 → `${arr[@]+"${arr[@]}"}` 또는 길이 확인 후 펼친다.
- **한계를 안다.** 스크립트 경유 실행(`./x.sh` 안의 git push), 변수로 조립한 명령, 파일 안의 SQL은 못 잡는다. 최종 방어선은 `upstream`의 push URL `DISABLED`와 GitHub 브랜치 보호다.

## 스킬

| 스킬 | 언제 |
|---|---|
| `/piece-start` | 조각 시작: 왜 지금 → 설계 선택지 → 흐름 미리보기 → 결정 대기 |
| `/piece-close` | 조각 마무리: 검증 증거 → 요청 순서대로 워크스루 → 이해 게이트 질문 |
| `/gate-review` | 게이트 답 교정 → 판정 → 기록 |
| `/verify` | 포맷·컴파일·테스트 + 실제 기동 관찰 증거 |
| `/adr` · `/trouble` | 설계 결정 기록 · 트러블슈팅 기록 |
| `/git-handoff` | 사용자가 실행할 git 명령을 커밋 단위로 |
| `/new-api` · `/domain-model` · `/write-test` · `/refactor-legacy` | 코드 작업을 규칙 순서대로 |

## 에이전트

| 에이전트 | 하는 일 |
|---|---|
| `boundary-auditor` | ArchUnit이 못 보는 경계 위반(여러 모듈 쓰기 트랜잭션, 다른 모듈 테이블 JOIN, 잃을 수 있는 이벤트)을 찾아 경계 지도와 비교 |
| `convention-reviewer` | 변경분을 규칙으로 리뷰. 기계 검사를 먼저 돌리고, 기계가 못 잡는 판단만 지적 |

둘 다 읽기 전용이다. 코드를 고치지 않는다.

## ArchUnit 기준선

- 테스트: `src/test/java/com/bready/server/architecture/` (레이어 · 코딩 규칙 · 모듈 경계)
- 기준선: `src/test/resources/archunit_store/`. 기록된 옛 위반은 통과, 새 위반은 실패. 위반을 고치면 테스트가 기준선에서 지운다.
- 기준선은 **규칙 설명 문자열**이 키다. `because(...)`를 바꾸면 새 규칙이 된다.
- 새 규칙 추가: `archunit.properties`의 `allowStoreCreation`을 잠깐 `true` → 실행 → `false`. 기준선을 손으로 고쳐 새 위반을 인정하지 않는다.
- 현재 수치: `docs/architecture/context-map.md` 11번.

## 검증 · 수정

```bash
.claude/hooks/test-hooks.sh                 # 훅 회귀 테스트. 훅을 고쳤으면 반드시
.claude/scripts/verify.sh                   # 포맷 → 컴파일 → 전체 테스트 (--fast: 테스트 생략)
./gradlew test --tests 'com.bready.server.architecture.*'

# 훅 단독 실행 (stdin으로 훅 JSON)
echo '{"tool_input":{"command":"git push"}}' | .claude/hooks/guard-bash.sh
echo '{"tool_input":{"file_path":"'$PWD'/.env","content":"x"}}' | .claude/hooks/guard-write.sh
echo '{"prompt":"API 추가해줘"}' | .claude/hooks/prompt-router.sh
echo '{}' | .claude/hooks/session-context.sh
```

- 오탐이 나면 훅을 우회하지 말고 패턴을 고치고 `test-hooks.sh`에 케이스를 추가한다. 막아야 할 것과 **막으면 안 되는 것**을 둘 다 케이스로 남긴다.
- Stop 게이트를 잠깐 끄려면 `CLAUDE_SKIP_VERIFY_GATE=1`.
- 설정이나 훅을 바꾼 뒤에는 새 세션에서 반영되는 것이 있다(SessionStart 등).

## 유지보수 규칙

1. 새 규칙은 `rules/`의 해당 파일에만 쓴다. 라우팅이 필요하면 `rules/00-map.md`와 `prompt-router.sh`에 한 줄.
2. 기계로 확인할 수 있는 규칙이면 ArchUnit(구조) 또는 `post-edit-lint.sh`(소스 패턴)에 올린다. 확인할 수 없는 것만 문서로 남긴다.
3. 규칙의 "BReady 현황" 근거(`파일:줄`)는 그 코드를 고치면 지우고, 줄이 밀리면 갱신한다. 낡은 근거는 규칙 전체를 믿지 못하게 만든다.
4. 규칙이 코드 현실과 어긋나면 코드를 확인하고 규칙을 고친다.
5. `CLAUDE.md`가 150줄을 넘으면 `rules/`로 나눈다.
6. 공개 파일에는 내부 진행 라벨(조각 번호, 결정 번호)이나 개인 기록을 넣지 않는다. 개인 기록은 `docs/private/`(gitignore).