# 2단계 모듈러 모놀리스

> 진행 중

## 이 단계의 목표

서비스를 떼어내기 전에, 한 프로세스 안에서 먼저 경계를 지키는 구조로 만든다. 컨텍스트 안의 참조는 그대로 두고, 컨텍스트를 넘는 참조(다른 컨텍스트의 리포지토리 주입, 엔티티 사용, 테이블 JOIN)를 공개 API 호출로 바꾼다. 진척은 ArchUnit 기준선의 건수로 본다.

| 조각 | 내용 | 상태 |
|---|---|---|
| 2-1 | 죽은 코드 제거 | 완료 |
| 2-2 | 컨텍스트 기준으로 모듈 재정의 | 완료 |
| 2-3 | 전환 경쟁 상태 수정 | 완료 |
| 2-4 | 소유권 검증 추가 | |
| 2-5 | Planning → Place Catalog 공개 API | |
| 2-6 | Stats·Recommendation → Planning 공개 API, 이벤트 소유권, global의 도메인 의존 | |

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

