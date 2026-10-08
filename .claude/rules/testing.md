# 테스트

BReady에는 테스트 기준이 없었으므로 commerce-msa 테스트 규칙을 기반으로 하고, 응답 검증은 BReady `CommonResponse`에 맞춘다.

## 1. 테스트 계층 — 무엇을 어디서 검증하나

| 계층 | 도구 | 검증 대상 | 스프링 |
|---|---|---|---|
| **도메인 단위** | JUnit + AssertJ | 엔티티·enum·값 객체의 규칙(불변식, 상태 전이, 소유권) | 없음 |
| **서비스 단위** | Mockito(`@ExtendWith(MockitoExtension.class)`) | 조율 흐름: 무엇을 조회하고, 무엇을 저장하고, 어떤 예외를 던지나 | 없음 |
| **컨트롤러 슬라이스** | `@WebMvcTest(controllers = X.class)` + `@MockitoBean` | HTTP 계약: 상태 코드, 요청 검증, 응답 JSON, 에러 코드 | 웹 계층만 |
| **리포지토리 슬라이스** | `@DataJpaTest` + Testcontainers MySQL | 쿼리 결과, fetch join, soft delete 필터, 락, native query | JPA만 |
| **통합(안전망)** | `@SpringBootTest` + Testcontainers(MySQL·Redis) + MockMvc | 핵심 사용자 흐름 전체, 트랜잭션 경계, 동시성 | 전부 |

- 규칙은 가장 아래 계층(도메인 단위)에서 검증한다. 같은 규칙을 위 계층에서 다시 검증하지 않는다.
- **통합 테스트가 안전망이다.** 옛 코드를 정리하거나 모듈을 떼기 전에, 그 흐름을 지키는 통합 테스트가 있어야 한다(`/refactor-legacy`).

## 2. 이름과 구조

```java
@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Test
    @DisplayName("성공 - 소유자가 플랜을 수정하면 제목·날짜·지역이 바뀐다")
    void updatePlan_success() {
        // given
        Plan plan = PlanFixture.activePlan(OWNER_ID);
        given(planRepository.findByIdAndDeletedAtIsNull(PLAN_ID)).willReturn(Optional.of(plan));

        // when
        PlanUpdateResponse response = planService.updatePlan(OWNER_ID, PLAN_ID, PlanFixture.updateRequest());

        // then
        assertThat(plan.getTitle()).isEqualTo("수정된 제목");
        assertThat(response.planId()).isEqualTo(plan.getId());
    }

    @Test
    @DisplayName("실패 - 소유자가 아니면 PLAN_ACCESS_DENIED")
    void updatePlan_notOwner() { ... }
}
```

- 클래스: `{대상}Test`. 메서드: `{메서드}_{상황}`.
- `@DisplayName`: `"성공 - …"` / `"실패 - …"` + 기대 결과. 실패는 ErrorCase 이름까지.
- 테스트 하나에 행위 하나. 성공과 실패를 한 테스트에 섞지 않는다.
- `// given` `// when` `// then` 주석으로 구역을 나눈다.

## 3. Mockito는 BDD 스타일 [린트]

- 준비: `given(...).willReturn(...)` / `willThrow(...)`. `when(...).thenReturn(...)` 금지.
- 확인: `then(repository).should().save(any())`, `then(eventPublisher).shouldHaveNoInteractions()`.
- `@MockitoSettings(strictness = LENIENT)`를 쓰지 않는다. 쓰지 않는 stub은 지운다.

## 4. 단언은 AssertJ만 [린트]

- `import static org.assertj.core.api.Assertions.*` 계열만. `AssertionsForClassTypes`, JUnit `assertEquals`/`assertTrue` 금지.
  - BReady 현황: `Assertions`와 `AssertionsForClassTypes`가 섞여 있다.
- 예외: `assertThatThrownBy(() -> ...).isInstanceOf(ApplicationException.class).extracting("errorCase").isEqualTo(PlanErrorCase.PLAN_ACCESS_DENIED)`.

## 5. Fixture — 엔티티를 mock하지 않는다

- **엔티티는 mock하지 않고 진짜 객체를 fixture로 만든다.** mock 엔티티는 규칙(불변식·소유권)을 우회해서 "실제로는 불가능한 상태"를 테스트하게 만든다. [린트]
  - BReady 현황: 서비스 테스트에서 `mock(Plan.class)` 같은 엔티티 mock이 많다(`AuthServiceTest` 14회, `PlaceCandidateServiceTest` 12회, `SwitchServiceTest` 10회).
- 위치: `src/test/java/com/bready/server/{module}/fixture/{Entity}Fixture.java`.
- 정적 메서드만. `defaultXxx()`/상황 이름 메서드(`activePlan(ownerId)`, `deletedPlan()`)를 둔다.
- ID가 필요하면 `ReflectionTestUtils.setField(entity, "id", 1L)`를 fixture 안에서만 쓴다.
- 요청 DTO(record)는 생성자 그대로 fixture에서 만든다.

## 6. 컨트롤러 슬라이스

```java
@WebMvcTest(controllers = PlanController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class PlanControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PlanService planService;

    @Test
    @DisplayName("실패 - 제목이 비면 400")
    void createPlan_blankTitle() throws Exception {
        mockMvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","planDate":"2026-10-08","region":"서울"}
                                """))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").exists());
    }
}
```

- `@WebMvcTest(controllers = …)`로 대상을 명시한다. `@MockBean`이 아니라 `@MockitoBean`.
- `MockMvcBuilders.standaloneSetup`을 새로 쓰지 않는다(실제 설정·검증·핸들러 구성과 달라진다).
- JSON 본문은 텍스트 블록(`"""`). 모든 요청에 `.andDo(print())`.
- 응답 검증: 성공은 `$.message == "success"`와 `$.data.*`, 실패는 HTTP 상태와 `$.errorCode`.
- `@CurrentUser`는 테스트용 인자 리졸버로 고정 사용자를 주입한다(기존 `TestConfig` 활용).

## 7. Testcontainers

- 리포지토리 슬라이스와 통합 테스트는 **MySQL 8.0 컨테이너**로 돌린다. H2는 native query·락·MySQL 문법을 검증하지 못한다.
- 컨테이너는 테스트 전체에서 한 번만 띄운다(공통 베이스 클래스 + `static` 컨테이너 + `@ServiceConnection`).
- 함정: Docker 29에서는 `build.gradle` test 태스크에 `systemProperty 'api.version', '1.44'` (CLAUDE.md 9번).
- 도입은 0단계(안전망)에서 한다. 그 전에는 기존 H2 테스트를 건드리지 않는다.

## 8. 시간 고정

- 시간에 의존하는 로직은 `Clock.fixed(...)`를 주입해서 검증한다(`service-transaction.md` 5번).
- `Thread.sleep`으로 시간을 흘려보내지 않는다.

## 9. 동시성 테스트

- 경쟁 상태(R7 같은)는 통합 테스트에서 `ExecutorService` + `CountDownLatch`로 같은 시점에 요청을 보내 재현한다.
- 재현하지 못한 동시성 버그를 "고쳤다"고 하지 않는다.

## 10. 금지 목록

- ❌ 엔티티 mock · ❌ `@MockBean` · ❌ `when().thenReturn()` · ❌ JUnit 단언 · ❌ `AssertionsForClassTypes`
- ❌ 슬라이스로 충분한데 `@SpringBootTest` · ❌ 테스트 하나에 성공·실패 혼합 · ❌ 테스트끼리 데이터 공유(순서 의존)
- ❌ 실제 외부 API(카카오·OpenAI·S3) 호출. 외부 연동은 클라이언트를 mock하거나 WireMock 같은 가짜 서버로.