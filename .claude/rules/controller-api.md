# 컨트롤러 · API

## 1. 컨트롤러는 위임만 한다 [빌드]

컨트롤러가 하는 일은 네 가지뿐이다: **HTTP 매핑 · 형식 검증 · 인증 사용자 주입 · 서비스 호출 결과를 `CommonResponse`로 감싸기**.

- 비즈니스 판단, 조회 결과에 따른 분기, 예외 던지기를 하지 않는다.
  - BReady 현황: `PlaceSearchController`가 위도/경도 짝 검증과 "결과 없음" 예외를 직접 던진다(`place/controller/PlaceSearchController.java:98,106`). 후자는 서비스가 이미 같은 예외를 던져서 중복이다.
- 리포지토리를 주입하지 않는다. 서비스는 하나만 주입하는 것을 기본으로 한다.
- 한 엔드포인트는 서비스 메서드 하나를 부른다.

## 2. Swagger는 `{Domain}Api` 인터페이스에 둔다

```java
@Tag(name = "Plan", description = "플랜 생성·조회·수정·삭제·공유")
public interface PlanApi {

    @Operation(summary = "플랜 생성", description = "인증된 사용자가 새 플랜을 만든다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 오류 (COMMON_001)",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    CommonResponse<PlanCreateResponse> createPlan(@Parameter(hidden = true) Long userId, PlanCreateRequest request);
}
```
```java
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController implements PlanApi {

    private final PlanService planService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommonResponse<PlanCreateResponse> createPlan(
            @CurrentUser Long userId, @Valid @RequestBody PlanCreateRequest request) {
        return CommonResponse.success(planService.createPlan(userId, request));
    }
}
```

- 인터페이스: 문서(`@Tag`, `@Operation`, `@ApiResponses`, `@Parameter`)만. 스프링 매핑 어노테이션은 두지 않는다.
- 컨트롤러: 매핑(`@GetMapping` 등), `@ResponseStatus`, 바인딩·검증(`@Valid`, `@RequestBody`, `@PathVariable`, `@CurrentUser`)만.
- 같은 정보를 두 곳에 쓰지 않는다. 경로는 컨트롤러에만, 설명은 인터페이스에만.
- `@ApiResponses`에는 **그 엔드포인트가 실제로 던질 수 있는 ErrorCase의 상태 코드를 전부** 적고, 설명에 코드(`PLAN_001`)를 함께 적는다. [리뷰]
- 모든 public 엔드포인트는 `{Domain}Api`를 구현해야 한다. [빌드]
  - BReady 현황: 전부 인라인 방식. `PlanStatsController`는 Swagger 설명이 아예 없다.

## 3. URL과 HTTP 의미

- prefix는 `/api/v1/{복수형 리소스}`. 하위 리소스는 `/plans/{planId}/categories`.
- 동사를 URL에 넣지 않는다. 단, 리소스로 표현하기 어려운 명령은 하위 경로 명사로 표현한다.
  - BReady 관례 유지: `POST /plans/{planId}/share`, `POST /places/candidates/{id}/representative`, `POST /triggers/{decisionId}/switch`.
- 메서드 의미:

  | 메서드 | 용도 | 성공 상태 |
  |---|---|---|
  | `GET` | 조회. 부수 효과 없음 | 200 |
  | `POST` | 생성, 명령 | 생성 201 (`@ResponseStatus(HttpStatus.CREATED)`), 명령 200 |
  | `PATCH` | 부분 수정 | 200 |
  | `DELETE` | 삭제 (soft delete 포함) | 200 (삭제 결과를 본문으로 돌려주는 BReady 관례 유지) |

- 기존 URL은 바꾸지 않는다(외부 계약). 바꿔야 하면 ADR로 남긴다.

## 4. 검증 위치

| 위치 | 검증하는 것 | 예 |
|---|---|---|
| 컨트롤러 (Bean Validation) | 형식: 필수, 길이, 범위, 패턴 | `@NotBlank`, `@Positive`, `@Max(50)` |
| 엔티티 | 어디서 만들어도 깨지면 안 되는 불변식 | 소유자 일치, 상태 전이 |
| 서비스 | 여러 엔티티·저장소를 봐야 아는 규칙 | 같은 카테고리의 후보인가 |

- 클래스에 `@Validated`, 바디에 `@Valid @RequestBody`, 경로·쿼리 파라미터에 `@Positive` 등을 붙인다.
- 같은 검증을 두 계층에서 반복하지 않는다.

## 5. 인증 사용자

- 인증이 필요한 엔드포인트는 `@CurrentUser Long userId`로 받는다. `SecurityContextHolder`를 직접 쓰지 않는다.
- **사용자 데이터를 바꾸는 모든 엔드포인트는 `userId`를 받아 소유권을 검증한다.** [리뷰]
  - BReady 현황(IDOR): 장소 후보·트리거·결정·전환 엔드포인트에 `@CurrentUser`가 없다. 2단계에서 공개 API와 함께 고친다.
- 인증 방식(JWT 필터·`SecurityConfig`)은 6단계 전까지 바꾸지 않는다.

## 6. 응답

- 항상 `CommonResponse.success(data)`. 반환 타입은 `CommonResponse<T>`. `ResponseEntity`는 헤더를 직접 다뤄야 할 때만(파일 다운로드 등).
- 본문 없는 성공은 `CommonResponse<Void>` + `CommonResponse.success(null)` 대신 `CommonResponse.success()`.
- 에러 응답은 컨트롤러가 만들지 않는다. 예외를 던지면 `GlobalExceptionHandler`가 만든다(`exception.md` 5번).

## 7. 페이지네이션 · 정렬

- 요청: `page`(0부터), `size`(기본값과 최대값을 상수로), 정렬은 enum 파라미터.
- 응답: 목록 + `PageInfo`(BReady `PlanListResponse` 구조 유지). 스프링 `Page`를 그대로 직렬화하지 않는다.