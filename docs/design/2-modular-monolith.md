# 2단계 모듈러 모놀리스

> 진행 중

## 이 단계의 목표

서비스를 떼어내기 전에, 한 프로세스 안에서 먼저 경계를 지키는 구조로 만든다. 컨텍스트 안의 참조는 그대로 두고, 컨텍스트를 넘는 참조(다른 컨텍스트의 리포지토리 주입, 엔티티 사용, 테이블 JOIN)를 공개 API 호출로 바꾼다. 진척은 ArchUnit 기준선의 건수로 본다.

| 조각 | 내용 | 상태 |
|---|---|---|
| 2-1 | 죽은 코드 제거 | 완료 |
| 2-2 | 컨텍스트 기준으로 모듈 재정의 | 완료 |
| 2-3 | 전환 경쟁 상태 수정 | 완료 |
| 2-4 | 소유권 검증 추가 | 완료 |
| 2-5 | Planning → Place Catalog 공개 API | 완료 |
| 2-6 | Stats·Recommendation → Planning 공개 API, 이벤트 소유권, global의 도메인 의존 | 진행 중 (가·나 완료) |

## 2-1 죽은 코드 제거

### 왜 지금

쓰이지 않는 코드가 경계 작업의 대상처럼 보인다. `PlanStatsJoinService` 하나가 위반 기준선에서 15줄(stats → plan)을 차지하고 있었다. 지우기만 해도 끊을 대상이 줄어든다. 0단계의 통합 테스트가 생겨 이제 안전하게 지울 수 있다.

### 죽은 코드라고 판단한 근거

네 가지를 확인

1. 직접 참조: 클래스 이름을 main과 test 전체에서 검색
2. 이름으로 부르는 곳: 설정 파일과 부하 테스트 스크립트
3. 스프링이 스스로 쓰는가: `@Service`는 주입받는 곳이 없으면 호출되지 않는다. `@RestController`는 주입이 없어도 스프링이 URL을 연결하므로 매핑 메서드가 있는지를 따로 본다.
4. 연쇄: 그 클래스만 쓰던 쿼리와 타입

| 대상 | 내용 | 참조 |
|---|---|---|
| `PlaceService` | 빈 클래스 | 없음 |
| `PlaceController` | 매핑 메서드가 없는 컨트롤러 | 없음. 연결된 URL이 없어 API가 바뀌지 않는다 |
| `PlanStatsService` | 본문이 빈 메서드 2개 | `SwitchServiceTest`의 쓰이지 않는 `@Mock` 필드 |
| `PlanStatsJoinService` | JOIN으로 통계를 계산하는 구현 | 없음 |
| `PlanRepository.findPlanSwitchStatsOptimized`, `PlanSwitchStatsRow` | native 통계 쿼리 | `PlanStatsJoinService`뿐 |
| `PlanCategoryRepository.findCategoryTypesByPlanIds` | 카테고리 타입 조회 | `PlanStatsJoinService`뿐 |
| `PlanCategoryRepository.findCategoryTypesByOwner`, `PlanCategoryTypeRow` | 카테고리 타입 조회 | 호출하는 곳 없음 |

### 설계 (선택지 · 결정)

`PlanStatsJoinService`는 README의 성능 개선 기록에 나오는 "개선 전" 구현이다. JOIN 방식과 사전 집계 방식을 k6로 비교한 뒤 API를 사전 집계로 통합했고(커밋 `3c61053`), 그때 URL은 사라지고 서비스 클래스만 남았다.

| 안 | 방법 | 얻는 것 | 잃는 것 |
|---|---|---|---|
| **A (채택)** | 전부 지우고, 개선 전 구현이 이력에 있다는 것을 기록한다 | 기준선 15줄 감소, 리포지토리에서 native query 제거 | 비교 실험을 다시 하려면 이력에서 복원해야 한다 |
| B | `PlanStatsJoinService`와 그 쿼리는 남긴다 | 개선 전 코드가 눈에 보인다 | 쓰이지 않는 코드와 위반 15줄이 남는다 |

A를 고른 이유: 남겨 두어도 호출할 URL이 없어 지금도 실험을 재현할 수 없다. `performance-test/k6/scripts/compare.js`가 부르는 `/stats/plans/join`은 이미 없다.

### 흐름 (before → after)

요청 흐름은 바뀌지 않는다. 지운 것은 어떤 요청도 지나가지 않던 코드다.

```
GET /api/v1/stats/plans
  → PlanStatsController → PlanStatsMaterializedService → plan_stats 조회     (그대로)

(아무도 부르지 않음)                                                          (삭제)
  PlanStatsJoinService
    → PlanRepository.findPlanSwitchStatsOptimized
    → PlanCategoryRepository.findCategoryTypesByPlanIds
  PlaceService, PlaceController, PlanStatsService
```

### 구현 파일

| 파일 | 변경 |
|---|---|
| `stats/service/PlanStatsJoinService.java`, `stats/service/PlanStatsService.java`, `place/service/PlaceService.java`, `place/controller/PlaceController.java` | 삭제 |
| `plan/repository/PlanRepository.java` | native query와 프로젝션 삭제 |
| `plan/repository/PlanCategoryRepository.java` | 쿼리 2개와 프로젝션 삭제 |
| `trigger/service/SwitchServiceTest.java` | 쓰이지 않는 `@Mock PlanStatsService` 삭제 |
| `src/test/resources/archunit_store/` | 테스트가 기준선에서 16줄을 지움 |

9개 파일, 203줄 삭제, 추가 없음.

### 검증 증거

```
.claude/scripts/verify.sh
  통과  format · compile · test (18s)
  테스트: 전체 137 · 실패 0 · 오류 0 · 건너뜀 0

남은 참조 검색: 0건
기준선: stats의 다른 모듈 참조 109 → 94 (15줄), {Domain}Api 미구현 컨트롤러 17 → 16 (1줄)
```

컴파일 통과는 남은 참조가 없다는 증거이고, 통합 테스트 통과는 스프링 컨텍스트가 이 빈들 없이 뜨고 통계 API를 포함한 핵심 흐름이 그대로라는 증거다.

### 알려진 한계

- 삭제하면서 드러난 문제: 통계 응답의 `categoryTypes`를 채우는 코드는 삭제한 `PlanStatsJoinService`에만 있었다. 지금 API가 쓰는 구현은 이 필드를 채우지 않아 항상 null이다. 삭제 전에도 그랬으므로 이번 변경으로 동작이 달라진 것은 아니다. 경계 지도 R8로 기록했고 Stats를 떼면서 고친다.
- k6 스크립트(`performance-test/`)는 이미 없는 URL을 부른다. 이번에는 건드리지 않았다.

## 2-2 컨텍스트 기준으로 모듈 재정의

### 왜 지금

ADR-0001로 경계를 정했지만 코드와 테스트는 그 결정을 모르고 있었다.

- ArchUnit이 패키지를 곧 모듈로 봤다. 같은 Planning 안인 `trigger → plan` 참조까지 위반으로 세어, 기준선 420건 중 무엇이 진짜 끊을 대상인지 테스트로 볼 수 없었다.
- `place` 패키지가 두 컨텍스트에 걸쳐 있었다. 장소 후보는 Planning, 장소와 검색은 Place Catalog인데 한 패키지에 섞여 있었다.

### 설계 (선택지 · 결정)

| 안 | 방법 | 얻는 것 | 잃는 것 |
|---|---|---|---|
| A | 패키지는 두고 ArchUnit에서만 컨텍스트로 묶는다 | 코드 이동 없음 | `place`가 갈리지 않아 클래스 이름 패턴으로 구분해야 한다 |
| **B (채택)** | 후보 관련 클래스만 `place`에서 `plan`으로 옮긴다. `trigger`·`auth`·`user`는 패키지를 유지하고 ArchUnit에서 컨텍스트로 묶는다 | 경계가 틀려 있던 한 곳만 바로잡는다. `place` 패키지가 곧 Place Catalog가 된다 | import를 고칠 파일이 생긴다 |
| C | 패키지 전체를 컨텍스트 이름으로 재배치한다 | 패키지 구조가 ADR과 완전히 일치한다 | 100개 넘는 파일이 움직이고 지금 얻는 것은 이름뿐이다 |

B를 고른 이유: `trigger`와 `plan`, `auth`와 `user`는 한 컨텍스트 안의 하위 모듈로 두어도 틀린 것이 없다. 경계와 어긋난 패키지는 `place` 하나였다.

함께 정한 것:

- URL은 바꾸지 않는다. `/api/v1/places/candidates`는 외부 계약이다.
- 후보가 쓰던 에러 다섯 개를 `PlaceErrorCase`에서 `plan` 쪽 `PlaceCandidateErrorCase`로 옮긴다. 숫자 코드(4106, 4107, 4110, 4111, 4112)와 메시지는 그대로라 응답은 달라지지 않는다.

구현하면서 드러나 추가로 고친 것:

- `PlacePersistenceService.getOrCreate`가 후보 등록 요청 DTO(`PlaceCandidateCreateRequest`)를 그대로 받고 있었다. 둘 다 `place` 패키지에 있을 때는 보이지 않았는데, 후보를 옮기자 Place Catalog의 서비스가 Planning의 DTO에 의존하는 모양이 됐다. Place Catalog를 첫 추출 대상으로 고른 근거가 "나가는 의존이 없다"였으므로 이 방향은 남겨 둘 수 없었다. Catalog가 자기 타입 `PlaceRegisterRequest`를 받도록 바꾸고, 변환은 호출하는 쪽(후보 서비스)이 한다.
- `PlaceSummaryResponse`는 후보 등록 응답에만 쓰여 함께 옮겼다.

### 흐름 (before → after)

요청 흐름과 URL은 그대로다. 클래스가 사는 곳과 의존 방향이 바뀌었다.

```
before
  place/  후보 + 장소 + 검색이 한 패키지
  plan  ⇄  place          서로 참조 (순환)
  PlaceCandidateService ──▶ PlacePersistenceService.getOrCreate(PlaceCandidateCreateRequest)
                            같은 패키지라 문제로 보이지 않음

after
  plan/   Planning: 플랜, 카테고리, 장소 후보
  place/  Place Catalog: 장소, 검색, 카카오 클라이언트
  plan  ──▶  place         한 방향
  PlaceCandidateService ──▶ PlacePersistenceService.getOrCreate(PlaceRegisterRequest)
        (plan)               (place)  Catalog는 자기 타입만 안다

  POST /api/v1/places/candidates   → plan.controller.PlaceCandidateController  (URL 그대로)
```

ArchUnit의 경계 정의:

```
before  패키지 8개 각각이 모듈          after  컨텍스트 6개
  boundaryOf("auth") … ("s3")            identity       = auth, user
                                         planning       = plan, trigger
                                         catalog        = place
                                         recommendation, stats, file(s3)
```

### 구현 파일

| 파일 | 변경 |
|---|---|
| `PlaceCandidate`, `PlaceCandidateRepository`, `PlaceCandidateService`, `PlaceCandidateController`, 후보 DTO 4개, `PlaceSummaryResponse` | `place` → `plan` 패키지 이동 (9개) |
| `PlaceCandidateControllerTest`, `PlaceCandidateServiceTest` | 같은 위치로 이동 |
| `plan/exception/PlaceCandidateErrorCase.java` | 새로 추가. 후보 에러 5개 |
| `place/exception/PlaceErrorCase.java` | 후보 에러 5개 제거 |
| `place/dto/PlaceRegisterRequest.java` | 새로 추가. 장소 등록에 필요한 값 |
| `place/service/PlacePersistenceService.java` | `PlaceRegisterRequest`를 받도록 변경 |
| `recommendation/service/PlaceRecommendationService.java` | 후보 없음 에러를 새 enum으로 |
| `architecture/ArchitectureRules.java`, `ModuleBoundaryTest.java` | 경계를 컨텍스트 단위로 정의 |
| `src/test/resources/archunit_store/` | 패키지 기준 규칙 9개의 기준선 삭제, 컨텍스트 기준 규칙 7개의 기준선 생성. 옮긴 클래스의 기존 코딩 규칙 위반 5건은 패키지 이름만 변경 |

### 검증 증거

```
.claude/scripts/verify.sh
  통과  format · compile · test (19s)
  테스트: 전체 135 · 실패 0     (경계 규칙이 9개에서 7개가 되어 137 → 135)

기준선 생성 허용(allowStoreCreation)은 새 규칙을 기록할 때만 켰고 지금은 false
```

실제 기동 (`docker compose -f docker-compose-local.yml up -d mysql redis` 후 `./gradlew bootRun`):

```
POST   /api/v1/places/candidates         → 200  candidateId 1, place.externalId "kakao-2-2"
POST   /api/v1/places/candidates (중복)   → 409  {"errorCode":4106,"message":"이미 해당 카테고리에 등록된 장소입니다."}
DELETE /api/v1/places/candidates/999999  → 404  {"errorCode":4110,"message":"존재하지 않는 장소 후보 입니다."}
DB     place_candidates 1행, places.external_id = kakao-2-2
```

URL, 상태 코드, 에러 코드가 이동 전과 같다.

컨텍스트 기준 기준선:

| 컨텍스트 | 위반 | 무엇을 넘나 |
|---|---|---|
| Identity (auth, user) | 0 | |
| Planning (plan, trigger) | 49 | Place Catalog의 `PlaceCategoryType`·`Place`, Identity의 사용자 |
| Place Catalog (place) | 0 | |
| Recommendation | 131 | Planning, Place Catalog |
| Stats | 94 | Planning |
| File (s3) | 0 | |
| 합계 | 274 | 패키지 기준 420건에서 146건 감소 |

### 알려진 한계

- 1단계의 추정(실제 경계 위반 약 231건)보다 43건 많다. `plan → place` 참조 대부분이 후보가 아니라 `PlaceCategoryType` enum이었다. 이 enum은 플랜의 카테고리 타입이면서 카카오 카테고리 코드도 들고 있어 두 컨텍스트에 걸쳐 있다. 소유를 어디에 둘지는 2-5에서 정한다.
- `PlaceCandidate.place`(JPA 연관)와 플랜 상세의 fetch join은 아직 Planning에서 Place Catalog의 엔티티를 직접 쓴다. 2-5의 대상이다.
- 패키지를 옮긴 클래스의 기존 위반 기록은 ArchUnit이 "사라진 위반 + 새 위반"으로 본다. 위반 문구에 패키지 이름이 들어가기 때문이다. 같은 위반이 주소만 바뀐 것이라 기준선에서 패키지 이름만 바꿨고, 줄 수가 같다는 것과 diff에 패키지 외의 변경이 없다는 것을 확인했다.
- `ModuleBoundaryTest`라는 클래스 이름은 그대로 뒀다. 안의 규칙은 컨텍스트 단위다.
- 옮긴 테스트(`PlaceCandidateServiceTest`)는 옛 방식(엔티티 mock, `when().thenReturn()`)이 그대로다. 이번에는 이동에 필요한 줄만 고쳤다.

## 2-3 전환 경쟁 상태 수정

### 왜 지금

전환과 후보 삭제가 겹치면 삭제된 후보가 대표로 지정되는 버그가 있었다(경계 지도 R7). ADR-0001에서 trigger를 Planning에 합치기로 한 근거가 "한 DB 안에서 락으로 정합성을 지킬 수 있다"였으므로, 실제로 그렇게 고칠 수 있는지 보여야 했다. 0단계의 통합 테스트 기반이 있어 동시성을 재현할 수 있게 됐다.

### 설계 (선택지 · 결정)

결론을 먼저 정하지 않고, 재현 테스트를 만든 뒤 수정안을 돌려 본 결과로 골랐다.

| 안 | 방법 | 재현 테스트의 전환 응답 | 판정 |
|---|---|---|---|
| 수정 전 | | 200 | 버그 재현 |
| A | 락 조회를 후보 확인보다 위로 옮긴다 | 200 | 고쳐지지 않음 |
| B | A + 후보 확인을 잠금 읽기로 | 400 | 막았지만 에러가 틀림. 후보 행 락이 늘고 락 순서가 엇갈린다 |
| **C (채택)** | A + 격리 수준을 READ COMMITTED로 | 404 | 통과. 락이 늘지 않는다 |

A가 실패한 이유: MySQL 기본 격리 수준(REPEATABLE READ)에서 일반 조회는 트랜잭션이 처음 조회한 시점의 스냅샷을 본다. 락을 얻은 뒤에 확인해도, 락을 기다리는 동안 커밋된 삭제가 보이지 않는다.

C의 약점은 이 메서드만 격리 수준이 다르다는 것이다. 이유를 주석으로 남겼고, 그 설정을 지우면 재현 테스트가 실패한다.

같은 모양의 버그가 대표 지정(`setRepresentative`)에도 있어 함께 고쳤다. 대표 지정은 락을 잡으려면 후보에서 카테고리를 먼저 알아내야 하므로 순서를 바꿀 수 없다. 대신 락을 얻은 뒤 후보가 살아 있는지 다시 확인한다.

### 흐름 (before → after)

```
before: 전환과 삭제가 겹칠 때
  전환                                         삭제
   BEGIN (REPEATABLE READ)
   결정 조회          ← 스냅샷 생성
   to 후보 확인: 살아 있음 (락 없음)
                                               BEGIN
                                               category_states FOR UPDATE
                                               후보 삭제
                                               COMMIT
   category_states FOR UPDATE
   대표 = to          ← 삭제된 후보
   COMMIT

after
  전환                                         삭제
   BEGIN (READ COMMITTED)
   결정 조회
                                               BEGIN
                                               category_states FOR UPDATE
                                               후보 삭제
                                               COMMIT
   category_states FOR UPDATE   ← 삭제가 끝난 뒤 얻는다
   to 후보 확인: 없음             ← 조회 시점의 최신 커밋을 본다
   ROLLBACK → 404 TO_CANDIDATE_NOT_FOUND
```

삭제가 락을 나중에 얻는 반대 순서에서는, 전환이 먼저 끝나 그 후보가 대표가 되고 삭제는 "대표는 삭제할 수 없다"로 거절된다. 어느 순서로도 삭제된 후보가 대표로 남지 않는다.

### 구현 파일

| 파일 | 변경 |
|---|---|
| `trigger/service/SwitchService.java` | 락 조회를 후보 확인 앞으로, `READ_COMMITTED` |
| `plan/service/PlaceCandidateService.java` | `setRepresentative`에 `READ_COMMITTED`, 락을 얻은 뒤 후보 생존 재확인 |
| `integration/CandidateDeletionRaceIntegrationTest.java` | 새로 추가. 전환·대표 지정 각각 삭제와 겹치는 경우 |
| `support/PlanningSteps.java` | 새로 추가. 통합 테스트가 HTTP로 밟는 단계 모음 |
| `plan/service/PlaceCandidateServiceTest.java` | 대표 지정 단위 테스트 2개에 생존 확인 응답 준비 추가 |

### 검증 증거

```
수정 전
  전환 대상 후보가 전환 도중 삭제되면 …        FAILED  expected: 404 but was: 200
  대표로 지정하려는 후보가 지정 도중 삭제되면 …  FAILED  expected: 404 but was: 200

수정 후
  CandidateDeletionRaceIntegrationTest   tests=2 failures=0
  .claude/scripts/verify.sh              전체 137 · 실패 0

확인 실험: 대표 지정에서 재확인 코드는 두고 격리 수준만 되돌림
  대표로 지정하려는 후보가 지정 도중 삭제되면 …  FAILED  expected: 404 but was: 200
```

재현 방법: 테스트가 `category_states` 락을 먼저 쥐고, 삭제 요청과 경쟁 요청을 차례로 보내 둘 다 락을 기다리게 한 뒤(`performance_schema.data_lock_waits`로 확인) 락을 푼다. 순서가 운에 달려 있지 않다.

### 알려진 한계

- B안의 데드락 가능성은 재현하지 않았다. 락 순서(카테고리 타입 변경은 후보 → 상태, B안의 전환은 상태 → 후보)를 보고 판단했다.
- 같은 락을 쓰는 나머지 경로(후보 등록, 카테고리 타입 변경, 트리거 생성)는 이번에 같은 방식으로 검증하지 않았다. 트리거 생성은 대표 후보를 락 없이 읽어 트리거에 기록하므로, 그 순간 대표가 바뀌면 직전 대표가 기록될 수 있다. 데이터가 깨지는 것은 아니라서 이번 범위에 넣지 않았다.
- READ COMMITTED에서는 한 트랜잭션 안의 두 조회가 서로 다른 결과를 볼 수 있다. 두 메서드는 락을 얻은 뒤에만 판단에 쓰는 값을 읽으므로 문제가 되지 않지만, 락 이전에 읽은 값으로 판단하는 코드를 추가하면 같은 버그가 다시 생긴다.
- 재현 테스트는 락 대기 현황을 보려고 root 계정으로 MySQL에 직접 접속한다. Testcontainers MySQL의 root 비밀번호가 앱 계정과 같다는 점에 기댄다.

## 2-4 소유권 검증 추가

### 왜 지금

로그인만 하면 남의 플랜에 후보를 등록하고, 대표를 바꾸고, 후보를 지우고, 트리거·결정·전환을 실행할 수 있었다. 여섯 API의 컨트롤러가 요청한 사용자를 받지 않았고 서비스에 소유자 확인이 없었다. 앞의 통합 테스트들도 이 구멍 때문에 "본인의 흐름만 검증한다"는 한계를 달고 있었다.

ADR-0001에서 trigger와 장소 후보를 Planning에 합쳤으므로, 이 검증은 다른 컨텍스트에 묻는 일이 아니라 같은 컨텍스트 안에서 플랜을 따라가 확인하는 일이 됐다.

### 설계 (선택지 · 결정)

검증 위치:

| 안 | 방법 | 얻는 것 | 잃는 것 |
|---|---|---|---|
| **A (채택)** | `Plan` 엔티티에 `validateOwner(userId)`를 두고 서비스가 부른다 | 규칙이 한 곳에 있고 엔티티만으로 테스트할 수 있다 | 서비스마다 플랜을 따라가 한 줄씩 불러야 한다 |
| B | 조회 쿼리에 소유자 조건을 넣는다 | 코드가 짧다 | 남의 것이면 404가 되어 기존 API(403)와 응답이 갈린다 |
| C | 어노테이션이나 AOP로 공통 처리한다 | 서비스 코드가 깨끗하다 | 플랜을 찾는 경로가 API마다 달라 공통화하기 어렵고 동작이 숨는다 |

남의 플랜일 때의 응답은 기존 플랜 조회와 같은 403 PLAN_ACCESS_DENIED(4002)로 맞췄다.

이미 복사돼 있던 소유자 비교 10곳 가운데 Planning 안의 8곳을 정리했다. 구현하면서 달라진 점이 하나 있다. `PlanService`의 4곳은 같은 에러를 던져서 `validateOwner`로 바꿨지만, `PlanCategoryService`의 4곳은 CATEGORY_ACCESS_DENIED(4201)라는 다른 코드를 던지고 있었다. 이것까지 `validateOwner`로 바꾸면 응답의 에러 코드가 4201에서 4002로 달라진다. 그래서 판단만 하는 `isOwnedBy(userId)`를 따로 두고, 카테고리 쪽은 기존 에러를 그대로 던지게 했다. 추천 서비스의 2곳은 다른 컨텍스트라 2-6에서 다룬다.

소유권 확인은 각 메서드에서 대상을 조회한 직후, 다른 검증보다 먼저 한다. 남의 결정이 KEEP인지 이미 전환됐는지 같은 정보를 에러 메시지로 알려 주지 않기 위해서다.

### 흐름 (before → after)

```
before
  POST /api/v1/triggers/{decisionId}/switch   (토큰: 다른 사용자)
    → SwitchController.executeSwitch(decisionId, request)
      → SwitchService.executeSwitch(decisionId, request)
         결정 조회 → 락 → 후보 확인 → 대표 변경            200. 남의 플랜이 바뀐다

after
  POST /api/v1/triggers/{decisionId}/switch   (토큰: 다른 사용자)
    → SwitchController.executeSwitch(@CurrentUser userId, decisionId, request)
      → SwitchService.executeSwitch(userId, decisionId, request)
         결정 조회 (트리거와 플랜을 함께 가져온다)
         decision.getTrigger().getPlan().validateOwner(userId)   403 PLAN_ACCESS_DENIED
         락을 잡기 전에 끝난다
```

플랜을 찾아가는 경로는 API마다 다르다.

| API | 플랜까지의 경로 |
|---|---|
| 후보 등록 | 요청의 카테고리 → 플랜 |
| 대표 지정, 후보 삭제 | 후보 → 카테고리 → 플랜 |
| 트리거 생성 | 요청의 카테고리 → 플랜 |
| 결정 | 트리거 → 플랜 |
| 전환 | 결정 → 트리거 → 플랜 |

### 구현 파일

| 파일 | 변경 |
|---|---|
| `plan/domain/Plan.java` | `isOwnedBy`, `validateOwner` 추가 |
| `PlaceCandidateService`, `TriggerService`, `DecisionService`, `SwitchService` | 여섯 메서드가 `userId`를 받아 소유권 확인 |
| `PlaceCandidateController`, `TriggerController`, `DecisionController`, `SwitchController` | `@CurrentUser Long userId` 추가, Swagger에 403 응답 추가 |
| `PlanService`, `PlanCategoryService` | 복사돼 있던 소유자 비교 8곳을 엔티티 메서드로 |
| `plan/domain/PlanTest.java` | 새로 추가. 소유권 규칙의 도메인 단위 테스트 |
| `integration/PlanningAuthorizationIntegrationTest.java` | 새로 추가. 다른 사용자의 토큰으로 여섯 API 호출 |
| 서비스·컨트롤러 단위 테스트 8개 | 새 시그니처에 맞춤 |
| `src/test/resources/archunit_store/` | 고친 위반 8줄 제거, 시그니처가 바뀐 메서드의 기록 7줄은 시그니처 문자열만 갱신 |

### 검증 증거

```
수정 전: PlanningAuthorizationIntegrationTest   tests=6 failures=6
  남의 플랜에 후보를 등록하면 …        expected: 403 but was: 200
  남의 플랜의 대표 후보를 바꾸면 …      expected: 403 but was: 200
  남의 플랜의 후보를 삭제하면 …        expected: 403 but was: 200
  남의 플랜에 트리거를 만들면 …        expected: 403 but was: 200
  남의 플랜의 트리거에 결정을 내리면 …  expected: 403 but was: 200
  남의 플랜에서 전환을 실행하면 …      expected: 403 but was: 200

수정 후
  PlanningAuthorizationIntegrationTest   tests=6 failures=0
  .claude/scripts/verify.sh              전체 145 · 실패 0
```

각 테스트는 403과 에러 코드 4002를 확인하고, 데이터가 바뀌지 않았는지도 DB에서 확인한다(후보 수, 대표 후보, 삭제 여부, 트리거·결정·전환 기록 수).

본인의 흐름을 지키는 기존 통합 테스트(핵심 흐름, 삭제와 겹치는 경쟁)는 그대로 통과한다.

기준선: `new ApplicationException(...)` 사용 88 → 80.

### 알려진 한계

- 플랜을 따라갈 때 지연 로딩으로 조회가 한두 번 더 나간다. 전환은 처음부터 플랜까지 함께 가져오므로 추가 조회가 없다.
- 추천 서비스의 소유자 비교 2곳은 그대로다. 추천은 다른 컨텍스트라 Planning 공개 API를 만들 때 함께 정리한다.
- 통계·추천 API는 원래 소유권을 확인하고 있어 이번 대상이 아니었다. 이번에 다시 검증하지는 않았다.
- 서비스 단위 테스트는 엔티티를 mock으로 만드는 옛 방식이 그대로다. 소유권 확인이 지나가도록 플랜만 진짜 객체로 넣었다.
- 메서드 시그니처가 바뀌면 ArchUnit은 그 메서드의 기존 위반을 새 위반으로 본다. 위반 문구에 시그니처가 들어가기 때문이다. 기록의 시그니처 문자열만 갱신했고 줄 수가 같은 것을 확인했다.

## 2-5 Planning → Place Catalog 공개 API

### 왜 지금

Planning의 경계 위반 기준선은 49건이었다. 전부 다른 컨텍스트의 타입을 직접 쓰는 것이다.

| 대상 | 건수 | 어디서 |
|---|---|---|
| `place.domain.PlaceCategoryType` (enum) | 38 | `PlanCategory`, 카테고리·플랜 상세 DTO 6개, `PlanService` |
| `place.domain.Place` (엔티티) | 16 | `PlaceCandidate.place`(`@ManyToOne`), `PlaceCandidateService`, `PlanService` |
| `user.repository.UserRepository`, `User`, `UserProfile` | 7 | `PlanService.buildPlanDto` (작성자 닉네임·프로필 이미지) |

(한 줄에 두 타입이 함께 나오는 위반이 있어 합이 49보다 크다.)

Place Catalog는 4단계에서 처음 떼어낼 서비스다. 이 모양으로는 뗄 수 없었다. `place_candidates.place_id`가 `places`를 가리키는 JPA 연관이었고, 플랜 상세 조회는 `left join fetch cand.place`로 두 컨텍스트의 테이블을 한 쿼리에서 읽었다. DB가 나뉘면 이 쿼리는 실행할 수 없다.

### 설계 (선택지 · 결정)

**1. 플랜 상세가 장소 이름·주소·좌표를 얻는 방법**

| 안 | 방법 | 얻는 것 | 잃는 것 |
|---|---|---|---|
| A | 후보를 등록할 때 장소 정보를 `place_candidates`에 복사해 둔다 | 상세 조회가 Planning 테이블만 읽는다. Catalog가 죽어도 상세는 보인다 | 컬럼 추가와 기존 행 채우기, 데이터 중복 |
| **B (채택)** | 후보는 `placeId`만 갖고, 조회할 때 ID를 모아 Catalog 공개 API를 한 번 부른다 | 스키마 변경이 없다. 쿼리는 하나만 늘어난다 | 서비스로 떼면 이 호출이 네트워크를 타고, Catalog 장애가 상세 조회로 번진다 |

A가 막아 주는 고통은 Catalog를 실제로 떼어낸 뒤에 생긴다. 그때 Catalog를 죽여 보고 정한다. B에서 A로 가는 길은 막히지 않는다(컬럼 추가와 채우기).

**2. `PlaceCategoryType`의 소유**

이 enum은 플랜 카테고리의 값이면서 카카오 검색 키워드와 카테고리 코드를 들고 있다. Planning은 값만 쓰고 키워드·코드는 장소 검색만 쓴다.

| 안 | 방법 | 버린 이유 또는 고른 이유 |
|---|---|---|
| A | Planning으로 옮긴다 | Catalog의 검색이 Planning을 import하게 된다. 첫 추출의 근거인 "나가는 의존 0"이 깨진다 |
| **B (채택)** | Catalog 소유로 두고 공개 API 패키지로 옮긴다 | 이동 한 번으로 38건이 사라지고 나가는 의존 0이 유지된다 |
| C | Planning의 값 enum과 Catalog의 검색 매핑으로 쪼갠다 | 값 목록이 두 곳이 되고 변환 코드가 생긴다. 지금 없는 고통을 위한 중복이다 |

**3. 공개 API의 모양**

| 안 | 방법 | 버린 이유 또는 고른 이유 |
|---|---|---|
| A | 기존 서비스를 그대로 부르고 반환만 record로 바꾼다 | 무엇이 공개이고 무엇이 내부인지 구분이 없다 |
| **B (채택)** | `{module}.api` 패키지에 인터페이스, 값 record, enum을 둔다. 구현은 `service`에 | 쓰는 쪽은 `api` 패키지만 import한다. 서비스로 떼면 구현만 원격 호출로 바뀌고 쓰는 쪽 코드는 그대로다 |
| C | 쓰는 쪽에 포트를 두고 어댑터가 부른다 | 쓰는 컨텍스트마다 포트와 어댑터가 생긴다. Recommendation도 Catalog를 쓴다 |

구현이 하나뿐인 인터페이스는 보통 만들지 않는다. 이 인터페이스는 4단계에서 구현이 실제로 바뀌는 자리라서 둔다. `api` 패키지는 자기 모듈의 엔티티를 import하지 않게 했다. 그래서 `Place` → `PlaceInfo` 변환은 `PlaceInfo.from(Place)`가 아니라 구현 서비스가 한다.

이 결정은 2-6에서도 그대로 쓰므로 2단계 ADR에 담는다.

**4. `plan → user` 7건**

같은 조각에서 함께 끊었다. 같은 모양(`user.api.UserApi`)을 한 번 더 적용하는 일이고, Planning 기준선이 0이 된다.

구현하면서 달라진 점:

- `PlacePersistenceService`를 `PlaceCatalogService`로 바꾸고 `PlaceCatalogApi`를 구현하게 했다. `getOrCreate`는 `register`가 됐고 엔티티 대신 `PlaceInfo`를 돌려준다. `REQUIRES_NEW`는 그대로다.
- `PlaceRegisterRequest`도 공개 API의 입력이라 `place.api`로 옮겼다.
- 대표 지정(`setRepresentative`)은 장소를 쓰지 않는데도 장소까지 fetch join하고 있었다. 장소 없는 조회로 바꾸고 쓰이지 않게 된 쿼리 두 개를 지웠다.
- 추천의 `PlaceRecommendationService.resolveBase`가 `candidate.getPlace()`로 좌표를 읽고 있었다. 필드가 사라지므로 이 부분만 Catalog 공개 API 조회로 바꿨다. 추천의 나머지는 2-6이다.
- 후보가 가리키는 장소가 Catalog에 없으면 플랜 상세는 500으로 실패한다. 한 DB 안에서는 일어나지 않는 일이고, 서비스로 떼면 다시 정할 자리라 표식을 남겼다.

### 흐름 (before → after)

플랜 상세 조회:

```
before
  PlanService.getPlanDetail            BEGIN (readOnly)
    plans 조회 → validateOwner
    users ⋈ user_profiles               UserRepository를 직접 주입
    plan_categories ⋈ place_candidates ⋈ places    두 컨텍스트의 테이블을 한 쿼리에서 JOIN
    category_states IN (...)
                                        COMMIT

after
  PlanService.getPlanDetail            BEGIN (readOnly)
    plans 조회 → validateOwner
    UserApi.findUserSummary(ownerId)    → users ⋈ user_profiles (Identity 안에서)
    plan_categories ⋈ place_candidates  Planning 테이블만
    category_states IN (...)
    후보들의 placeId를 모은다
    PlaceCatalogApi.getPlaces(placeIds) → places where id in (...) (Catalog 안에서)
    Map<placeId, PlaceInfo>로 조립
                                        COMMIT
```

후보 등록:

```
before  Place place = placePersistenceService.getOrCreate(...)   REQUIRES_NEW, 엔티티가 경계를 넘는다
        PlaceCandidate.create(category, place)
after   PlaceInfo place = placeCatalogApi.register(...)          REQUIRES_NEW 그대로, 값 record가 넘어온다
        PlaceCandidate.create(category, place.id())
```

바뀌지 않은 것: URL, 응답 JSON, `place_id` 컬럼, 유니크 제약 `uk_category_place`.

### 구현 파일

| 파일 | 변경 |
|---|---|
| `place/api/PlaceCatalogApi.java`, `PlaceInfo.java` | 새로 추가. Catalog의 공개 API와 경계 밖으로 나가는 장소 정보 |
| `place/api/PlaceCategoryType.java`, `PlaceRegisterRequest.java` | `place.domain`, `place.dto`에서 이동 |
| `place/service/PlaceCatalogService.java` | `PlacePersistenceService`에서 이름 변경. `PlaceCatalogApi` 구현, `getPlaces` 추가, 클래스 `readOnly` |
| `user/api/UserApi.java`, `UserSummary.java` | 새로 추가. Identity의 공개 API |
| `user/service/UserService.java` | `UserApi` 구현(`findUserSummary`) |
| `plan/domain/PlaceCandidate.java` | `@ManyToOne Place place` → `Long placeId` |
| `plan/repository/PlanCategoryRepository.java` | 플랜 상세 쿼리에서 `left join fetch cand.place` 제거 |
| `plan/repository/PlaceCandidateRepository.java` | 장소를 fetch join하던 쿼리 2개 삭제 |
| `plan/service/PlaceCandidateService.java` | `PlaceCatalogApi` 주입, 후보를 `place.id()`로 생성 |
| `plan/service/PlanService.java` | `UserApi`·`PlaceCatalogApi` 주입, 장소를 ID로 한 번에 조회해 조립 |
| `plan/dto/PlaceSummaryResponse.java` | `from(PlaceInfo)` 추가, 빌더 제거 |
| `recommendation/service/PlaceRecommendationService.java` | 대표 후보의 좌표·주소를 Catalog 공개 API로 조회 |
| `architecture/ModuleBoundaryTest.java`, `ArchitectureRules.java` | "공개 API를 연 컨텍스트는 api 패키지로만 쓴다" 규칙 추가(catalog, identity) |
| `integration/PlaceCatalogContractIntegrationTest.java` | 새로 추가. 후보 등록과 플랜 상세의 응답 JSON 고정 |
| `place/service/PlaceCatalogServiceTest.java`, `place/fixture/PlaceFixture.java` | 새로 추가 |
| `PlanServiceTest`, `PlaceCandidateServiceTest`, `UserServiceTest` | 새 협력 객체에 맞춤, 테스트 5개 추가 |
| `src/test/resources/archunit_store/` | 아래 검증 증거 참고 |

### 검증 증거

구현 전에 응답 계약 테스트를 먼저 쓰고 옛 코드에서 통과하는 것을 확인했다. 구현 뒤에도 그대로 통과한다.

```
구현 전 (옛 코드)   PlaceCatalogContractIntegrationTest   tests=4 failures=0
구현 후            PlaceCatalogContractIntegrationTest   tests=4 failures=0
.claude/scripts/verify.sh   format 통과 · compile 통과 · 전체 162 · 실패 0
```

플랜 상세 조회 한 번에 나가는 쿼리(구현 후, Testcontainers MySQL):

```
select ... from plans p1_0 where p1_0.id=? and p1_0.deleted_at is null
select ... from users u1_0 left join user_profiles up1_0 on u1_0.id=up1_0.user_id where u1_0.id=?
select ... from plan_categories pc1_0 left join place_candidates c1_0 on pc1_0.id=c1_0.category_id ... order by pc1_0.sequence
select ... from category_states cs1_0 left join plan_categories c1_0 on c1_0.id=cs1_0.category_id where c1_0.id in (?)
select ... from places p1_0 where p1_0.id in (?,?)
```

세 번째 쿼리에서 `places`가 빠졌고, 장소는 마지막 쿼리 하나로 후보 두 개 분을 함께 읽는다.

기준선:

| 규칙 | 전 | 후 |
|---|---|---|
| Planning이 다른 컨텍스트의 Repository·Entity 사용 | 49 | 0 |
| Recommendation이 다른 컨텍스트의 Repository·Entity 사용 | 131 | 105 |
| 공개 API를 연 Catalog를 api 패키지 밖으로 사용 (새 규칙) | | 17 |
| 공개 API를 연 Identity를 api 패키지 밖으로 사용 (새 규칙) | | 0 |
| `new ApplicationException(...)` 사용 | 80 | 79 |
| `@Transactional` 서비스의 클래스 readOnly 선언 없음 | 14 | 13 |
| domain의 표준 예외 | 10 | 10 |

Recommendation 131 → 105: enum이 `place.api`로 옮겨 가면서 19건, 대표 후보의 장소를 엔티티로 읽던 호출이 사라지면서 나머지가 줄었다. 새 규칙의 17건은 전부 Recommendation이 `PlaceSearchService`·`PlaceSearchResponse`·`PlaceErrorCase`를 쓰는 것으로, 2-6에서 줄인다.

기준선 파일에서 문구를 직접 고친 줄은 8줄이다. 새 위반을 인정한 것이 아니라 같은 위반의 문구가 바뀐 것이다.

- `PlaceSearchService.search`의 `new ApplicationException` 1줄: 파라미터 타입 `PlaceCategoryType`의 패키지가 바뀌어 시그니처 문자열이 달라졌다.
- `PlaceCandidate.create`의 표준 예외 2줄: 파라미터가 `Place`에서 `Long`으로 바뀌어 시그니처 문자열이 달라졌다.
- `PlaceRecommendationService` 생성자의 리포지토리 주입 3줄: 생성자에 `PlaceCatalogApi`가 추가돼 시그니처 문자열이 달라졌다.
- `PlaceRecommendationService.resolveBase` 2줄: `candidate.getPlace()` 호출 4건과 `findAliveByIdWithCategoryAndPlace` 호출이 `candidate.getPlaceId()`와 `findAliveById` 호출로 바뀌었다.

### 워크스루 요약

플랜 상세 조회(`GET /api/v1/plans/{planId}`)를 따라가면 바뀐 곳이 순서대로 나온다.

1. `PlanService`는 `UserApi`와 `PlaceCatalogApi` 인터페이스를 주입받는다. 구현 클래스나 리포지토리를 모른다.
2. 작성자 닉네임은 `userApi.findUserSummary(ownerId)`로 받는다. 엔티티 대신 값 record(`UserSummary`)가 넘어온다.
3. `findAllDetailByPlanId`는 카테고리와 후보까지만 가져온다. 후보는 장소의 ID만 안다.
4. `findPlacesOf`가 후보들의 `placeId`를 모아 중복을 빼고 `placeCatalogApi.getPlaces(ids)`를 한 번 부른다. 후보마다 부르면 후보 수만큼 호출이 나간다.
5. `PlaceCatalogService.getPlaces`가 `places`를 `IN`으로 읽어 `PlaceInfo`로 바꿔 돌려준다. 변환은 `api` 패키지가 엔티티를 모르도록 서비스가 한다.
6. `buildCandidateDto`가 `Map`에서 장소를 꺼내 응답을 조립한다. 응답 JSON은 전과 같다.

### 알려진 한계

- 로컬 DB에는 `place_candidates.place_id → places.id` FK 제약이 남아 있다. `ddl-auto: update`는 제약을 지우지 않는다. 4단계에서 스키마를 나눌 때 없앤다. 테스트 DB는 매번 새로 만들어서 FK가 없다.
- 후보 등록은 장소 저장(Catalog, 별도 트랜잭션)과 후보 저장(Planning)이 원자적이지 않다. 후보 저장이 실패해도 장소는 남는다. 공용 데이터라 남아도 되지만, 서비스로 떼면 "장소 저장은 됐는데 응답을 못 받은" 경우가 추가된다.
- 플랜 상세에서 Catalog가 장소를 돌려주지 않으면 500이다. Catalog가 죽었을 때 장소 없이 보여 줄지, 후보에 복사해 둘지는 4단계에서 정한다.
- `PlaceCategoryType`을 Planning과 Catalog가 함께 쓴다. 서비스로 떼면 값이 추가될 때 양쪽을 함께 배포해야 한다.
- 추천은 여전히 Planning의 리포지토리와 엔티티를 직접 쓴다(105건). 이번에는 장소를 읽는 네 줄만 바꿨다.
- 작성자를 찾지 못하면 PLAN_NOT_FOUND를 돌려주는 기존 동작과, 닉네임 기본값 `"사용자"`를 서비스가 정하는 것은 그대로 뒀다.
- `PlaceCandidateServiceTest`는 엔티티를 mock으로 만드는 옛 방식이 그대로다. 바뀐 협력 객체만 맞췄다.
- 새 ArchUnit 규칙의 기준선은 `allowStoreCreation=false`인 채로 만들어졌다. 이 설정은 기준선 폴더가 없을 때만 막고, 이미 있는 폴더에 새 규칙이 추가되는 것은 막지 않는다.

## 2-6 Stats·Recommendation → Planning 공개 API

> 진행 중

### 왜 지금

2-5까지 끝낸 뒤 경계 위반은 199건이 남았고, 전부 Recommendation과 Stats가 Planning의 내부를 직접 쓰는 것이다. 2-5에서 정한 `{module}.api` 방식을 이번에는 Planning이 내놓는 쪽이 되어 적용한다.

| 남은 것 | 건수 |
|---|---|
| `TriggerType`·`DecisionType` enum을 Recommendation·Stats가 사용 | 71 |
| Stats → Planning 리포지토리·엔티티 | 63 |
| Recommendation → Planning 리포지토리·엔티티 | 65 |
| Recommendation → Catalog를 `api` 패키지 밖으로 사용 | 17 |
| trigger가 `stats.event.*` 이벤트를 생성 | 2 |
| global이 도메인 모듈의 ErrorCase를 사용 | 8 |

### 설계 (선택지 · 결정)

양이 많아 네 묶음으로 나눠 묶음마다 확인하며 진행한다: 가) enum 이동, 나) Stats → Planning 조회 API와 이벤트 소유권, 다) Recommendation → Planning·Catalog 공개 API, 라) global의 도메인 의존.

**묶음 가. enum을 어디로 옮기나**

| 안 | 위치 | 버린 이유 또는 고른 이유 |
|---|---|---|
| **A (채택)** | `trigger.api.TriggerType`, `trigger.api.DecisionType` | 지금 패키지 구조 그대로라 이동이 가장 작다. Planning의 공개 패키지가 둘(`plan.api`, `trigger.api`)이 되지만 같은 컨텍스트라 경계 규칙에는 차이가 없다 |
| B | `plan.api`로 모은다 | 창구가 한 곳이 되지만 `trigger`의 엔티티가 `plan.api`의 enum을 쓰게 되어 어색하다 |

**묶음 나. Stats가 Planning의 데이터를 어떻게 얻나**

통계 서비스 6개가 Planning의 리포지토리 4개를 직접 주입받아 쿼리 메서드 11개를 부르고 있었다.

| 안 | 방법 | 버린 이유 또는 고른 이유 |
|---|---|---|
| **A (채택)** | Planning이 조회용 공개 API를 열고 Stats가 부른다 | 변경이 작고 동작이 그대로다. 대가는 Stats를 서비스로 떼었을 때 통계 화면이 Planning에 동기로 의존한다는 것 |
| B | Stats가 이벤트를 받아 자기 테이블에 통계용 데이터를 쌓고 조회는 자기 테이블만 읽는다 | Planning이 죽어도 통계가 보이는 진짜 분리지만, 이벤트가 유실되면 숫자가 계속 틀린다. 유실을 막는 Outbox와 Kafka는 5단계의 일이다 |

A는 B로 가는 중간 단계다. 5단계에서 Stats를 떼어낼 때 조회 API 호출을 자기 테이블 조회로 바꾼다.

인터페이스는 데이터의 주인 패키지에 뒀다. `plan.api.PlanQueryApi`(플랜 수, 소유한 플랜, 최근 플랜)와 `trigger.api.ActivityQueryApi`(트리거·결정·전환의 건수와 최근 목록). 리포지토리 안의 프로젝션 인터페이스(`RecentSwitchActivityRow` 등)는 경계 밖으로 내보내지 않고 공개 record(`RecentActivity`, `PlanActivity`, `PlanSummary`)로 바꿔 내보낸다. 쿼리는 리포지토리에 있던 것을 그대로 쓴다.

**묶음 나. 이벤트 클래스의 위치**

전환과 트리거 발생을 알리는 이벤트 클래스가 받는 쪽인 `stats.event`에 있어서, 보내는 쪽인 trigger가 stats를 import했다. 이벤트는 보내는 쪽이 주인이므로 Planning으로 옮긴다. `trigger.event`에 두면 Stats가 쓰는 순간 "공개 패키지로만" 규칙에 걸리므로 `trigger.api.event`에 뒀다.

### 흐름 (before → after)

묶음 나:

```
GET /api/v1/stats/summary

before
  StatsService                          BEGIN (readOnly)
    planRepository.countActiveByOwnerId          Planning의 리포지토리를 직접
    switchLogRepository.countByOwnerIdAndPeriod
    decisionRepository.countByOwnerIdAndPeriod
                                        COMMIT

after
  StatsService                          BEGIN (readOnly)
    planQueryApi.countActivePlans(ownerId)        → PlanQueryService → PlanRepository
    activityQueryApi.countSwitches(ownerId, startAt)   → ActivityQueryService → SwitchLogRepository
    activityQueryApi.countDecisions(ownerId, startAt)  → ActivityQueryService → DecisionRepository
                                        COMMIT
```

```
전환 실행 → 통계 갱신

before  SwitchService ── new stats.event.SwitchLogCreatedEvent ──▶ PlanStatsEventListener
        trigger가 stats를 import하고 stats는 trigger를 import한다 (순환)
after   SwitchService ── new trigger.api.event.SwitchLogCreatedEvent ──▶ PlanStatsEventListener
        stats만 trigger.api를 import한다 (한 방향)
```

### 구현 파일

묶음 나:

| 파일 | 변경 |
|---|---|
| `plan/api/PlanQueryApi.java`, `PlanSummary.java` | 새로 추가 |
| `plan/service/PlanQueryService.java` | 새로 추가. `PlanQueryApi` 구현 |
| `trigger/api/ActivityQueryApi.java`, `RecentActivity.java`, `PlanActivity.java` | 새로 추가 |
| `trigger/service/ActivityQueryService.java` | 새로 추가. `ActivityQueryApi` 구현 |
| `trigger/api/event/TriggerCreatedEvent.java`, `SwitchLogCreatedEvent.java` | `stats.event`에서 이동 |
| `stats/service/` 6개 (`StatsService`, `TriggerStatsService`, `RecentActivityStatsService`, `PlanActivityStatsService`, `PlanStatsMaterializedService`, `PlanStatsUpdater`) | 리포지토리 주입을 공개 API 주입으로 |
| `stats/listener/PlanStatsEventListener.java`, `TriggerService`, `SwitchService` | 이벤트 import 경로 |
| `integration/StatsContractIntegrationTest.java` | 새로 추가. 통계 API 5개의 응답 고정 |
| `stats/service/StatsServiceTest.java` | 새로 추가 |

묶음 가:

| 파일 | 변경 |
|---|---|
| `trigger/api/TriggerType.java`, `DecisionType.java` | `trigger.domain`에서 이동 |
| 두 enum을 쓰는 파일 23개 (trigger, recommendation, stats와 그 테스트) | import 경로만 변경 |
| `src/test/resources/archunit_store/` | 아래 참고 |

### 검증 증거

묶음 나:

구현 전에 통계 API 5개의 응답을 고정하는 테스트를 쓰고 옛 코드에서 통과하는 것을 확인했다. 트리거 2건(유지 1, 전환 1)을 만든 뒤 요약, 트리거 분석, 최근 활동, 플랜별 활동, 플랜별 통계를 확인한다.

```
구현 전 (옛 코드)   StatsContractIntegrationTest   tests=5 failures=0
구현 후            .claude/scripts/verify.sh      format 통과 · compile 통과 · 전체 169 · 실패 0
```

| 기준선 | 전 | 후 |
|---|---|---|
| Stats가 다른 컨텍스트의 Repository·Entity 사용 | 63 | 0 |
| 다른 컨텍스트의 이벤트 클래스 생성 | 2 | 0 |
| 경계 위반 합계 | 128 | 65 |

기준선 파일을 손으로 고친 줄은 없다. `stats` 패키지의 import 가운데 `plan`·`trigger`를 가리키는 것은 전부 `api` 패키지다.

묶음 가:

```
.claude/scripts/verify.sh   format 통과 · compile 통과 · 전체 162 · 실패 0
```

| 기준선 | 전 | 후 |
|---|---|---|
| Recommendation이 다른 컨텍스트의 Repository·Entity 사용 | 105 | 65 |
| Stats가 다른 컨텍스트의 Repository·Entity 사용 | 94 | 63 |
| 경계 위반 합계 | 199 | 128 |

파라미터 타입의 패키지가 바뀌어 시그니처 문자열이 달라진 기존 위반 10줄(`Trigger.create`, `Decision.create`, `PlaceRecommendationService.resolveBase`)은 문구만 갱신했다. 세 규칙의 건수는 그대로다(79, 10, 11).

경계 규칙 9개의 기준선 파일 이름이 전부 바뀌었다. `ModuleBoundaryTest`의 `because(...)` 문구를 다듬으면서 규칙 설명이 달라졌고, ArchUnit은 설명 문자열로 기준선을 찾기 때문에 9개를 새 규칙으로 보고 기준선을 새로 만들었다. 새 기준선의 건수가 옛 기준선에서 enum 71건을 뺀 값과 같은 것을 확인한 뒤(65, 63, 17, 2, 나머지 0) 옛 파일 9개와 `stored.rules`의 옛 줄 9개를 지웠다.

### 알려진 한계

- Stats는 여전히 Planning에 의존한다. 내부 대신 공개 API에 의존하게 됐을 뿐이다. 서비스로 떼면 통계 화면마다 Planning을 동기로 호출하게 되고, 최근 활동은 호출이 두 번이다.
- `ActivityQueryApi`의 메서드가 10개다. 통계 화면이 필요한 모양 그대로 열어서 Stats 전용에 가깝다. 5단계에서 Stats가 자기 테이블을 가지면 대부분 없어진다.
- 통계 쿼리는 여전히 Planning의 테이블에서 돌아간다. 통계 조회 부하가 Planning의 DB에 걸린다.
- 통계 갱신 이벤트는 메모리에만 있다. 커밋 직후 프로세스가 죽거나 리스너가 실패하면 `plan_stats`가 틀린 채로 남는다. 이번에는 이벤트 클래스의 위치만 옮겼다.
- 통계 서비스 4곳에 복사된 기간 계산(`LocalDateTime.now()` 직접 호출), 삭제된 플랜의 트리거가 통계에 섞이는 문제, 플랜별 통계의 `categoryTypes`가 항상 null인 문제는 그대로다.
- `apiOnly("planning")` 규칙은 아직 추가하지 않았다. Recommendation의 위반이 남아 있어 지금 넣으면 같은 위반이 두 규칙에 이중으로 잡힌다.
- 규칙 설명 문구만 바꿔도 기준선이 조용히 새로 만들어진다. `allowStoreCreation=false`는 기준선 폴더가 없을 때만 막는다. 이번에는 건수를 대조해 새로 인정된 위반이 없음을 확인했지만, 확인하지 않았다면 그 사이에 생긴 위반이 기준선에 묻혔을 것이다. `stored.rules`에 없는 규칙이 있으면 실패하는 검사가 필요하다.
