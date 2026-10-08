---
name: git-handoff
description: 작업을 마친 뒤 사용자가 직접 실행할 git 명령어를 커밋 단위로 쪼개서 정리한다. Claude는 git을 실행하지 않는다. "커밋 정리해줘", "커밋 쪼개서 알려줘" 또는 조각 마무리 시 사용.
---

# /git-handoff — 커밋 명령어 정리

**Claude는 `git add/commit/push`를 실행하지 않는다.** (guard-bash가 차단) 명령어를 정리해 넘기는 것까지가 역할이다.

## 절차
1. `git status --short --untracked-files=all`과 `git diff --stat`으로 변경 전체를 확인한다.
2. **`docs/private/`는 절대 포함하지 않는다.** (gitignore 확인: `git check-ignore -q docs/private/roadmap.md`)
3. 변경을 **성격별 커밋**으로 나눈다. 한 커밋 = 한 가지 이유.
   - 순서: 빌드·도구 → 리팩터링(동작 불변) → 기능/구조 변경 → 테스트 → 문서
   - **포맷 전용 변경(spotlessApply 등)은 반드시 단독 커밋**, 해시를 `.git-blame-ignore-revs`에 추가하도록 안내.
   - 구현과 그 설계서는 같은 커밋도 가능하지만, 학습일지·트러블슈팅 갱신은 따로 묶는 편이 읽기 좋다.
4. 각 커밋마다:
   - `git add <파일 경로를 하나씩 명시>` — `git add .` / `-A` 금지.
   - 커밋 메시지는 레포 관례 `[타입] 한국어 요약`. 타입: `feat` `fix` `refactor` `test` `docs` `chore`.
   - 이 커밋을 왜 나눴는지 한 줄 설명.
5. 마지막에 확인 명령: `git status --short`(남은 변경 없음, docs/private 안 보임), `git log --oneline -N`.

## 출력 형식
```bash
# 1) {이 커밋의 이유}
git add path/a path/b
git commit -m "[chore] …"
```
push·PR은 사용자가 원할 때 직접 한다. upstream에는 어떤 경우에도 push하지 않는다.