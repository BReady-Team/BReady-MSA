# 서비스 · 트랜잭션

## 1. 서비스가 하는 일

서비스는 **조율자**다. 규칙 판단은 엔티티(`entity.md` 3·4번), 변환은 DTO(`dto.md` 4번)가 한다.

```java
@Transactional
public PlanUpdateResponse updatePlan(Long userId, Long planId, PlanUpdateRequest request) {
    Plan plan = getActivePlan(planId);                              // 1. 조회
    plan.validateOwner(userId);                                     // 2. 규칙 검증 (엔티티)
    plan.update(request.title(), request.planDate(), request.region()); // 3. 변경 (엔티티)
    return PlanUpdateResponse.from(plan);                           // 4. 변환 (DTO)
}
```

- 한 public 메서드 = 한 유스케이스. 다른 public 메서드를 내부에서 부르지 않는다(8번 자기 호출 함정)
- 반복되는 조회+예외는 private 헬퍼로: `getActivePlan(id)`, `getOwnedPlan(userId, id)`
- 서비스 시그니처에 웹 타입(`HttpServletRequest`, `ResponseEntity`)을 쓰지 않는다. `MultipartFile`은 파일 업로드를 다루는 서비스 메서드에서만 허용한다.
- 서비스 인터페이스(`XxxUsecase`)를 만들지 않는다. 구현이 하나뿐인 인터페이스는 YAGNI다. 다른 모듈이 부르는 **공개 API**는 2단계에서 인터페이스로 만든다(`msa-boundary.md` 2번)

## 2. 트랜잭션 선언 [빌드]

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)      // 클래스 기본값: 읽기 전용
public class PlanService {

    @Transactional                   // 쓰기 메서드에만 덮어쓴다
    public PlanCreateResponse createPlan(...) { ... }

    public PlanDetailResponse getPlanDetail(...) { ... }   // readOnly 상속
}
```

- 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`
- 클래스와 메서드에 같은 설정을 두 번 쓰지 않는다.
  - BReady 현황: `PlanStatsUpdater`는 클래스와 메서드 모두 `@Transactional`(`stats/service/PlanStatsUpdater.java:20,27`). `PlanService`는 클래스 선언 없이 메서드마다 붙인다.
- `readOnly = true`의 효과: Hibernate가 변경 감지(dirty checking)를 건너뛰고, 읽기 전용 커넥션 라우팅이 가능해진다. 읽기 메서드에서 엔티티를 바꿔도 반영되지 않는다는 점을 안다.
- 컨트롤러·리포지토리에 `@Transactional`을 두지 않는다.

## 3. 외부 I/O는 트랜잭션 밖으로

HTTP 호출·S3·OpenAI·메시지 발행처럼 **DB가 아닌 자원**을 트랜잭션 안에서 다루지 않는다.
- 트랜잭션 안에서 느린 외부 호출을 하면 DB 커넥션을 그동안 붙잡는다(커넥션 풀 고갈)
- 외부 호출은 성공했는데 DB 커밋이 실패하면 되돌릴 수 없다(S3에 고아 파일)

**BReady 좋은 예 — 외부 호출과 DB 작업을 빈으로 분리**
```
KakaoAuthService.login()              ← 트랜잭션 없음
  ├─ exchangeKakaoToken(code)         ← 외부 HTTP (auth/service/KakaoAuthService.java:29)
  ├─ fetchKakaoUserInfo(token)        ← 외부 HTTP
  └─ kakaoAuthTransactionHandler.processKakaoLogin(userInfo)   ← @Transactional, DB만
```

**BReady 나쁜 예**: `UserService.updateProfileImage`는 `@Transactional` 안에서 S3 업로드와 삭제를 한다(`user/service/UserService.java:80`)

- 외부 호출 결과가 필요한 쓰기는 위처럼 "외부 호출(트랜잭션 없음) → 트랜잭션 빈 호출" 구조로 만든다.
- 커밋 후에 해야 하는 외부 작업은 커밋 이후 단계로 미룬다

## 4. 동시성

**검증과 변경은 같은 락 안에서 한다.** 락을 잡기 전에 읽은 값으로 판단하면, 락을 기다리는 사이에 다른 트랜잭션이 그 값을 바꿀 수 있다.
- BReady 현황(경쟁 상태 R7): `SwitchService.executeSwitch`는 전환 대상 후보가 살아 있는지 **락 없이** 확인한 뒤 `category_states` 락을 잡는다. 그 사이 후보 삭제가 커밋되면 삭제된 후보가 대표로 지정된다. → 락을 먼저 잡고 검증한다.

**중복 생성은 DB 유니크 제약으로 막고 예외를 번역한다** 
```java
try {
    saved = placeCandidateRepository.saveAndFlush(PlaceCandidate.create(category, place));
} catch (DataIntegrityViolationException e) {
    throw ApplicationException.from(PlaceErrorCase.DUPLICATE_PLACE_CANDIDATE);
}
```
(`place/service/PlaceCandidateService.java:49`) — "먼저 조회해서 없으면 저장"은 동시 요청 두 개가 둘 다 "없음"을 본다. 유니크 제약이 최종 판정자다. `saveAndFlush`여야 예외가 이 try 안에서 터진다.

**락 선택 기준**
| 상황 | 방법 |
|---|---|
| 같은 행을 동시에 수정, 충돌이 잦음 | 비관적 락 `@Lock(PESSIMISTIC_WRITE)` + 메서드 이름 `...ForUpdate` (BReady 관례) |
| 충돌이 드묾 | 낙관적 락 `@Version` + 충돌 시 ErrorCase(409) 또는 재시도 |
| 수량 차감·카운터 | 원자적 UPDATE(`repository-query.md` 8번) |

- 락은 항상 같은 순서로 잡는다(예: plan → category → state). 순서가 엇갈리면 데드락이 난다.

**`REQUIRES_NEW`는 이유가 있을 때만**
- 바깥 트랜잭션이 롤백돼도 남아야 하는 작업에만 쓴다(예: 공용 장소 카탈로그 `getOrCreate` — `place/service/PlacePersistenceService.java:22`).
- 같은 클래스 안에서 부르면 프록시를 거치지 않아 적용되지 않는다(8번). 반드시 다른 빈으로.

## 5. Clock 주입

- 현재 시각은 `Clock` 빈에서 얻는다: `LocalDateTime.now(clock)`
- 엔티티에는 시각을 파라미터로 넘긴다(`entity.md` 8번)
- 테스트에서는 `Clock.fixed(...)`로 고정한다(`testing.md` 8번)

## 6. 이벤트

- **이벤트 클래스는 발행하는 모듈이 소유한다.** 소비자 패키지에 두지 않는다. [빌드]
  - BReady 현황: `TriggerCreatedEvent`·`SwitchLogCreatedEvent`가 `stats.event`에 있고 trigger가 import한다(`trigger/service/SwitchService.java:14`) → trigger ⇄ stats 순환 의존.
- 이벤트는 사실(과거형) 이름: `SwitchExecutedEvent`, `TriggerOccurredEvent`. 필요한 ID와 값만 담는다. 엔티티를 담지 않는다.
- `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`의 의미를 알고 쓴다:
  - 커밋된 뒤에만 실행된다(롤백된 변경으로 통계가 바뀌지 않는다)
  - **메모리에만 있다.** 커밋 직후 JVM이 죽거나 리스너가 실패하면 영원히 사라진다(Outbox가 없는 이중 쓰기)
  - 리스너 안의 DB 쓰기는 새 트랜잭션이 필요하다(`@Transactional(propagation = REQUIRES_NEW)` 또는 별도 빈)
- **잃으면 안 되는 부수 효과**에 새 `@Async` 리스너를 추가하지 않는다. 그런 곳은 5단계 Outbox의 대상으로 기록해 둔다.

## 7. 예외와 트랜잭션 롤백

- `ApplicationException`은 `RuntimeException`이라 기본적으로 롤백된다.
- 예외를 잡아서 삼키면 트랜잭션은 커밋된다. 잡았으면 다시 던지거나, 삼키는 이유를 주석으로 남긴다.
- 체크 예외를 던지는 코드를 쓰지 않는다.

## 8. 프록시 함정

`@Transactional`, `@Async`, `@Cacheable`은 **스프링 프록시를 거칠 때만** 동작한다.
- 같은 클래스 안의 메서드 호출(`this.foo()`)은 프록시를 거치지 않는다 → 어노테이션이 무시된다.
- `private` 메서드에 붙여도 무시된다. [린트]
- 그래서 트랜잭션 경계를 나누려면 빈을 나눈다(3번의 `KakaoAuthTransactionHandler` 패턴).
