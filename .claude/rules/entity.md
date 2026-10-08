# 엔티티 · 도메인 모델

엔티티는 **데이터 묶음이 아니라 규칙을 지키는 객체**다. "이 값이 이렇게 바뀌어도 되는가"는 엔티티가 판단한다.

## 1. 골격 [빌드]

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA 전용, 외부 생성 금지
@Entity
@Table(name = "plans")
public class Plan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;                             // user 모듈 소유 → ID 참조 (6번)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status;

    public static Plan create(Long ownerId, String title, LocalDate planDate, String region) {
        Plan plan = new Plan();
        plan.ownerId = ownerId;
        plan.title = title;
        plan.planDate = planDate;
        plan.region = region;
        plan.status = PlanStatus.ACTIVE;
        return plan;
    }
}
```

- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 필수. public 생성자 금지.
  - BReady 현황: `Trigger`·`SwitchLog`·`PlanStats`는 `@NoArgsConstructor`(public)(`trigger/domain/Trigger.java:14` 외), `User`·`Plan`·`UserProfile`은 생성자를 선언하지 않아 암묵적 public.
- 생성은 `public static create(...)` 정적 팩토리만. 의미가 다른 생성 경로는 이름으로 구분한다(`User.createLocal`, `User.createSocial` — BReady 좋은 예).
- 감사 필드(`createdAt`, `updatedAt`, `deletedAt`)는 `BaseEntity`를 상속해서 얻는다. 직접 선언하지 않는다.

## 2. 생성 시 검증

- 필수값·형식 불변식은 `create` 안에서 검증하고 **`ApplicationException.from(XxxErrorCase)`**로 던진다.
- `IllegalArgumentException`·`IllegalStateException`을 도메인 규칙에 쓰지 않는다. 핸들러가 500으로 바꿔 버린다. [린트]
  - BReady 현황: `Trigger.create`(`trigger/domain/Trigger.java:48`), `Place.create`, `PlaceCandidate.create`, `User.createSocial`.
- 요청 형식 검증(`@NotBlank` 등)은 DTO 몫이다(`dto.md` 3번). 엔티티는 **어디서 생성돼도** 깨지면 안 되는 규칙만 검증한다.

## 3. 상태 변경은 의도를 드러내는 메서드로 [빌드]

- setter 금지. 무엇을 하는지 이름이 말하는 메서드를 둔다.
  - 좋은 예: `CategoryState.changeRepresentative(Long)` (`plan/domain/CategoryState.java:40`), `PlanCategory.updateSequence(Integer)`.
  - 나쁜 예: `Plan.setShareToken(String)` (`plan/domain/Plan.java:56`), `User.setUserProfile(...)` (`user/domain/User.java:66`).
- 공유 토큰처럼 "없으면 만들고 있으면 그대로"인 규칙도 엔티티 안으로:
  ```java
  public String issueShareToken(Supplier<String> tokenGenerator) {
      if (shareToken == null) {
          shareToken = tokenGenerator.get();
      }
      return shareToken;
  }
  ```

## 4. 규칙 검증 메서드 — 소유권·상태

- 서비스에 흩어진 같은 검증은 엔티티 메서드 하나로 모은다.
  - BReady 현황: `if (!plan.getOwnerId().equals(userId))`가 `PlanService`·`PlanCategoryService`·추천 서비스에 10번 복사돼 있다(`plan/service/PlanService.java:58` 외).
  ```java
  public void validateOwner(Long userId) {
      if (!ownerId.equals(userId)) {
          throw ApplicationException.from(PlanErrorCase.PLAN_ACCESS_DENIED);
      }
  }
  ```
- 판단만 필요하면 `boolean isXxx(...)`, 위반 시 막아야 하면 `validateXxx(...)`.
- 다른 모듈의 엔티티를 검증하지 않는다. trigger가 plan 소유자를 확인해야 하면 plan 모듈의 공개 API에 묻는다(`msa-boundary.md` 2번).

## 5. enum

- `@Enumerated(EnumType.STRING)` + `@Column(length = N)`. ORDINAL 금지(순서가 바뀌면 데이터가 깨진다). [빌드]
- enum에 행위를 둔다. 분기 `switch`가 서비스 여러 곳에 있으면 enum 메서드로 옮긴다.
  - 좋은 예: `PlaceCategoryType`이 `keyword`, `kakaoCategoryCode`, `indoor`를 가진다.
  - 만들 것: `StatsPeriod.startAt(LocalDateTime now)` (`java-style.md` 2번).

## 6. 연관관계 vs ID 참조

| 상황 | 방법 |
|---|---|
| 같은 모듈, 같은 애그리거트 | `@ManyToOne(fetch = LAZY)`. EAGER 금지 [빌드] |
| 같은 모듈, 다른 애그리거트 | ID 참조 우선. 연관이 꼭 필요하면 LAZY 단방향 |
| **다른 모듈** | **`Long xxxId` 필드만.** 의도를 주석으로 남긴다 [빌드] |
| `@OneToMany` | 부모를 통해서만 생명주기가 관리되는 하위 객체일 때만. `mappedBy` 양방향은 이유가 있을 때만 |

- 다른 모듈 엔티티에 대한 `@ManyToOne`/`@OneToOne`/`@OneToMany`는 **새로 만들지 않는다**. 기존 것은 2단계에서 끊는다.
  - BReady 현황: `PlaceCandidate.category`(place → plan), `PlanCategory.candidates`(plan → place), `Trigger.plan`·`Trigger.category`(trigger → plan), `SwitchLog.fromCandidate`·`toCandidate`(trigger → place). 근거는 `docs/architecture/context-map.md` 4번.
  - 좋은 예(이미 ID로 끊어 둔 것): `Plan.ownerId`, `CategoryState.currentCandidateId`, `PlanStats.planId`.
- `cascade`는 같은 애그리거트 안에서만.

## 7. soft delete

- `BaseEntity.softDelete()`로 지운다. 하위 객체도 지워야 하면 부모 엔티티의 메서드가 책임진다.
- 조회에서 지운 행을 빼는 방법은 **쿼리에 명시**한다(`repository-query.md` 6번). `@SQLRestriction`을 새로 추가하지 않는다.
  - 이유: 숨은 필터라 통계·관리 쿼리에서 예상과 다른 결과가 나오고, 명시 필터와 섞이면 같은 조건이 두 번 걸린다.
  - BReady 현황: `PlaceCandidate`는 `@SQLRestriction`(`place/domain/PlaceCandidate.java:22`)과 쿼리의 `deletedAt is null`(`place/repository/PlaceCandidateRepository.java:39`)을 둘 다 쓴다.

## 8. 시간

- 엔티티는 현재 시각을 스스로 구하지 않는다. **파라미터로 받는다**.
  ```java
  public void changeRepresentative(Long candidateId, LocalDateTime changedAt) { ... }
  // 서비스: state.changeRepresentative(candidateId, LocalDateTime.now(clock));
  ```
  - BReady 현황: `CategoryState`·`Decision`·`Trigger`가 `LocalDateTime.now()`를 직접 부른다(`plan/domain/CategoryState.java:36` 외).
- `createdAt`/`updatedAt`은 JPA Auditing이 채운다. 손대지 않는다.

## 9. equals · hashCode · toString

- 엔티티에 Lombok `@EqualsAndHashCode`·`@ToString`을 붙이지 않는다. 지연 로딩 프록시와 양방향 연관 때문에 예기치 않은 쿼리나 무한 재귀가 생긴다.
- 동등성이 필요하면 ID 기반으로 직접 작성하고 이유를 주석으로 남긴다.

## 10. 엔티티 밖으로 내보내지 않는다

- 컨트롤러 응답에 엔티티를 넣지 않는다(`dto.md` 4번).
- 다른 모듈에 엔티티를 넘기지 않는다. 포트·공개 API 시그니처에도 넣지 않는다.
  - BReady 현황: `CategoryRecommendationPort`가 `List<PlanCategory>`를 받는다(`recommendation/port/CategoryRecommendationPort.java:12`).