# 경계 지도 (Context Map) — 초안 

> **상태**: 초안 (Phase 2 시작 시점의 "있는 그대로" 스냅샷)
> **분석 방법**: `com.bready.server.*` 전체 import 그래프 추출 → 엔티티 연관관계·JPQL/Native 쿼리·`@Transactional` 메서드 전수 확인
> **이 문서의 용도**: 로드맵 1단계(경계 지도)의 출발점. 2단계(모듈러 모놀리스)에서 "무엇을 끊어야 하는지"의 체크리스트
> 위반을 하나 끊을 때마다 이 문서의 해당 행을 갱신

---

## 0. 한 문장 요약

**패키지는 9개지만, 실제로는 `plan` · `place(후보)` · `trigger` 세 패키지가 하나의 덩어리로 엉켜 있다.**
`CategoryState`(plan 소유)를 place·trigger 서비스가 직접 잠그고 바꾸며, 엔티티끼리 양방향으로 참조한다.
반대로 `place(장소 카탈로그)` · `recommendation` · `stats` · `user/auth`는 바깥쪽에 비교적 깔끔한 선이 있다.

---

## 1. 한눈에 보기 — 패키지 의존 그래프

화살표 `A ──▶ B` = "A가 B의 클래스를 import 한다". 괄호는 **무엇을** 가져다 쓰는지(R=Repository, E=Entity, S=Service, Ev=Event, Err=ErrorCase).

```
                         ┌──────────┐
                         │  global  │◀── 모든 모듈 (CommonResponse, ApplicationException, BaseEntity, @CurrentUser)
                         └────┬─────┘
                              │ (역의존) Err: AuthErrorCase, S3ErrorCase, StatsErrorCase
                              ▼
   ┌──────┐  R,E,Err  ┌──────┐   S    ┌────┐
   │ auth │─────────▶ │ user │──────▶ │ s3 │
   └──────┘           └──────┘        └────┘
                         ▲
                         │ R,E  (플랜 상세에 작성자 닉네임/프로필)
                         │
   ┌──────────────── 플래닝 코어 (강결합) ─────────────────────┐
   │                     ┌──────┐                          │
   │          ┌─────────▶│ plan │◀─────────┐               │
   │   R,E    │  R,E     └──┬───┘   R,E    │               │
   │ (State,  │  (양방향!)   │ R,E(후보)      │               │
   │ Category,│             ▼              │               │
   │  Log)  ┌─┴─────────────────┐   ┌──────┴──┐            │
   │        │ place             │◀──│ trigger │            │
   │        │ (후보+카탈로그)      │R,E └────┬────┘           │
   │        └───────────────────┘         │ Ev(stats.event)│
   └──────────────────────────────────────┼────────────────┘
               ▲  S(PlaceSearchService)   │         ▲ R (Trigger/Decision/SwitchLog)
               │  R,E                     ▼         │
       ┌───────┴────────┐   R,E      ┌───────┐──────┘
       │ recommendation │──────────▶ │ stats │──────▶ plan (R,E)
       └────────────────┘  (trigger, └───────┘
                            plan도 R,E)
```

**순환 의존 3개** (모듈러 모놀리스에서 반드시 끊어야 할 것):

| # | 순환 | 어디서 |
|---|---|---|
| C1 | `plan ⇄ place` | `PlanCategory.candidates`(OneToMany) ↔ `PlaceCandidate.category`(ManyToOne) **엔티티 양방향** + 서비스끼리 서로의 Repository 사용 |
| C2 | `trigger ⇄ stats` | trigger가 `stats.event.*`를 발행, stats가 `trigger.repository.*`를 조회. **이벤트 클래스가 소비자(stats) 패키지에 있음** |
| C3 | `global ⇄ 도메인` | `GlobalExceptionHandler`가 `S3ErrorCase`·`StatsErrorCase`를, `AuthUtils`가 `AuthErrorCase`를 import |

---

## 2. 컨텍스트(현재 패키지)별 책임과 소유 데이터

| 패키지 | 책임 | 엔티티 / 저장소 | 테이블 | 외부 의존 | API prefix |
|---|---|---|---|---|---|
| **auth** | 회원가입·로그인·토큰 발급/갱신/로그아웃, 카카오·네이버 OAuth | `RefreshToken`(@RedisHash) | Redis `refresh_token:*` | 카카오/네이버 OAuth (WebClient) | `/api/v1/auth` |
| **user** | 내 프로필 조회·닉네임/소개/이미지 변경 | `User`, `UserProfile`, `UserStatus`, `UserAuthProvider` | `users`, `user_profiles` | S3(이미지, `s3` 경유) | `/api/v1/users` |
| **plan** | 플랜 CRUD·공유 토큰·카테고리 추가/삭제/순서/타입 변경·플랜 상세 조립 | `Plan`, `PlanCategory`, `CategoryState`, `CategorySelectionLog` | `plans`, `plan_categories`, `category_states`, `category_selection_logs` | – | `/api/v1/plans`, `/api/v1/plans/{id}/categories` |
| **place** | ① 장소 검색(카카오) ② 장소 마스터(`places`) ③ 카테고리별 **장소 후보** 등록/대표 지정/삭제 | `Place`, `PlaceCandidate`, `PlaceCategoryType`(enum) | `places`, `place_candidates` | 카카오 로컬 검색 API | `/api/v1/places` |
| **trigger** | 상황 발생(트리거) → 결정(KEEP/SWITCH) → 대표 후보 전환(SwitchLog) | `Trigger`, `Decision`, `SwitchLog`, `TriggerType`, `DecisionType` | `triggers`, `decisions`, `switch_logs` | – | `/api/v1/triggers` |
| **recommendation** | 트리거 이후 대체 카테고리/장소 추천 (규칙 기반 + OpenAI 재정렬, Redis 캐시) | **없음** (port/adapter 구조, Redis 캐시만) | Redis `reco:*` | OpenAI(Spring AI), 카카오(place 경유) | `/api/v1/recommendations` |
| **stats** | 통계 요약·트리거 분석·플랜별 전환 수·최근 활동 | `PlanStats`(사전 집계, Materialized View 패턴) | `plan_stats` | – | `/api/v1/stats` |
| **s3** | 파일 업로드/삭제/URL 생성 | 없음 | – | AWS S3 | `/api/v1/files` (테스트용) |
| **global** | 공통 응답·예외·감사(BaseEntity)·보안(JWT 필터)·AOP 로깅·설정 | `BaseEntity`(@MappedSuperclass) | – | – | – |

**관찰**
- `place` 패키지 안에 **성격이 다른 두 개념이 섞여 있다.**
  - `Place` = 카카오에서 가져온 장소 **카탈로그**(외부 ID로 dedup, 누구의 플랜과도 무관)
  - `PlaceCandidate` = "**이 플랜의 이 카테고리**에 올려 둔 후보" → 생명주기가 `PlanCategory`에 묶여 있다(카테고리 타입을 바꾸면 후보가 전부 soft delete됨)
  - → **바운디드 컨텍스트로 보면 `PlaceCandidate`는 plan 쪽 개념이다.** 
- `recommendation`은 **자기 테이블이 없다.** 남의 데이터를 읽어 계산만 하는 컨텍스트
- `stats`는 **읽기 모델(read model)** 이다. `plan_stats`는 트리거/전환 이벤트로 갱신되는 사전 집계 테이블이고, 나머지 통계 API는 trigger·plan 테이블을 직접 JOIN 한다.

---

## 3. 의존 방향 매트릭스 — "누가 누구의 무엇을 직접 쓰는가"

행 = 사용하는 쪽, 열 = 사용되는 쪽. **R** = Repository 직접 주입, **E** = Entity 직접 사용, **S** = Service 호출, **Ev** = 이벤트 클래스, **Err** = ErrorCase.
**R은 MSA 관점에서 가장 나쁜 결합**(남의 테이블에 직접 쿼리)이고, S는 "공개 API로 바꾸기 쉬운" 결합이다.

| 사용 ↓ \ 피사용 → | user | plan | place | trigger | stats | s3 | auth |
|---|---|---|---|---|---|---|---|
| **auth** | **R** E Err | | | | | | |
| **user** | | | | | | **S** | |
| **plan** | **R** E | | **R** E | | | | |
| **place** | | **R** E | | | | | |
| **trigger** | | **R** E | **R** E | | **Ev** | | |
| **recommendation** | | **R** E Err | **R** E **S** Err | **R** E Err | | | |
| **stats** | | **R** E | | **R** E | | | |
| **global** | | | | | Err | Err | Err |

**구체 목록 (파일 단위)**

| 사용하는 쪽 | 직접 쓰는 남의 것 | 위치 |
|---|---|---|
| auth | `UserRepository`, `UserProfileRepository`, `User`, `UserProfile`, `UserErrorCase` | `AuthService`, `KakaoAuthTransactionHandler`, `NaverAuthTransactionHandler` |
| user | `S3Uploader` (Service) | `UserService.updateProfileImage` |
| plan | `UserRepository.findByIdWithProfile` | `PlanService.buildPlanDto` (작성자 닉네임/이미지) |
| plan | `PlaceCandidateRepository.findAllAliveByCategoryIdForUpdate` | `PlanCategoryService.updateCategoryType` |
| place | `PlanCategoryRepository`, `CategoryStateRepository`, `CategorySelectionLogRepository` | `PlaceCandidateService` (3개 메서드 전부) |
| trigger | `PlanCategoryRepository`, `CategoryStateRepository`, `PlaceCandidateRepository` | `TriggerService`, `SwitchService` |
| trigger | `stats.event.TriggerCreatedEvent`, `SwitchLogCreatedEvent` | `TriggerService`, `SwitchService` |
| recommendation | `TriggerRepository`, `PlanCategoryRepository`, `CategoryStateRepository`, `PlaceCandidateRepository` | `PlaceRecommendationService`, `RecommendValidationService` |
| recommendation | `PlaceSearchService` (Service) | `RuleBasedRecommendationAdapter` |
| recommendation | `PlanCategory`(엔티티)가 **port 인터페이스 시그니처**에 노출 | `CategoryRecommendationPort`, `PlaceRecommendationPort` |
| stats | `PlanRepository`, `PlanCategoryRepository`, `TriggerRepository`, `DecisionRepository`, `SwitchLogRepository` | `StatsService`, `TriggerStatsService`, `RecentActivityStatsService`, `PlanActivityStatsService`, `PlanStatsMaterializedService`, `PlanStatsUpdater`, `PlanStatsJoinService` |

> 주목: **"남의 Service를 호출"하는 곳은 2곳뿐**(`user→s3`, `recommendation→place`). 나머지 모든 모듈 간 결합은 **Repository 직접 주입**이다.
> 즉 2단계의 핵심 작업은 "남의 Repository 직접 사용 → 그 모듈이 공개한 API(인터페이스) 호출"로 바꾸는 것이다.

---

## 4. 모듈 경계를 넘는 JPA 연관관계

| 엔티티 (소유 패키지) | 필드 | → 대상 (소유 패키지) | 종류 | MSA로 가면 |
|---|---|---|---|---|
| `PlaceCandidate` (place) | `category` | `PlanCategory` (plan) | `@ManyToOne LAZY` | ID 참조 또는 같은 컨텍스트로 이동 |
| `PlanCategory` (plan) | `candidates` | `PlaceCandidate` (place) | `@OneToMany(mappedBy)` | **양방향 → 순환 C1의 원인** |
| `PlaceCandidate` (place) | `place` | `Place` (place) | `@ManyToOne LAZY` | 패키지는 같지만 "후보 ↔ 카탈로그"는 다른 개념 (§8) |
| `Trigger` (trigger) | `plan` | `Plan` (plan) | `@ManyToOne LAZY` | `planId` (Long) |
| `Trigger` (trigger) | `category` | `PlanCategory` (plan) | `@ManyToOne LAZY` | `categoryId` (Long) |
| `SwitchLog` (trigger) | `fromCandidate`, `toCandidate` | `PlaceCandidate` (place) | `@ManyToOne LAZY` | `fromCandidateId`, `toCandidateId` |

**경계 안쪽(문제없음)**: `Decision.trigger`(1:1), `SwitchLog.decision`(1:1), `CategoryState.category`(1:1), `UserProfile.user` ↔ `User.userProfile`(1:1 양방향)

**이미 ID 참조로 되어 있는 곳**

| 필드 | 가리키는 것 |
|---|---|
| `Plan.ownerId` | `User.id` (user) |
| `Trigger.candidateId` | `PlaceCandidate.id` (place) — 트리거 발생 당시 대표 후보 스냅샷 |
| `CategoryState.currentCandidateId` | `PlaceCandidate.id` (place) |
| `CategorySelectionLog.categoryId`, `candidateId` | plan / place |
| `PlanStats.planId` | `Plan.id` (plan) |

---

## 5. 모듈 경계를 넘는 JOIN 쿼리

### 5.1 JPQL — 엔티티 연관관계를 타고 남의 테이블까지 JOIN

| Repository (소유) | 메서드 | JOIN 경로 | 누가 쓰나 |
|---|---|---|---|
| `TriggerRepository` (trigger) | `countByOwnerIdAndPeriod`, `countByTriggerType` | `Trigger ⋈ Plan` (ownerId 필터) | stats |
| `DecisionRepository` (trigger) | `findByIdWithTriggerPlanCategory` | `Decision ⋈ Trigger ⋈ PlanCategory ⋈ Plan` (fetch) | trigger(Switch) |
| `DecisionRepository` | `findRecentKeepDecisionActivities`, `countByOwnerIdAndPeriod`, `findRecentKeepActivities` | `Decision ⋈ Trigger ⋈ Plan` | stats |
| `SwitchLogRepository` (trigger) | `findRecentSwitchActivities`, `countByOwnerIdAndPeriod`, `countSwitchByPlanIdAndPeriod`, `findRecentSwitchActivitiesAllPlans` | `SwitchLog ⋈ Decision ⋈ Trigger ⋈ Plan` | stats |
| `PlanCategoryRepository` (plan) | `findAllDetailByPlanId` | `PlanCategory ⋈ PlaceCandidate ⋈ Place` (fetch) | plan(상세) |
| `PlaceCandidateRepository` (place) | `findByIdWithCategoryAndPlace`, `findByIdWithCategory`, `findAliveByIdWithCategoryAndPlace` | `PlaceCandidate ⋈ PlanCategory (⋈ Place)` (fetch) | place, recommendation |
| `PlaceCandidateRepository` | `existsAliveByIdAndCategoryId`, `findAliveByIdAndCategoryId`, `findAllAliveByCategoryIdForUpdate` | `pc.category.id` 경로 (FK 컬럼만 사용, 실제 JOIN은 없음) | trigger, plan |

**패턴**: trigger/stats 쿼리의 대부분은 **`ownerId`로 필터하려고 `Plan`까지 JOIN** 한다.
→ 분리 시 대표적 해법은 (a) `triggers`에 `owner_id`를 비정규화, (b) stats가 이벤트로 자기 읽기 모델을 구축. (5단계 주제)

### 5.2 Native SQL — 테이블 4개를 한 번에

`PlanRepository.findPlanSwitchStatsOptimized` 
```sql
plans (서브쿼리, LIMIT push-down)
  LEFT JOIN triggers    ON t.plan_id = p.id
  LEFT JOIN decisions   ON d.trigger_id = t.id
  LEFT JOIN switch_logs ON sl.decision_id = d.id
```
- plan 패키지의 Repository가 **trigger 컨텍스트 테이블 3개를 직접 JOIN**
- 사용처는 `PlanStatsJoinService` 하나인데, **이 서비스는 어떤 컨트롤러에서도 쓰이지 않는다**(README의 성능 개선 1차 실험 흔적. 현재 API는 `PlanStatsMaterializedService` 사용)
- → **죽은 코드를 지우는 것만으로 경계 위반 1건이 공짜로 사라진다.**

---

## 6. 여러 모듈을 한 트랜잭션에서 건드리는 곳

MSA로 쪼개면 **이 목록이 곧 "분산 트랜잭션 문제가 생길 자리"** 다. (commerce-msa 2단계에서 `OrderService.createOrder`가 재고 차감(원격) + 주문 저장(로컬)을 하던 것과 같은 구조가, 여기서는 이미 모놀리식 안에 여러 개 있다.)

| # | 메서드 | 한 트랜잭션 안에서 건드리는 것 | 잠금 | 난이도 |
|---|---|---|---|---|
| T1 | `SwitchService.executeSwitch` | trigger(`decisions` 조회, `switch_logs` INSERT) + place(`place_candidates` 2건 검증) + plan(`category_states` **비관적 락 후 UPDATE**) + after-commit 이벤트(stats) | `PESSIMISTIC_WRITE` on `category_states` | ★★★ |
| T2 | `PlaceCandidateService.createCandidate` | plan(`plan_categories` 검증) → place(`places` **REQUIRES_NEW** 별도 커밋) → place(`place_candidates` INSERT) → plan(`category_states` 락·UPSERT, `category_selection_logs` INSERT) | `PESSIMISTIC_WRITE` | ★★★ |
| T3 | `PlanCategoryService.updateCategoryType` | plan(`plans` 락, `plan_categories` 락·UPDATE) + place(`place_candidates` 락·soft delete 일괄) + plan(`category_states` 리셋) | 락 3종 | ★★★ |
| T4 | `TriggerService.createTrigger` | plan(`plan_categories`, `category_states`) + place(후보 생존 확인) + trigger(`triggers` INSERT) + after-commit 이벤트(stats) | – | ★★ |
| T5 | `PlaceCandidateService.setRepresentative` / `deleteCandidate` | place(`place_candidates`) + plan(`category_states` 락, `category_selection_logs`) | `PESSIMISTIC_WRITE` | ★★ |
| T6 | `PlanService.getPlanDetail` / `getSharedPlanDetail` (읽기) | user(`users`+`user_profiles`) + plan + place(후보+장소) + plan(`category_states`) | – | ★★ (API Composition) |
| T7 | `AuthService.signup`, `Kakao/NaverAuthTransactionHandler` | user(`users`, `user_profiles`) INSERT + **Redis에 refresh token 저장(트랜잭션 밖 자원)** | – | ★ (identity를 한 서비스로 두면 내부 문제) |
| T8 | `UserService.updateProfileImage` | DB(`user_profiles` UPDATE) 트랜잭션 **안에서 S3 업로드·삭제(외부 I/O)** | – | ★ (커밋 실패 시 S3 고아 객체) |
| T9 | `PlanStatsEventListener` (`@Async` + `AFTER_COMMIT`) | trigger 커밋 **후** 별도 스레드·별도 트랜잭션에서 `plan_stats` 재계산 | `PESSIMISTIC_WRITE` on `plan_stats` | ★ — 이미 "최종 일관성"이지만 **유실 가능** |

**T9가 특히 중요하다.** 이건 **commerce-msa 4단계에서 Outbox로 해결한 문제의 모놀리식 버전**이다.
`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`는 메모리 큐다. 커밋 직후 JVM이 죽으면 이벤트는 사라지고 `plan_stats`는 영원히 틀린다(재계산 배치도 없다).
여기선 "같은 DB 안이라서 아직 안 아팠을 뿐"이고, stats를 떼어내는 순간 Kafka + Outbox가 **필요해지는 자리**가 바로 이곳이다.

**대표 흐름 — T1 전환(Switch) 시퀀스 (현재)**

```
Client          SwitchController   SwitchService              DB (단일 MySQL)
  │ POST /triggers/{decisionId}/switch │                          │
  │──────────────▶│────────────────▶│  BEGIN                    │
  │               │                 │── decisions⋈triggers⋈plan_categories⋈plans (fetch) ─▶│  [trigger+plan]
  │               │                 │── switch_logs exists? ───────────────────────────────▶│  [trigger]
  │               │                 │── place_candidates (to) alive & same category ──────▶│  [place]
  │               │                 │── category_states FOR UPDATE ────────────────────────▶│  [plan] 🔒
  │               │                 │── place_candidates (from) alive ─────────────────────▶│  [place]
  │               │                 │── UPDATE category_states.current_candidate_id ───────▶│  [plan]
  │               │                 │── INSERT switch_logs (FK→place_candidates×2) ────────▶│  [trigger]
  │               │                 │  publishEvent(SwitchLogCreated) → 메모리 보관
  │               │                 │  COMMIT  ← 여기까지 전부 원자적 (ACID가 공짜)
  │               │                 │  └─(커밋 후, 다른 스레드) PlanStatsUpdater.recalculate × 3 periods  [stats]
  │◀──────────────│◀────────────────│
```
→ 서비스를 plan / place / trigger로 나누면 이 한 줄짜리 COMMIT이 **세 서비스에 걸친 Saga**가 된다. "그럴 가치가 있나?"가 §8의 질문이다.

---

## 7. 분석 중 발견한 리스크 (경계 외)

경계 문제는 아니지만 **안전망(0단계)과 이후 작업에 직접 영향**을 주는 것들.

| # | 발견 | 근거 | 영향 |
|---|---|---|---|
| R1 | **인가(소유자 확인) 누락 — IDOR.** 로그인만 하면 **남의 플랜**의 후보 등록/대표 변경/삭제, 트리거 발생, 결정, 전환이 가능 | `PlaceCandidateController`, `TriggerController`, `DecisionController`, `SwitchController`에 `@CurrentUser` 없음 + 서비스에 owner 체크 없음 (plan·recommendation·stats는 체크함) | 보안 결함. 고치려면 trigger/place가 "이 플랜이 이 사용자 것인가?"를 plan에 물어야 함 → **첫 번째 모듈 공개 API의 자연스러운 후보** |
| R2 | **현재 로컬 컨텍스트 기동 불가** | `./gradlew test` → 100개 중 99 통과, `BReadyApplicationTests.contextLoads` 실패: `Could not resolve placeholder 'cloud.aws.credentials.access-key'` | `application.yml`이 gitignore되어 레포에 없고, test 프로필엔 S3 설정이 없다. **"진짜 키 없이 로컬에서 돈다"가 아직 성립 안 함** → 0단계 첫 작업 |
| R3 | `gradlew` 실행 권한 없음 | `git ls-files -s gradlew` → `100644` | `./gradlew` 실패(`permission denied`), `sh ./gradlew`로 우회 중. 사용자가 `git update-index --chmod=+x gradlew` 필요 |
| R4 | 죽은 코드 | `PlaceService`(빈 클래스), `PlaceController`(빈 컨트롤러), `PlanStatsService`(TODO 스텁, 테스트에서 mock만 됨), `PlanStatsJoinService`(미사용) | 경계 지도 노이즈. 특히 `PlanStatsJoinService`는 경계 위반 native query를 끌고 있음 |
| R5 | 이벤트 유실 가능 | T9 참고 | stats 정합성 |
| R7 | **전환 검증이 락보다 먼저 실행됨 (경쟁 상태)** | `SwitchService.executeSwitch`는 `toCandidate` 생존 검증(락 없음)을 한 **뒤에** `category_states FOR UPDATE`를 잡는다. 그 사이 `deleteCandidate`가 같은 락을 잡고 그 후보를 지우고 커밋하면, 전환은 락을 얻은 뒤 다시 확인하지 않고 **삭제된 후보를 대표로 지정**한다 | 모놀리식 안에서도 이미 생기는 버그. 안전망에 동시성 테스트로 재현할 후보. "검증과 변경은 같은 락 안에서" 원칙의 근거 |
| R6 | 소프트 삭제 일관성 | `Plan.softDelete()`는 하위 카테고리/후보/트리거를 지우지 않음. stats 쿼리 중 일부만 `p.deletedAt is null` 필터 (예: `TriggerRepository.countByOwnerIdAndPeriod`는 필터 없음) | 삭제한 플랜의 트리거가 통계에 섞임. 안전망 테스트에서 "현재 동작"으로 기록할지 결정 필요 |

---

## 8. 의견 — 패키지 ≠ 바운디드 컨텍스트

현재 패키지 경계를 그대로 서비스 경계로 삼으면 안 된다. 근거:

1. **함께 바뀌는 것은 함께 둔다.** `PlanCategory`의 타입을 바꾸면 후보가 지워지고(T3), 후보를 넣으면 대표 상태가 바뀌고(T2), 전환하면 대표 상태가 바뀐다(T1). `category_states`를 **세 패키지의 서비스가 비관적 락을 잡고 직접 수정**한다. 이건 하나의 애그리거트(플랜 → 카테고리 → 후보 → 대표 상태)가 세 패키지에 찢어져 있다는 신호다.
2. **`PlaceCandidate`는 plan 쪽 개념이다.** 카탈로그(`Place`)와 후보(`PlaceCandidate`)는 생명주기가 다르다. 카탈로그는 외부 ID 기준 공용 캐시이고, 후보는 특정 플랜 카테고리에 종속된다.
3. **trigger는 플래닝의 "변경 이력 + 의사결정 기록"에 가깝다.** 전환(Switch)의 본질은 "대표 후보 변경"(plan의 상태 변경)이고, `SwitchLog`는 그 이력이다.

**잠정 바운디드 컨텍스트 (1단계에서 확정할 초안)**

| 잠정 컨텍스트 | 포함 (현재 패키지) | 소유 테이블 | 성격 |
|---|---|---|---|
| **Identity** | auth + user | `users`, `user_profiles`, Redis refresh token | 인증·프로필. JWT 쟁점은 6단계 |
| **Planning (코어)** | plan + place의 *후보* 부분 (+ trigger를 여기 둘지가 핵심 결정) | `plans`, `plan_categories`, `place_candidates`, `category_states`, `category_selection_logs` (+ `triggers`, `decisions`, `switch_logs`?) | 강한 일관성이 필요한 핵심 도메인 |
| **Place Catalog** | place의 *검색·카탈로그* 부분 | `places` | 카카오 어댑터 + 장소 마스터 |
| **Recommendation** | recommendation | 없음 (Redis 캐시) | 계산형, 외부(OpenAI) 의존 큼 |
| **Stats** | stats | `plan_stats` (+ 앞으로 자기 읽기 모델) | 읽기 모델, 최종 일관성 허용 |
| **File** | s3 | 없음 | 기술 역량 (서비스 분리 대상 아님, 라이브러리/어댑터) |

---

## 9. 의견 — 먼저 떼어내기 좋은 후보 vs 가장 어려운 곳

### 먼저 떼어내기 좋은 후보: **Place Catalog** (장소 검색 + `places`)

| 기준 | Place Catalog | Recommendation | Stats |
|---|---|---|---|
| 자기 테이블 | `places` 1개 | 없음 | `plan_stats` 1개 |
| 들어오는 의존(남이 나를 씀) | `PlaceCandidate.place` FK 1개, 플랜 상세 fetch join, recommendation이 **Service로** 호출 | 없음 | 없음 (이벤트만 받음) |
| 나가는 의존(내가 남을 씀) | **없음** (카카오만) | trigger·plan·place Repository 4개 | trigger·plan Repository 5개 |
| 4단계 학습 목표(공유 DB→스키마 분리→DB 분리, **데이터 이관**) 충족 | ✅ `places` 이관 + FK 끊기 | ❌ 이관할 데이터 없음 | △ 가능하지만 읽기 모델 재구축이 사실상 Kafka(5단계) 선행 |

- **나가는 의존이 0** 이다. 떼어낸 서비스가 모놀리식을 호출할 일이 없다 → 첫 추출에서 "양방향 호출 지옥"을 피한다.
- 끊어야 할 선이 **명확하고 적다**: `PlaceCandidate.place` FK와 플랜 상세의 `left join fetch cand.place`.
  → "후보에 장소 스냅샷(이름/주소/좌표)을 복사해 둘까, 상세 조회 때 Catalog API를 부를까?" 라는 **진짜 MSA 설계 질문**을 첫 추출에서 마주한다.
- 외부 API(카카오) 어댑터가 이미 있어 **타임아웃·장애 전파**를 바로 관찰할 수 있다.
- 차선: **Recommendation**. 테이블이 없어 추출 자체는 쉽지만, 모놀리식에 "트리거/대표후보 조회용 내부 API"를 먼저 열어야 하고 데이터 이관 학습이 빠진다. OpenAI 지연 때문에 **서킷브레이커가 진짜 필요해지는 곳**이라 2번째 추출로 좋다.
- Stats는 이미 이벤트 기반이라 매력적이지만, 지금 통계 API 대부분이 trigger 테이블을 직접 JOIN 한다. 떼려면 **자기 읽기 모델을 이벤트로 재구축**해야 하고, 그 순간 T9의 유실 문제가 터진다 → Kafka + Outbox가 도입되는 5단계에 맞다.

### 가장 어려울 곳: **Planning ↔ Place 후보 ↔ Trigger 삼각형** (T1·T2·T3)

- `category_states` 한 행을 놓고 세 패키지가 **비관적 락으로 직렬화**한다. 서비스를 나누면 DB 락이 서비스 경계를 넘을 수 없으므로 동시성 보장 방식부터 다시 설계해야 한다(낙관적 락 + 재시도, 또는 소유 서비스 하나로 몰기).
- 엔티티 양방향(C1) + fetch join(상세 조회) + 락이 동시에 얽혀 있다.
- **내 의견**: 이 셋은 **처음부터 쪼개지 않는 것이 정답일 가능성이 높다.** 2단계(모듈러 모놀리스)에서 경계를 다시 그어 *Planning 컨텍스트 하나*로 묶고, MSA로 갈 때도 한 서비스로 두는 안을 1순위로 검토하자. "모든 패키지를 서비스로 만드는 것"은 목표가 아니다. 이걸 데이터로 설명할 수 있으면 면접에서 강한 답이 된다.

---

## 10. 1단계(경계 지도 확정)에서 결정할 질문

- [ ] Q1. trigger(Trigger/Decision/SwitchLog)를 Planning 컨텍스트에 합칠 것인가, 별도 컨텍스트로 둘 것인가?
- [ ] Q2. `PlaceCandidate`를 plan 쪽으로 옮길 것인가? (2단계에서 패키지 이동 = 코드 변경)
- [ ] Q3. auth와 user를 하나의 Identity로 볼 것인가? (6단계 JWT와 연결)
- [ ] Q4. R1(IDOR)을 언제 고칠 것인가? (안전망 직후 별도 조각 / 2단계 공개 API 도입과 함께)
- [ ] Q5. R6(소프트 삭제 불일치)을 안전망 테스트에서 "현재 동작"으로 고정할 것인가, 버그로 기록할 것인가?

---

## 변경 이력

| 날짜 | 버전 | 내용 |
|---|---|---|
| 2026-10-08 | v0 | 최초 작성 (코드 수정 없이 분석) |