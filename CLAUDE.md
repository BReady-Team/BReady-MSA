# CLAUDE.md — BReady-MSA

> 매 세션 자동 로드. **항상 필요한 것만** 둔다. 상세 규범은 `.claude/rules/`, 절차는 `.claude/skills/`, 강제는 `.claude/hooks/`.
> 현재 위치·게이트 상태는 여기 쓰지 않는다 → `docs/private/roadmap.md`(세션 시작 훅이 주입).

## 1. 이 레포는 무엇인가

- 팀 모놀리식 **BReady-Backend**(Spring Boot)를 히스토리째 클론한 **독립 레포**. 한 사람이 **Strangler Fig** 방식으로 MSA로 전환한다.
- 빨리 끝내는 것이 아니라 **모든 조각의 "왜"와 "흐름"을 사용자가 자기 말로 설명할 수 있게 되는 것**.
- 원격: `origin` = BReady-MSA, `upstream` = BReady-Backend(**push URL DISABLED**). 기본 브랜치 `develop`.
- 배포 없음. 전부 로컬(docker-compose, 나중에 필요하면 kind).

## 2. 역할

| Claude | 사용자 |
|---|---|
| 코드 작성 · 테스트 · 실행 · 관찰(로그·curl·DB 조회) · 문서 초안 | 이해 · 결정 · **git 전부**(commit/push/branch/PR) |

- 설계 선택지가 있으면 **2~3개 + 트레이드오프 + 추천**을 제시하고 사용자 결정을 기다린다. 결정 전에 구현하지 않는다.
- 작업을 마치면 사용자가 실행할 git 명령어를 **커밋 단위로 쪼개서** 정리한다(`/git-handoff`).

## 3. 절대 규칙

1. **원본 레포·팀에 영향 금지.** upstream push, 팀원 멘션, 팀 리소스(ghcr 이미지, EC2, `bready.site`, 팀 시크릿) 접근 금지. (guard-bash가 차단)
2. **git은 사용자가 한다.** Claude는 명령어만 정리한다. (권한 규칙 + guard-bash가 차단)
3. **시크릿 커밋 금지.** 설정에는 `${ENV_VAR:로컬더미값}`만, 실제 값은 `.env`(읽기 금지)에만. (guard-write가 차단)
4. **기존 동작을 깨지 않는다.** 리팩터링·추출 전에 안전망 테스트부터. 모놀리식은 항상 돌아가는 상태로.
5. **YAGNI.** 로드맵에 아직 안 온 인프라를 미리 깔지 않는다. 각 조각은 **직전 단계의 고통**을 해결하는 형태여야 한다.
6. **막히는 건 커리큘럼.** 우회하지 말고 원인을 같이 이해한다. 해결하면 `docs/TROUBLESHOOTING.md`에 기록.
7. **"됐다" 대신 증거.** 테스트 출력·로그·curl·쿼리 결과를 보여준다.
8. **코드는 생략 없이 완전하게.** "…(생략)" 금지.
9. **모호하거나 규칙이 충돌하면 추측하지 말고 묻는다.**
10. **옛 팀 배포 자산**(`deploy/`, `nginx/`, `docker-compose-{blue,green,prod}.yml`, `.coderabbit.yaml`)은 두고 실행·수정하지 않는다.

## 4. 조각 진행 방식 (한 턴에 한 조각)

| 단계 | 내용 | 스킬 |
|---|---|---|
| 1 왜 지금 | 직전 단계의 고통·한계와 연결 | `/piece-start` |
| 2 설계 | 선택지 2~3개 → 추천 → **사용자 결정** → 중요하면 ADR | `/piece-start`, `/adr` |
| 3 흐름 미리보기 | 요청/데이터/이벤트 흐름을 텍스트 시퀀스로 (before → after) | `/piece-start` |
| 4 구현 | 작은 단위, 완전한 코드 | — |
| 5 검증 | 테스트 + 실제 기동·관찰 증거 | `/verify` |
| 6 코드 워크스루 | **요청이 들어와서 나가는 순서대로**, "이 줄을 빼면 무엇이 깨지나"까지 | `/piece-close` |
| 7 이해 게이트 | "왜/만약" 질문 2~3개 → 사용자 답 → 교정 | `/piece-close`, `/gate-review` |
| 8 기록 | 설계서 · 학습일지 · 트러블슈팅 · ADR | `/gate-review`, `/trouble` |

- **게이트가 `OPEN`이면 다음 조각으로 가지 않는다.** 사용자가 대충 넘어가려 해도 한 번은 붙잡는다. 사용자가 그래도 넘어가겠다고 하면 `SKIPPED`로 기록한다.
- **하네스(H) 조각은 경량 모드**: 묶어서 진행, 이해 게이트 질문 없음(`SKIPPED`로 기록). **본 작업(0단계~)은 엄격 모드**.
- 설명할 땐 **commerce-msa(Phase 1)에서 겪은 것과 연결**한다(예: "commerce-msa 4단계 Outbox와 같은 문제인데, 여기선 ~가 다르다").

## 5. 로드맵 (요약 — 체크와 현재 위치는 `docs/private/roadmap.md`)

H 하네스 → **0** 안전망(키 없이 로컬 기동 + 통합테스트) → **1** 경계 지도 확정 → **2** 모듈러 모놀리스(JPA 연관→ID, 공개 API, 경계 래칫) → **3** Strangler facade(Gateway) → **4** 첫 추출(Place Catalog, 공유 DB→스키마→DB 분리) → **5** 다음 추출 + 고통이 생긴 곳에만 Kafka/Outbox/Saga/서킷브레이커/추적 → **6** 인증 분리·관측성·(선택) k8s

- 경계 현황: [docs/architecture/context-map.md](docs/architecture/context-map.md). 잠정 방침: **플래닝 코어(plan+후보+trigger)는 쪼개지 않고 가장자리부터 뗀다**(1단계에서 ADR로 확정).
- 인증(JWT)은 6단계 전에는 손대지 않는다.

## 6. 문서 — 공개 / 비공개

레포는 **public**이다.

| 공개 (커밋) | 비공개 `docs/private/` (gitignore) |
|---|---|
| `docs/architecture/` 경계 지도 · `docs/design/` 조각 설계서 · `docs/adr/` · `LEARNING_JOURNEY.md` · `TROUBLESHOOTING.md` | 로드맵·게이트 상태(`roadmap.md`), 게이트 Q&A(`gates.md`), 하네스 구성안·하네스 조각 기록 |

- 공개 문서에 게이트 질문·사용자 답·개인 메모를 넣지 않는다. 공개 파일에서 비공개 문서를 링크하지 않는다.
- 커밋 명령어에 `docs/private/`를 넣지 않는다.

## 7. 코드 컨벤션 — 상세는 `.claude/rules/` (지도: `00-map.md`)

**코드를 쓰기 전에 해당 규칙 파일을 읽는다.** 주변 옛 코드를 흉내 내지 않는다(BReady 옛 코드는 규칙과 다른 곳이 많다).

- **BReady 뼈대 유지**: 모듈별 패키지(`controller/service/repository/domain/dto/exception`) + `global/`, `CommonResponse<T>`, `ApplicationException` + 모듈별 `ErrorCase`, 엔티티 정적 팩토리 + `@NoArgsConstructor(PROTECTED)`, 생성자 주입.
- **핵심 기준 한 줄씩**
  - 엔티티: setter 금지, 의도 드러나는 메서드, 규칙 위반은 ErrorCase, 다른 모듈은 ID 참조, 시간은 파라미터 (`entity.md`)
  - 서비스: 클래스 `readOnly` + 쓰기만 `@Transactional`, 조회 → 엔티티 검증·변경 → `Response.from()`, 외부 I/O는 트랜잭션 밖, `Clock` 주입 (`service-transaction.md`)
  - 컨트롤러: 위임만, Swagger는 `{Domain}Api` 인터페이스 (`controller-api.md`)
  - DTO: 요청·응답 모두 record, `{Resource}{Action}Request/Response` (`dto.md`) — **auth DTO record 전환은 LoggingAspect 인자 로깅 제거 후**
  - 예외: `ApplicationException.from(...)`만, 코드 `{DOMAIN}_{NNN}`(전환은 ADR과 별도 조각) (`exception.md`)
  - 테스트: 엔티티 mock 금지·fixture, BDD, AssertJ, `@WebMvcTest(controllers=…)`, 리포지토리·통합은 Testcontainers MySQL (`testing.md`)
  - 경계: 남의 Repository·Entity·테이블 금지, 이벤트는 발행자 소유, 위반은 래칫으로 늘지 않게 (`msa-boundary.md`)
- **보이스카우트 규칙**: 옛 코드는 건드릴 때 정리한다. 단, 안전망 테스트가 있는 범위에서만(`/refactor-legacy`).

## 8. 빌드 · 실행 · 테스트

```bash
.claude/scripts/verify.sh            # 포맷 → 컴파일 → 전체 테스트 (--fast: 테스트 생략)
./gradlew spotlessApply              # 포맷 맞추기. 손으로 맞추지 않는다 (palantir-java-format)
./gradlew test                       # 전체 테스트 (현재 contextLoads 1건 실패 — TROUBLESHOOTING T-002)
.claude/hooks/test-hooks.sh          # 훅을 고쳤으면 반드시
```
- Java를 고친 채 끝내려 하면 Stop 훅이 포맷·컴파일을 검사하고, 깨져 있으면 끝내지 못하게 막는다.
- Java 21, Spring Boot 3.5.7, Gradle 9.2.1, MySQL 8.0, Redis 7.2. 베이스 패키지 `com.bready.server`.
- 외부 의존(OpenAI, S3, 카카오/네이버 OAuth, 카카오 로컬)은 **실제 키 없이 로컬에서 돌 수 있어야 한다**(0단계 목표).

## 9. 알려진 함정

- Docker 29 + Testcontainers: `Could not find a valid Docker environment` / HTTP 400 → `build.gradle` test 태스크에 `systemProperty 'api.version', '1.44'`.
- Kafka Testcontainer는 `ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0")` (apache/kafka 이미지는 advertised.listeners 에러).
- `LoggingAspect`가 모든 서비스 인자를 `toString()`으로 INFO 로깅한다 → DTO를 record로 바꾸면 비밀번호·토큰이 평문으로 찍힌다. **record 전환 전에 로깅 정책부터.**

## 10. 하네스 지도

| 위치 | 역할 |
|---|---|
| `.claude/settings.json` | 권한(allow/ask/deny) + 훅 등록 |
| `.claude/hooks/` | `guard-bash`·`guard-write`(차단), `session-context`(세션 시작 주입), `prompt-router`(게이트 리마인더 + 규칙 파일 라우팅), `post-edit-lint`(새로 쓴 코드의 규칙 위반 알림), `stop-verify-gate`(끝내기 전 포맷·컴파일·아키텍처 테스트), `test-hooks.sh`(회귀) |
| `src/test/.../architecture/` | ArchUnit 레이어·코딩·모듈 경계 규칙. 옛 위반은 기준선(`archunit_store`), 새 위반은 빌드 실패 |
| `.claude/skills/` | 조각 진행: `/piece-start` `/piece-close` `/gate-review` `/adr` `/trouble` `/verify` `/git-handoff` · 코드: `/new-api` `/domain-model` `/write-test` `/refactor-legacy` |
| `.claude/scripts/` | `verify.sh` |
| `.claude/rules/` | 규범 10개 + 지도 `00-map.md` |
