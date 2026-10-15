# 경계 지도 (Context Map)

> **상태**: 확정 (2026-10-12). 결정과 근거는 [ADR-0001](../adr/0001-bounded-contexts-and-extraction-order.md)
> **2026-10-12 이후**: 장소 후보(`PlaceCandidate`와 그 리포지토리·서비스·컨트롤러·DTO)를 `plan` 패키지로 옮겼다. 아래 1~6번은 분석 시점의 패키지 기준 기록이라 후보가 `place`에 있는 것으로 나온다. 
> **2026-10-13 이후**: Planning은 Place Catalog와 Identity를 공개 API(`place.api`, `user.api`)로만 쓴다. `PlaceCandidate.place` 연관은 `placeId`가 됐고 플랜 상세는 `places`를 JOIN하지 않는다. 아래 3~6번의 해당 행에 표시했다.
> **분석 방법**: `com.bready.server.*` 전체 import 그래프 추출 → 엔티티 연관관계·JPQL/Native 쿼리·`@Transactional` 메서드 전수 확인
> **이 문서의 용도**: 로드맵 1단계(경계 지도)의 출발점. 2단계(모듈러 모놀리스)에서 "무엇을 끊어야 하는지"의 체크리스트
> 위반을 하나 끊을 때마다 이 문서의 해당 행을 갱신

---

## 용어

이 문서와 ADR에서 쓰는 이름이다. 비슷해서 헷갈리기 쉬운 것을 먼저 적는다.

| 용어 | 뜻 | 코드에서 |
|---|---|---|
| **Place Catalog** (장소 카탈로그) | 장소 자체에 대한 정보 모음. 이름·주소·좌표처럼 누구의 플랜과도 무관한 공용 데이터와 카카오 장소 검색 | `place` 패키지, `places` 테이블 |
| **카테고리** (PlanCategory) | 플랜 안의 한 단계. 식사, 카페, 산책 같은 것. Place Catalog와는 다른 말이다 | `plan` 패키지, `plan_categories` 테이블 |
| **장소 후보** (PlaceCandidate) | 어떤 카테고리에 올려 둔 장소. "이 플랜의 카페 후보 중 하나" | `plan` 패키지, `place_candidates` 테이블 |
| **Planning** | 플랜, 카테고리, 장소 후보, 대표 후보 상태, 트리거·결정·전환 | `plan`, `trigger` 패키지 |
| **Identity** | 가입, 로그인, 토큰, 프로필 | `auth`, `user` 패키지 |

장소 후보는 장소를 가리킨다. 후보는 Planning의 것이고 장소는 Place Catalog의 것이다.

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
| C2 | ~~`trigger ⇄ stats`~~ **해결(2026-10-13)** | trigger가 `stats.event.*`를 발행, stats가 `trigger.repository.*`를 조회하던 순환. 이벤트를 `trigger.api.event`로 옮기고 조회를 공개 API로 바꿔 stats → Planning 한 방향이 됐다 |
| C3 | `global ⇄ 도메인` (일부 해결 2026-10-13) | `GlobalExceptionHandler`가 `S3ErrorCase`·`StatsErrorCase`를 import하던 것은 끊었다. `AuthUtils`·`JwtTokenProvider`가 `AuthErrorCase`를 import하는 6건은 6단계 인증 분리에서 푼다 |

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
| plan | ~~`UserRepository.findByIdWithProfile`~~ **해결(2026-10-13)**: `user.api.UserApi.findUserSummary` | `PlanService.buildPlanDto` (작성자 닉네임/이미지) |
| plan | `PlaceCandidateRepository.findAllAliveByCategoryIdForUpdate` | `PlanCategoryService.updateCategoryType` |
| place | `PlanCategoryRepository`, `CategoryStateRepository`, `CategorySelectionLogRepository` | `PlaceCandidateService` (3개 메서드 전부) |
| trigger | `PlanCategoryRepository`, `CategoryStateRepository`, `PlaceCandidateRepository` | `TriggerService`, `SwitchService` |
| trigger | `stats.event.TriggerCreatedEvent`, `SwitchLogCreatedEvent` | `TriggerService`, `SwitchService` |
| recommendation | `TriggerRepository`, `PlanCategoryRepository`, `CategoryStateRepository`, `PlaceCandidateRepository` | `PlaceRecommendationService`, `RecommendValidationService` |
| recommendation | `PlaceSearchService` (Service) | `RuleBasedRecommendationAdapter` |
| recommendation | `PlanCategory`(엔티티)가 **port 인터페이스 시그니처**에 노출 | `CategoryRecommendationPort`, `PlaceRecommendationPort` |
| stats | `PlanRepository`, `PlanCategoryRepository`, `TriggerRepository`, `DecisionRepository`, `SwitchLogRepository` | `StatsService`, `TriggerStatsService`, `RecentActivityStatsService`, `PlanActivityStatsService`, `PlanStatsMaterializedService`, `PlanStatsUpdater` |

> 주목: **"남의 Service를 호출"하는 곳은 2곳뿐**(`user→s3`, `recommendation→place`). 나머지 모든 모듈 간 결합은 **Repository 직접 주입**이다.
> 즉 2단계의 핵심 작업은 "남의 Repository 직접 사용 → 그 모듈이 공개한 API(인터페이스) 호출"로 바꾸는 것이다.

---

## 4. 모듈 경계를 넘는 JPA 연관관계

| 엔티티 (소유 패키지) | 필드 | → 대상 (소유 패키지) | 종류 | MSA로 가면 |
|---|---|---|---|---|
| `PlaceCandidate` (place) | `category` | `PlanCategory` (plan) | `@ManyToOne LAZY` | ID 참조 또는 같은 컨텍스트로 이동 |
| `PlanCategory` (plan) | `candidates` | `PlaceCandidate` (place) | `@OneToMany(mappedBy)` | **양방향 → 순환 C1의 원인** |
| `PlaceCandidate` (place) | ~~`place`~~ | `Place` (place) | ~~`@ManyToOne LAZY`~~ **해결(2026-10-13)**: `Long placeId` | 패키지는 같지만 "후보 ↔ 카탈로그"는 다른 개념 (8번) |
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
| `PlaceCandidate.placeId` | `Place.id` (Place Catalog). 2026-10-13에 연관에서 ID로 바꿨다 |
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
| `SwitchLogRepository` (trigger) | `findRecentSwitchActivities`, `countByOwnerIdAndPeriod`, `findRecentSwitchActivitiesAllPlans` | `SwitchLog ⋈ Decision ⋈ Trigger ⋈ Plan` | stats |
| `SwitchLogRepository` (trigger) | `countSwitchByPlanIdAndPeriod` | `SwitchLog ⋈ Decision ⋈ Trigger`, `t.plan.id` (FK 컬럼만 사용, `plans` JOIN 없음) | stats |
| `TriggerRepository` (trigger) | `countByPlanIdAndPeriod` | `t.plan.id` (FK 컬럼만 사용, 실제 JOIN은 없음) | stats |
| `PlanCategoryRepository` (plan) | `findAllDetailByPlanId` | `PlanCategory ⋈ PlaceCandidate` (fetch). `⋈ Place`는 2026-10-13에 제거하고 Catalog 공개 API 조회로 바꿨다 | plan(상세) |
| `PlaceCandidateRepository` (place) | `findByIdWithCategory` | `PlaceCandidate ⋈ PlanCategory` (fetch). 장소까지 가져오던 `findByIdWithCategoryAndPlace`, `findAliveByIdWithCategoryAndPlace`는 2026-10-13에 삭제 | place |
| `PlaceCandidateRepository` | `existsAliveByIdAndCategoryId`, `findAliveByIdAndCategoryId`, `findAllAliveByCategoryIdForUpdate` | `pc.category.id` 경로 (FK 컬럼만 사용, 실제 JOIN은 없음) | trigger, plan |

**패턴**: trigger/stats 쿼리의 대부분은 **`ownerId`로 필터하려고 `Plan`까지 JOIN** 한다.
→ 분리 시 대표적 해법은 (a) `triggers`에 `owner_id`를 비정규화, (b) stats가 이벤트로 자기 읽기 모델을 구축. (5단계 주제)

### 5.2 Native SQL

없음. `PlanRepository.findPlanSwitchStatsOptimized`(`plans`에 `triggers`·`decisions`·`switch_logs`를 JOIN하던 통계 쿼리)는 유일한 사용처 `PlanStatsJoinService`가 어떤 컨트롤러에서도 쓰이지 않아 함께 삭제했다(2026-10-12). 성능 비교 실험의 "개선 전" 구현이었고, 커밋 `3c61053` 이전 이력에 남아 있다.

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
| T6 | `PlanService.getPlanDetail` / `getSharedPlanDetail` (읽기) | user(`users`+`user_profiles`) + plan + place(후보+장소) + plan(`category_states`). **2026-10-13**: user와 장소는 공개 API 호출로 바꿨다(한 프로세스 안의 API 합성). 서비스로 떼면 이 두 호출이 네트워크를 탄다 | – | ★★ (API Composition) |
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
→ 서비스를 plan / place / trigger로 나누면 이 한 줄짜리 COMMIT이 **세 서비스에 걸친 Saga**가 된다. "그럴 가치가 있나?"가 8번의 질문이다.

---

## 7. 분석 중 발견한 리스크 (경계 외)

경계 문제는 아니지만 **안전망(0단계)과 이후 작업에 직접 영향**을 주는 것들.

| # | 발견 | 근거 | 영향 |
|---|---|---|---|
| R1 | ~~인가(소유자 확인) 누락 — IDOR~~ **해결(2026-10-12)**: 후보 등록·대표 지정·후보 삭제·트리거·결정·전환 여섯 API가 요청한 사용자를 받아 플랜 소유자인지 확인한다 | 수정 전에는 컨트롤러에 `@CurrentUser`가 없고 서비스에 소유자 확인이 없었다. 다른 사용자의 토큰으로 여섯 API를 호출해 전부 200이 나오는 것을 `PlanningAuthorizationIntegrationTest`로 재현한 뒤 고침 | 남의 플랜이면 403 PLAN_ACCESS_DENIED(4002). URL과 요청 본문은 그대로 |
| R2 | ~~현재 로컬 컨텍스트 기동 불가~~ **해결(0-1, 2026-10-12)**: 키 없는 `application.yml` 커밋, 127개 통과 | `./gradlew test` → 100개 중 99 통과, `BReadyApplicationTests.contextLoads` 실패: `Could not resolve placeholder 'cloud.aws.credentials.access-key'` | `application.yml`이 gitignore되어 레포에 없고, test 프로필엔 S3 설정이 없다. **"진짜 키 없이 로컬에서 돈다"가 아직 성립 안 함** → 0단계 첫 작업 |
| R3 | `gradlew` 실행 권한 없음 | `git ls-files -s gradlew` → `100644` | `./gradlew` 실패(`permission denied`), `sh ./gradlew`로 우회 중. 사용자가 `git update-index --chmod=+x gradlew` 필요 |
| R4 | ~~죽은 코드~~ **해결(2026-10-12)**: `PlaceService`, `PlaceController`, `PlanStatsService`, `PlanStatsJoinService`와 그것만 쓰던 쿼리 3개 삭제 | 참조 검색, 컴파일, 전체 테스트 137개 통과로 확인 | 기준선 stats 109 → 94, 컨트롤러 규칙 17 → 16 |
| R5 | 이벤트 유실 가능 | T9 참고 | stats 정합성 |
| R7 | ~~전환 검증이 락보다 먼저 실행됨 (경쟁 상태)~~ **해결(2026-10-12)**: 락을 먼저 잡고, 그 뒤의 검증이 최신 커밋을 보도록 READ_COMMITTED로 변경 | 수정 전 `SwitchService.executeSwitch`는 `toCandidate` 생존을 락 없이 확인한 뒤 `category_states FOR UPDATE`를 잡았다. 그 사이 삭제가 커밋되면 삭제된 후보가 대표가 됐다. `CandidateDeletionRaceIntegrationTest`로 재현하고 고침 | 락 순서만 바꿔서는 고쳐지지 않았다(REPEATABLE READ의 스냅샷). TROUBLESHOOTING T-005 |
| R9 | ~~대표 지정에도 같은 경쟁 상태~~ **발견·해결(2026-10-12)** | `PlaceCandidateService.setRepresentative`도 후보를 먼저 읽고 락을 잡은 뒤 다시 확인하지 않았다. R7을 고치다가 찾았다 | 같은 테스트 클래스에서 재현하고 같은 방법으로 고침 |
| R6 | 소프트 삭제 일관성 | `Plan.softDelete()`는 하위 카테고리/후보/트리거를 지우지 않음. stats 쿼리 중 일부만 `p.deletedAt is null` 필터 (예: `TriggerRepository.countByOwnerIdAndPeriod`는 필터 없음) | 삭제한 플랜의 트리거가 통계에 섞임. 안전망 테스트에서 "현재 동작"으로 기록할지 결정 필요 |
| R8 | **통계 응답의 `categoryTypes`가 항상 null** | `PlanStatsItem.categoryTypes`를 채우는 코드는 삭제한 `PlanStatsJoinService`에만 있었다. 지금 API가 쓰는 `PlanStatsMaterializedService`는 이 필드를 채우지 않는다 | `GET /api/v1/stats/plans` 응답에 카테고리 정보가 빠진다. API를 통합할 때 누락된 것으로 보인다. Stats를 떼면서 읽기 모델을 다시 만들 때 고친다 |

---

## 8. 의견 — 패키지 ≠ 바운디드 컨텍스트

현재 패키지 경계를 그대로 서비스 경계로 삼으면 안 된다. 근거:

1. **함께 바뀌는 것은 함께 둔다.** `PlanCategory`의 타입을 바꾸면 후보가 지워지고(T3), 후보를 넣으면 대표 상태가 바뀌고(T2), 전환하면 대표 상태가 바뀐다(T1). `category_states`를 **세 패키지의 서비스가 비관적 락을 잡고 직접 수정**한다. 이건 하나의 애그리거트(플랜 → 카테고리 → 후보 → 대표 상태)가 세 패키지에 찢어져 있다는 신호다.
2. **`PlaceCandidate`는 plan 쪽 개념이다.** 카탈로그(`Place`)와 후보(`PlaceCandidate`)는 생명주기가 다르다. 카탈로그는 외부 ID 기준 공용 캐시이고, 후보는 특정 플랜 카테고리에 종속된다.
3. **trigger는 플래닝의 "변경 이력 + 의사결정 기록"에 가깝다.** 전환(Switch)의 본질은 "대표 후보 변경"(plan의 상태 변경)이고, `SwitchLog`는 그 이력이다.

**바운디드 컨텍스트 (확정 2026-10-12)**

| 컨텍스트 | 포함 (현재 패키지) | 소유 테이블 | 성격 |
|---|---|---|---|
| **Identity** | auth + user | `users`, `user_profiles`, Redis refresh token | 인증·프로필. JWT 쟁점은 6단계 |
| **Planning (코어)** | plan + place의 *후보* 부분 + trigger | `plans`, `plan_categories`, `place_candidates`, `category_states`, `category_selection_logs`, `triggers`, `decisions`, `switch_logs` | 강한 일관성이 필요한 핵심 도메인. 한 트랜잭션과 한 락으로 함께 바뀐다 |
| **Place Catalog** | place의 *검색·카탈로그* 부분 | `places` | 카카오 어댑터 + 장소 마스터 |
| **Recommendation** | recommendation | 없음 (Redis 캐시) | 계산형, 외부(OpenAI) 의존 큼 |
| **Stats** | stats | `plan_stats` (+ 앞으로 자기 읽기 모델) | 읽기 모델, 최종 일관성 허용 |
| **File** | s3 | 없음 | 기술 역량 (서비스 분리 대상 아님, 라이브러리/어댑터) |

**확정한 세 가지와 근거**

| 결정 | 근거 |
|---|---|
| trigger를 Planning에 합친다 | 전환의 본질은 대표 후보 변경이다. `SwitchService.executeSwitch`가 잠그고 바꾸는 `category_states`는 plan 소유다. 나누면 한 번의 COMMIT이 두 서비스에 걸친 트랜잭션이 되고, DB 락은 서비스 경계를 넘지 못한다(T1) |
| `PlaceCandidate`는 Planning, `Place`만 Place Catalog | 후보의 생명주기는 카테고리에 묶여 있다. 카테고리 타입을 바꾸면 후보가 전부 지워진다(T3). 카탈로그는 누구의 플랜과도 무관한 공용 데이터다 |
| auth와 user는 하나의 Identity | 가입이 `users` 쓰기와 토큰 발급을 한 번에 한다(T7). 지금 인증만 따로 확장할 이유가 없다 |

**경계를 이렇게 그으면 달라지는 것**: 기준선 435건 중 약 189건은 같은 컨텍스트 안의 참조가 된다(11번 "컨텍스트 기준으로 다시 보기").

---

## 9. 추출 순서 — 먼저 떼어내기 좋은 후보 vs 가장 어려운 곳

**확정(2026-10-12): Place Catalog → Recommendation → Stats.** Planning과 Identity는 모놀리식에 남긴다. 아래는 그 근거다.

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

## 10. 결정한 질문

- [x] Q1. trigger(Trigger/Decision/SwitchLog)를 Planning 컨텍스트에 합칠 것인가, 별도 컨텍스트로 둘 것인가? → **합친다** (8번)
- [x] Q2. `PlaceCandidate`를 plan 쪽으로 옮길 것인가? → **Planning 소유로 본다.** 패키지 이동은 2단계에서 한다
- [x] Q3. auth와 user를 하나의 Identity로 볼 것인가? → **하나로 본다**
- [x] Q4. R1(IDOR)을 언제 고칠 것인가? → **모듈러 모놀리스 단계에서 Planning 진입점을 정리할 때 함께 고친다.** trigger와 후보가 Planning 안에 있으므로 같은 컨텍스트 안의 소유권 검증이 된다
- [x] Q5. R6(소프트 삭제 불일치)을 어떻게 다룰 것인가? → **버그로 기록하고 Stats를 떼면서 읽기 모델을 새로 만들 때 고친다.** 그 쿼리들은 그때 전부 다시 쓴다

---

## 11. 경계 위반 기준선 (ArchUnit)

위 분석을 테스트로 고정했다(`monolith/src/test/java/com/bready/server/architecture/`). 아래 위반은 기준선(`monolith/src/test/resources/archunit_store/`)에 기록되어 통과하고, **기준선에 없는 새 위반은 빌드가 실패한다.** 위반을 고치면 테스트가 기준선에서 그 줄을 지운다. 이 표의 숫자가 줄어드는 것이 MSA 전환의 진척도다.

숫자는 위반 "건수"다. 같은 클래스 쌍이라도 필드·생성자·메서드 호출마다 한 건씩 센다.

**모듈 경계 — 다른 모듈의 Repository·Entity 직접 사용**

| 모듈 | 2026-10-08 | 2026-10-12 |
|---|---|---|
| recommendation | 131 | 131 |
| stats | 109 | 94 |
| auth | 67 | 67 |
| plan | 58 | 58 |
| trigger | 38 | 38 |
| place | 32 | 32 |
| user | 0 | 0 |
| s3 | 0 | 0 |
| 합계 | 435 | 420 |

**컨텍스트 기준으로 다시 보기 (2026-10-12)**

위 표는 패키지를 모듈로 보고 센 것이다. 8번에서 확정한 컨텍스트로 묶으면 같은 435건이 이렇게 나뉜다.

| 누가 → 누구를 | 건수 | 컨텍스트로 보면 |
|---|---|---|
| auth → user | 67 | Identity 내부 |
| plan → place | 52 | 대부분 Planning 내부(후보). `Place`를 쓰는 부분만 Catalog 경계 |
| place → plan | 32 | Planning 내부 |
| trigger → plan | 27 | Planning 내부 |
| trigger → place | 11 | Planning 내부 |
| recommendation → trigger | 68 | 경계 위반 |
| recommendation → plan | 52 | 경계 위반 |
| recommendation → place | 11 | 경계 위반 |
| stats → trigger | 73 | 경계 위반 |
| stats → plan | 36 | 경계 위반 |
| plan → user | 6 | 경계 위반 |

컨텍스트 내부 참조가 약 189건(43%), 실제로 끊어야 하는 경계 위반이 약 246건이다. (이 표는 경계를 확정한 시점의 집계다. 그 뒤 죽은 코드 삭제로 stats → plan이 15건 줄어 실제 경계 위반은 약 231건이 됐다.) ArchUnit 규칙의 모듈 정의를 컨텍스트 기준으로 바꾸는 일은 2단계에서 패키지를 옮길 때 함께 한다.

**컨텍스트 기준 기준선**

장소 후보를 `plan` 패키지로 옮기고 ArchUnit의 경계를 컨텍스트 단위로 바꾼 뒤 다시 기록한 기준선이다. 

| 컨텍스트 | 포함 패키지 | 위반 | 무엇을 넘나 |
|---|---|---|---|
| Identity | auth, user | 0 | |
| Planning | plan, trigger | 49 → **0** (2026-10-13) | Place Catalog의 `PlaceCategoryType`·`Place`, Identity의 `UserRepository`·`User`·`UserProfile`. 공개 API로 바꿔 전부 끊었다 |
| Place Catalog | place | 0 | |
| Recommendation | recommendation | 131 → 105 → 65 → **0** (2026-10-13) | Planning. Place Catalog의 enum과 `Place`는 더 쓰지 않는다 |
| Stats | stats | 94 → 63 → **0** (2026-10-13) | Planning. `TriggerType`·`DecisionType`은 공개 패키지(`trigger.api`)로 옮겨 더 세지 않는다 |
| File | s3 | 0 | |
| 합계 | | 274 → 199 → 128 → 65 → **0** (2026-10-13) | 패키지 기준 420건에서 146건이 컨텍스트 내부 참조로 빠졌다 |

추정보다 많은 이유: `plan → place` 52건 대부분이 후보가 아니라 `PlaceCategoryType` enum을 쓰는 것이었다. 카테고리 타입은 플랜의 카테고리가 가지는 값인데 enum이 `place.domain`에 있었다. 2026-10-13에 Catalog 소유로 두고 공개 API 패키지(`place.api`)로 옮겼다. Planning으로 옮기면 Catalog의 검색이 Planning을 import하게 되어 "나가는 의존 0"이 깨지기 때문이다.

**공개 API 사용 (2026-10-13 추가)**

공개 API(`api` 패키지)를 연 컨텍스트를 다른 컨텍스트가 `api` 패키지 밖으로 쓰는 건수다. 위 표가 Repository·Entity만 본다면 이 표는 서비스·DTO·ErrorCase까지 본다.

| 공개 API를 연 컨텍스트 | 위반 | 누가 무엇을 |
|---|---|---|
| Place Catalog (`place.api`) | 17 → **0** (2026-10-13) | Recommendation이 `PlaceSearchService`·`PlaceSearchResponse`·`PlaceErrorCase`를 쓰던 것을 `PlaceSearchApi`로 바꿨다 |
| Identity (`user.api`) | 0 | |
| Planning (`plan.api`, `trigger.api`) | 0 (2026-10-13 규칙 추가) | |

**구조·코딩 규칙**

| 규칙 | 2026-10-08 |
|---|---|
| `new ApplicationException(...)` (from 사용) | 88 (2026-10-12: 80, 2026-10-13: 71, 2026-10-15: 70) |
| DTO가 `*Request`/`*Response` record가 아님 | 41 (2026-10-15: 40) |
| 컨트롤러가 `{Domain}Api`를 구현하지 않음 | 17 (2026-10-12: 16) |
| `@Transactional` 서비스의 클래스 readOnly 선언 없음 | 14 (2026-10-13: 12) |
| domain·service의 `LocalDateTime.now()` (Clock 미사용) | 11 |
| domain의 `IllegalArgumentException`·`IllegalStateException` | 10 |
| global이 도메인 모듈에 의존 (C3) | 8 (2026-10-13: 6, 전부 인증) |
| ErrorCase 코드 중복 | 8 (2026-10-15: 4. 카카오 검색 4104·4105가 Catalog로 나감) |
| 엔티티 public 기본 생성자 | 6 |
| 필드 주입(`@Value` 필드 포함) | 5 |
| 다른 모듈의 이벤트 클래스 생성 (C2) | 2 (2026-10-13: 0) |
| 엔티티 public setter | 2 |
| 레이어 방향, 표준 출력, private `@Transactional`, enum ORDINAL | 0 |

---

## 변경 이력

| 날짜 | 버전 | 내용 |
|---|---|---|
| 2026-10-08 | v0 | 최초 작성 (코드 수정 없이 분석) |
| 2026-10-08 | v0.1 | 경계 위반 기준선(ArchUnit) 추가 |
| 2026-10-15 | v1.10 | 4-1 카카오 장소 검색을 place-catalog-service로 이동. 모놀리식은 `PlaceSearchApi`를 HTTP 어댑터로 구현. 코딩 규칙 기준선 71→70, 41→40, 8→4 |
| 2026-10-13 | v1.9 | 공통 예외 핸들러의 도메인 의존 제거. global → 도메인 8 → 6 (남은 것은 인증, 6단계) |
| 2026-10-13 | v1.8 | Recommendation이 Planning·Place Catalog를 공개 API로만 쓰게 변경. Recommendation 65 → 0, Catalog `api` 밖 사용 17 → 0. 컨텍스트 사이의 Repository·Entity 직접 사용이 0이 됐다 |
| 2026-10-13 | v1.7 | Stats가 Planning을 공개 API(`plan.api`, `trigger.api`)로만 쓰게 변경, 이벤트를 `trigger.api.event`로 이동. Stats 63 → 0, C2 해결, 합계 65 |
| 2026-10-13 | v1.6 | `TriggerType`·`DecisionType`을 `trigger.api`로 이동. Recommendation 105 → 65, Stats 94 → 63, 합계 128 |
| 2026-10-13 | v1.5 | Planning이 Place Catalog·Identity를 공개 API로만 쓰게 변경. Planning 기준선 49 → 0, 합계 274 → 199. 공개 API 사용 표 추가 |
| 2026-10-12 | v1.4 | R1(인가 누락) 해결 |
| 2026-10-12 | v1.3 | R7 해결, R9(대표 지정의 같은 경쟁 상태) 발견·해결, 용어 절 추가 |
| 2026-10-12 | v1.2 | 장소 후보를 plan 패키지로 이동, 기준선을 컨텍스트 단위로 다시 기록(11번): 420 → 274 |
| 2026-10-12 | v1.1 | 죽은 코드 삭제 반영: 5.2 native SQL 제거, R4 해결, R8 추가, 기준선 stats 109 → 94 |
| 2026-10-12 | v1.0 | 추출 순서 확정(9번), 남은 결정 질문 정리(10번), ADR-0001 연결 |
| 2026-10-12 | v0.3 | 바운디드 컨텍스트 경계 확정(8번, Q1~Q3), 기준선을 컨텍스트 기준으로 다시 집계(11번) |
| 2026-10-08 | v0.2 | 5.1 정정: `countSwitchByPlanIdAndPeriod`는 `plans`를 JOIN하지 않음, `TriggerRepository.countByPlanIdAndPeriod` 누락 추가 (boundary-auditor 감사로 발견) |
