# 리포지토리 · 쿼리

## 1. 무엇으로 쓸까

| 조건 | 방법 |
|---|---|
| 조건 2개 이하의 단순 조회 | 파생 쿼리 `findByIdAndDeletedAtIsNull` |
| 조건 3개 이상, JOIN, 집계 | `@Query` JPQL + `@Param` |
| JPQL로 못 하는 것 (서브쿼리 안의 LIMIT, DB 함수) | native query + 이유 주석 + Testcontainers 테스트 필수 |

- 파생 쿼리 이름이 한 줄을 넘으면(`findAllByPlan_IdAndDeletedAtIsNullOrderBySequenceAsc`) `@Query`로 바꾸고 의미 있는 이름을 붙인다: `findActiveByPlanIdOrderBySequence`.
- `@Query`의 모든 파라미터에 `@Param`을 붙인다. 컴파일 옵션(`-parameters`)에 기대지 않는다.
  - BReady 현황: `PlaceCandidateRepository`의 여러 메서드가 `@Param` 없이 `:candidateId`를 쓴다.

## 2. 이름

| 접두어 | 의미 |
|---|---|
| `findBy…` | 단건 `Optional` 또는 목록 |
| `existsBy…` | boolean |
| `countBy…` | long |
| `…ForUpdate` | 비관적 락을 건다 (BReady 관례 유지) |
| `findActive…` | soft delete된 행 제외 (6번) |

- BReady 현황: 같은 의미에 `Alive`(후보)와 `DeletedAtIsNull`(플랜)을 섞어 쓴다. 새 메서드는 `Active`로 통일한다.

## 3. 조회 결과 형태

- 화면·통계용 조회는 엔티티가 아니라 **프로젝션**으로 받는다. 필요한 컬럼만 가져오고 영속성 컨텍스트에 쌓이지 않는다.
  - 인터페이스 프로젝션(BReady 관례, `PlanRepository.PlanSwitchStatsRow` — `plan/repository/PlanRepository.java:20`) 또는 record 생성자 프로젝션(`select new ...Row(...)`).
- 수정할 엔티티는 엔티티로 받는다.

## 4. N+1

- 컬렉션·연관을 반복문에서 건드리기 전에 **몇 번 쿼리가 나가는지** 따진다. [리뷰]
- 해결 순서:
  1. `join fetch`(단건 또는 컬렉션 하나) — BReady 좋은 예: `PlanCategoryRepository.findAllDetailByPlanId` (`plan/repository/PlanCategoryRepository.java:58`)
  2. ID를 모아서 `IN` 조회 후 `Map`으로 조립 — BReady 좋은 예: 대표 후보 일괄 조회 (`plan/service/PlanService.java:108`)
  3. `@EntityGraph`
- 페이징과 컬렉션 `join fetch`를 같이 쓰지 않는다(메모리 페이징 경고 + 결과 중복). 페이징이 필요하면 ID 페이지를 먼저 조회하고 2번 방법으로.

## 5. 락

- `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@Query` + 이름 끝 `ForUpdate`.
- 락 조회는 쓰기 트랜잭션 안에서만 의미가 있다.
- 락을 잡고 나서 검증한다(`service-transaction.md` 4번).

## 6. soft delete 필터

- 지운 행을 빼야 하는 조회는 쿼리에 `deletedAt is null`을 **명시**하고 이름에 `Active`를 넣는다.
- JOIN하는 쪽 엔티티의 삭제 여부도 함께 따진다. 플랜이 지워졌는데 그 플랜의 트리거가 통계에 잡히지 않게.
  - BReady 현황: `TriggerRepository.countByOwnerIdAndPeriod`는 플랜 삭제 여부를 보지 않는다(context-map R6).
- `@SQLRestriction`을 새로 쓰지 않는다(`entity.md` 7번).

## 7. 모듈 경계

- 리포지토리는 **자기 모듈의 서비스에서만** 쓴다. 다른 모듈의 리포지토리를 주입하지 않는다. [빌드]
- 다른 모듈의 테이블을 JOIN하는 쿼리를 새로 만들지 않는다. 필요한 데이터는 그 모듈의 공개 API로 받는다(`msa-boundary.md`).
  - BReady 현황: `SwitchLogRepository`·`DecisionRepository`·`TriggerRepository`가 `Plan`까지 JOIN해서 `ownerId`로 거른다. `PlanRepository`의 native query는 `triggers`·`decisions`·`switch_logs`를 JOIN한다.

## 8. 일괄 변경

- 여러 행을 한 번에 바꾸면 `@Modifying(clearAutomatically = true, flushAutomatically = true)` + `@Query("update ...")`. 영속성 컨텍스트에 남은 옛 값을 읽지 않게 한다.
- 카운터·수량처럼 동시에 바뀌는 숫자는 "읽고 → 더하고 → 저장" 대신 원자적 UPDATE: `set count = count + 1 where id = :id`.
- 반환값(바뀐 행 수)이 0이면 ErrorCase로 처리한다.