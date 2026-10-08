# DTO

## 1. 요청·응답 모두 record [빌드]

```java
public record PlanCreateRequest(
        @NotBlank(message = "플랜 제목을 입력해주세요.") String title,
        @NotNull(message = "플랜 날짜를 선택해주세요.") LocalDate planDate,
        @NotBlank(message = "지역 정보를 입력해주세요.") String region
) {}

public record PlanCreateResponse(Long planId, LocalDateTime createdAt) {
    public static PlanCreateResponse from(Plan plan) {
        return new PlanCreateResponse(plan.getId(), plan.getCreatedAt());
    }
}
```

- 불변이고, 생성자·접근자·`equals`·`toString`이 자동으로 생긴다. Lombok이 필요 없다.
- 필드가 5개 이상이고 일부만 채우는 응답은 record에 `@Builder`를 붙여도 된다(`java-style.md` 5번).
- **선행 조건**: auth 요청 DTO를 record로 바꾸기 전에 `LoggingAspect`의 인자 로깅을 없앤다(`logging.md` 3번). record의 `toString()`은 모든 필드를 출력한다.
- BReady 현황: 요청 DTO 18개 중 10개가 `@Getter @NoArgsConstructor` 클래스(예: `plan/dto/PlanCreateRequest.java:10`), 응답은 `@Builder` 클래스와 record가 섞여 있다.

## 2. 이름 [빌드]

- `{Resource}{Action}Request` / `{Resource}{Action}Response` (BReady 다수 관례).
  - 예: `PlanCreateRequest`, `PlanCategoryOrderUpdateRequest`, `PlaceCandidateDeleteResponse`.
  - BReady 현황: `SignupRequest`, `LoginRequest`, `UpdateNicknameRequest`, `UpdateBioRequest`, `RefreshRequest`가 예외다. 고칠 때 맞춘다(외부 API 계약은 JSON 필드라 클래스 이름 변경은 계약 변경이 아니다).
- 조회 응답의 하위 항목은 응답 record 안에 **중첩 record**로 둔다.
  ```java
  public record PlanDetailResponse(PlanSummary plan, List<CategoryItem> categories) {
      public record CategoryItem(Long planCategoryId, PlaceCategoryType categoryType, ...) {}
  }
  ```
- 새 코드에 `Dto` 접미사를 쓰지 않는다(`PlanDetailCategoryDto` → `PlanDetailResponse.CategoryItem`).
- 외부 API 응답을 받는 타입은 `{Provider}{Resource}Response`(`KakaoTokenResponse`)로 두되 `external`/`client` 패키지에 둔다.

## 3. 요청 검증

- 형식 검증 어노테이션을 record 컴포넌트에 붙인다. 메시지는 사용자가 읽을 한국어 문장.
- 중첩 객체·리스트에는 `@Valid`를 붙여야 내부까지 검증된다.
- 숫자 ID는 `@NotNull @Positive`.
- 여러 필드를 같이 봐야 하는 형식 검증(위도·경도 짝)은 record 안에 `@AssertTrue` 메서드로:
  ```java
  @AssertTrue(message = "위도와 경도는 함께 입력해야 합니다.")
  private boolean isCoordinatePaired() {
      return (latitude == null) == (longitude == null);
  }
  ```

## 4. 변환 위치

| 방향 | 방법 |
|---|---|
| 엔티티 → 응답 | **응답 record의 `static from(Entity)`**. 여러 엔티티를 합치면 `from(a, b)` 또는 `of(...)` |
| 요청 → 엔티티 | 서비스가 엔티티의 정적 팩토리를 부른다: `Plan.create(userId, request.title(), ...)`. 요청 DTO가 엔티티를 만들지 않는다 |

- 의존 방향은 `dto → domain` 한쪽뿐이다. 엔티티는 DTO를 모른다.
- 서비스에서 응답을 빌더로 조립하지 않는다.
  - BReady 현황: `PlanService`는 응답 조립에 약 80줄을 쓴다(`plan/service/PlanService.java:46` 외).
- 엔티티를 응답에 그대로 넣지 않는다. 응답의 하위 객체도 record다.

## 5. 민감 필드

- 비밀번호·토큰·인가 코드를 담는 record는 `toString()`을 재정의해서 값을 가린다. 로그 정책과 별개로 두는 이중 방어다. [린트]
  ```java
  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
      @Override
      public String toString() {
          return "LoginRequest[email=" + email + ", password=****]";
      }
  }
  ```
- 응답에 비밀번호 해시, 내부 키, 다른 사용자의 이메일을 넣지 않는다.

## 6. 직렬화

- 날짜는 `LocalDate`·`LocalDateTime`을 그대로 쓴다(Jackson ISO-8601). 문자열로 미리 포맷하지 않는다.
  - BReady 현황: `SignupResponse.createdAt`, `UserProfileDto.joinedAt`이 서비스에서 문자열로 포맷된다(UTC와 KST가 섞여 있다).
- enum은 이름 그대로 직렬화한다. 화면용 라벨이 필요하면 별도 필드(`label`)로.