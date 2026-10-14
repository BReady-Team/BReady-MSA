# 3단계 Strangler facade

> 진행 중 (2026-10-14 시작). 이 단계의 결정은 단계가 끝날 때 ADR-0003으로 남긴다.

## 이 단계의 목표

서비스를 떼어내기 전에 앞단에 입구(Gateway)를 세운다. 지금은 모든 경로를 모놀리식으로 보낸다. 증명할 것은 하나다: **Gateway를 끼우기 전과 후의 응답이 같다.** 4단계에서 Place Catalog를 떼면 라우트만 바꾸고 클라이언트는 주소 하나만 계속 안다.

| 조각 | 내용 | PR | 상태 |
|---|---|---|---|
| 3-1 | 모놀리식을 `monolith/`로 이동(동작 변경 없음), 하네스 경로 수정 | PR1 `refactor/multi-module` | 설계 확정 |
| 3-2 | `gateway-service` + 전체 경로를 모놀리식으로 라우팅 + 전후 비교 검증 | PR2 `feat/gateway-routing` | 대기 |
| 3-3 | 로컬 compose에 Gateway 추가, 옛 배포 자산 정리 | PR3 `chore/local-compose` | 대기 |

## 왜 지금

모놀리식 안에서는 아직 Gateway가 없어서 생기는 문제가 없다. 4단계에서 생길 문제를 미리 막는 준비다.

- 클라이언트는 모놀리식 주소(`:8080`) 하나만 안다.
- Place Catalog를 떼면 `/api/v1/places/**`의 일부가 다른 프로세스로 가야 한다. 앞단 입구가 없으면 클라이언트가 주소 두 개를 알아야 한다. 외부 계약이 바뀐다.
- 그래서 떼기 전에 입구를 세우고 "끼워도 아무것도 바뀌지 않는다"를 먼저 증명한다.

팀 레포의 `nginx/bready.conf`도 앞단 프록시였지만, 같은 앱 두 벌을 번갈아 띄우는 blue/green 배포용(upstream 하나)이었다. 여기서 세우는 것은 경로마다 목적지가 다른 라우팅이다.

## 설계 (선택지 · 결정)

| 결정 | 선택지 | 결정 | 이유 |
|---|---|---|---|
| Gateway 구현 | Spring Cloud Gateway / nginx / 별도 Gateway 없이 모놀리식이 넘겨 줌 | **Spring Cloud Gateway** | 라우팅을 자동 테스트로 검증할 수 있다. 5단계 서킷브레이커, 6단계 JWT 필터가 같은 자리에 붙는다 |
| 레포 구조 | 루트가 모놀리식 + `include 'gateway'` / 루트 `settings.gradle`로 묶은 멀티 모듈 / 서비스마다 독립 Gradle 프로젝트 | **서비스마다 독립 프로젝트**(`gateway-service/`, `monolith/`, 4단계 `place-catalog-service/`). 루트에는 빌드 파일이 없다 | 루트가 모놀리식이면 떼어낸 서비스가 모놀리식의 부속처럼 보인다. 독립 프로젝트는 서로의 코드를 가져다 쓸 방법이 아예 없어 독립이 구조로 드러난다. 모놀리식은 지금 루트의 빌드 파일과 `src/`를 그대로 한 층 내리면 된다. 대가: 전체 빌드를 한 명령으로 돌릴 수 없고(하네스가 서비스 단위로 빌드), 빌드 설정이 서비스마다 중복된다. 공통 코드 문제는 4단계에서 실제로 생길 때 정한다 |
| 인증 · CORS | 모놀리식 그대로, Gateway는 통과 / Gateway에서도 JWT 검증 | **모놀리식 그대로** | 6단계 전에는 JWT를 건드리지 않는다. CORS 헤더는 한 곳에서만 붙인다(두 곳에서 붙이면 `Access-Control-Allow-Origin`이 두 번 나가 브라우저가 거부한다) |
| PR 분할 | 1개 / 3개 | **3개** | PR1은 "파일만 옮겼다", PR2는 "끼워도 응답이 같다", PR3는 정리와 기록. 이동과 새 기능이 섞이면 수백 줄의 이동 diff 속에서 진짜 변경을 찾을 수 없다 |

## 흐름 (before → after)

```
[before]
Client ──► :8080 Monolith
             JwtFilter → Controller → Service ─BEGIN─ DB ─COMMIT─
           ◄── CommonResponse

[after]
Client ──► :8000 Gateway ──(라우트: /** → http://localhost:8080)──► :8080 Monolith
             (JWT 안 봄, 헤더·본문 그대로)      JwtFilter → Controller → Service ─BEGIN─ DB ─COMMIT─
       ◄── 상태코드·헤더·본문 그대로 ◄──────────────────────────── CommonResponse
```

4단계에서는 라우트가 늘어난다. `/api/v1/places/candidates/**`는 Planning 소유라 모놀리식으로 가야 하므로 `/api/v1/places/**`보다 먼저 매칭돼야 한다. 라우트는 위에서부터 비교하고 먼저 맞은 것이 이긴다.

## 구현 파일

### 3-1 모놀리식을 `monolith/`로 이동

| 파일 | 변경 |
|---|---|
| `build.gradle` `settings.gradle` `gradlew` `gradlew.bat` `gradle/` `Dockerfile` `src/` `db/` | 내용 그대로 `monolith/` 아래로 이동 |
| `.gitattributes` | `/gradlew` → `gradlew`. 루트에 고정된 패턴이라 `monolith/gradlew`의 줄바꿈 규칙(LF)이 빠질 뻔했다 |
| `.claude/scripts/verify.sh`, `.claude/hooks/stop-verify-gate.sh` | Gradle을 `monolith/`에서 실행. 서비스가 하나라 디렉터리를 고정했다(서비스가 늘면 고친 파일의 서비스마다) |
| `.claude/hooks/guard-write.sh`, `post-edit-lint.sh`, `guard-bash.sh` | 경로 패턴을 `{서비스}/src/...`로. 특정 서비스에 고정하지 않아 새 서비스도 같은 보호를 받는다 |
| `.claude/hooks/test-hooks.sh` | 케이스 경로를 `monolith/` 아래로, 새 구조 케이스 11개 추가 |
| `CLAUDE.md`, `.claude/README.md`, `.claude/rules/`, `.claude/skills/`, `.claude/agents/`, `docs/architecture/context-map.md` | 현재 위치를 가리키는 경로만 수정. 지난 단계의 기록(2단계 설계서, 트러블슈팅)은 그 시점의 사실이라 두었다 |

## 검증 증거

### 3-1

- 이동한 추적 파일 304개 전부 커밋된 내용과 바이트 단위로 같다(`git hash-object` 비교). `gradlew` 실행 권한 755 유지. 그래서 git은 전부 rename으로 기록한다.
- `monolith/`에서 포맷·컴파일·전체 테스트: 189개 통과, 실패 0(이동 전과 같다).
- 훅 회귀 테스트: 163개 통과, 실패 0.
- Stop 훅: 포맷이 깨진 임시 파일을 `monolith/src`에 만들자 `포맷 위반이 있다(monolith)`로 막았다. 확인 뒤 지웠다.

## 워크스루 요약

## 알려진 한계