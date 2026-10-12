# 2단계 모듈러 모놀리스

> 진행 중

## 이 단계의 목표

서비스를 떼어내기 전에, 한 프로세스 안에서 먼저 경계를 지키는 구조로 만든다. 컨텍스트 안의 참조는 그대로 두고, 컨텍스트를 넘는 참조(다른 컨텍스트의 리포지토리 주입, 엔티티 사용, 테이블 JOIN)를 공개 API 호출로 바꾼다. 진척은 ArchUnit 기준선의 건수로 본다.

| 조각 | 내용 | 상태 |
|---|---|---|
| 2-1 | 죽은 코드 제거 | 완료 |
| 2-2 | 컨텍스트 기준으로 모듈 재정의 | |
| 2-3 | 전환 경쟁 상태 수정 | |
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
