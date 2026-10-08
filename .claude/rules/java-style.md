# Java 스타일

모든 Java 코드에 적용한다. 포맷(들여쓰기·줄바꿈·import 순서)은 palantir-java-format이 결정하므로 여기서 다루지 않는다.

## 1. 네이밍

- 클래스는 명사, 메서드는 동사로 시작한다. `create`, `find`, `get`, `validate`, `change`, `calculate`.
- `get`은 없으면 예외, `find`는 `Optional` 반환. 서비스 private 조회 헬퍼도 이 구분을 따른다.
  ```java
  private Plan getActivePlan(Long planId) {         // 없으면 PLAN_NOT_FOUND
      return planRepository.findByIdAndDeletedAtIsNull(planId)
              .orElseThrow(() -> ApplicationException.from(PlanErrorCase.PLAN_NOT_FOUND));
  }
  ```
- boolean은 `is`/`has`/`can`/`should`로 시작한다. `isSwitch()`, `isRepresentative(id)` (BReady 좋은 예).
- 축약하지 않는다. `pc`, `cs`, `v`, `t1` 같은 지역 변수 이름 금지(람다의 한 글자 파라미터는 한 줄짜리일 때만).
- 도메인 용어는 한 가지 영어 단어로 고정한다.

  | 한국어 | 영어 | 쓰지 않는 말 |
  |---|---|---|
  | 플랜 | Plan | Schedule |
  | 카테고리(플랜 안의 단계) | PlanCategory | Step |
  | 장소 후보 | PlaceCandidate | Option |
  | 대표 후보 | representative | main, selected |
  | 트리거(상황 발생) | Trigger | Event |
  | 결정(유지/전환) | Decision | Choice |
  | 전환 | Switch | Change |
  | 소유자 | owner | user(문맥상 소유자일 때) |

## 2. 메서드

- 한 메서드는 한 가지 일만 한다. **30줄을 넘으면 나눌 곳을 찾는다.** [리뷰]
- 중첩은 2단계까지. 예외 상황은 먼저 걸러낸다(가드 절).
- 파라미터는 4개까지. 넘으면 record로 묶는다.
  - 나쁜 예: `PlaceRecommendationPort.recommendPlaceCandidates(...)`는 파라미터가 8개다.
- boolean 플래그 파라미터 금지. 동작이 갈리면 메서드를 나눈다.
- 같은 private 메서드가 두 클래스 이상에 복붙되면 그 지식이 있어야 할 곳(enum·도메인 메서드·값 객체)으로 옮긴다.
  - 나쁜 예: `resolveStartAt(StatsPeriod)`가 stats 서비스 4곳에 복사돼 있다(`stats/service/StatsService.java:46` 외 3곳). → `StatsPeriod.startAt(LocalDateTime now)`로.

## 3. null과 Optional

- `Optional`은 **반환 타입으로만** 쓴다. 필드·파라미터·컬렉션 요소로 쓰지 않는다.
- 컬렉션은 null 대신 빈 컬렉션(`List.of()`)을 반환한다.
- `Optional.get()` 금지. `orElseThrow(() -> ApplicationException.from(...))`.
- `orElse(null)` 뒤에 `if (x == null)`로 분기하는 코드는 `Optional` 체인이나 별도 메서드로 정리한다.

## 4. Stream과 반복문

- 변환·필터·집계는 Stream. 부수 효과(저장, 상태 변경, 예외 던지기 반복)는 for 문.
- Stream 안에서 외부 변수를 바꾸지 않는다.
- 3단계를 넘는 Stream 체인 안의 람다가 여러 줄이면 메서드로 뽑는다.

## 5. Lombok 허용 목록 [린트]

| 허용 | 금지 |
|---|---|
| `@Getter` (엔티티·설정 클래스) | `@Setter`, `@Data`, `@Value` |
| `@RequiredArgsConstructor` (빈) | `@AllArgsConstructor` (엔티티) |
| `@NoArgsConstructor(access = AccessLevel.PROTECTED)` (엔티티) | 접근 수준 없는 `@NoArgsConstructor` (엔티티) |
| `@Slf4j` | `@ToString`, `@EqualsAndHashCode` (엔티티) |
| `@Builder` (필드 5개 이상인 응답 record만) | `@Builder` (엔티티) |

DTO는 record이므로 Lombok이 필요 없다(`dto.md` 1번).

## 6. 주석

- 주석은 **왜**를 쓴다. **무엇**은 코드가 말하게 한다.
  - 좋은 예: `// Place는 중복이면 재사용 (REQUIRES_NEW로 분리된 빈 호출)` — 이유가 보인다.
  - 나쁜 예: `// 캐시 조회`, `// 캐시 저장` — 메서드 이름과 같은 말이다.
- 주석 처리된 코드는 남기지 않는다. git이 기억한다.
- `TODO`에는 언제 할지를 붙인다: `// TODO(2단계): plan 공개 API로 교체`.
- 한국어 주석을 쓴다. 장식용 구분선(`// ─────`)은 쓰지 않는다.

## 7. import와 타입 [린트]

- 와일드카드 import 금지(`import java.util.*`). 예외: 테스트의 static import(`import static org.assertj.core.api.Assertions.*`).
  - import 순서와 안 쓰는 import 제거는 Spotless가 한다. 와일드카드를 펼쳐 주지는 않으므로 옛 코드(41개 파일)는 건드릴 때 정리한다.
- 코드 중간에 정규화된 이름을 쓰지 않는다.
  - 나쁜 예: `.block(java.time.Duration.ofSeconds(10))` (`place/external/KakaoPlaceClient.java:69`)
- `var`는 오른쪽에서 타입이 바로 보일 때만(`var plan = Plan.create(...)`).

## 8. 상수와 리터럴

- 의미 있는 숫자·문자열은 이름 붙은 상수나 enum으로. `DEFAULT_LIMIT`, `MAX_LIMIT` (BReady 좋은 예).
- 상태를 문자열로 들고 다니지 않는다.
  - 나쁜 예: `Plan.status`가 `String`이고 `"ACTIVE"`를 넣는다(`plan/domain/Plan.java:34`). → enum.
- 표시용 기본값을 서비스에 박지 않는다.
  - 나쁜 예: 닉네임 없으면 `"사용자"` (`plan/service/PlanService.java:129`)

## 9. 죽은 코드

- 빈 클래스, 호출되지 않는 서비스, 쓰이지 않는 메서드는 지운다.
  - 현재 예: `PlaceService`, `PlaceController`, `PlanStatsService`, `PlanStatsJoinService`.
- 실험 코드는 브랜치에 남긴다.

## 10. 불변성

- 필드는 기본적으로 `final`. 엔티티 필드는 JPA 때문에 예외.
- 컬렉션 필드를 그대로 반환하지 않는다. 필요하면 `List.copyOf(...)`나 `Collections.unmodifiableList(...)`.