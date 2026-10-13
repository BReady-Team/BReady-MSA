# 예외 · ErrorCase

## 1. 구조 (BReady 뼈대 유지)

```
global/exception/ApplicationException   ← 비즈니스 예외는 이것 하나
global/exception/ErrorCase              ← 인터페이스: 상태 · 코드 · 메시지
global/exception/CommonErrorCase        ← (아직 없다) 도메인과 무관한 공통 오류. 지금은 핸들러가 숫자 코드와 문구를 직접 쓴다. 에러 코드 형식을 바꾸는 조각에서 만든다
global/exception/GlobalExceptionHandler ← 예외 → CommonResponse 변환은 여기서만
{module}/exception/{Domain}ErrorCase    ← 모듈마다 자기 enum
```

- 예외 클래스를 새로 만들지 않는다. 새 오류는 해당 모듈의 ErrorCase enum에 한 줄 추가가 전부다.

## 2. 코드 형식 `{DOMAIN}_{NNN}`

```java
@Getter
@RequiredArgsConstructor
public enum PlanErrorCase implements ErrorCase {

    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_001", "플랜을 찾을 수 없습니다."),
    PLAN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PLAN_002", "플랜에 대한 접근 권한이 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
```

- 접두어는 모듈 기준으로 하나씩: `COMMON` `AUTH` `USER` `PLAN` `CATEGORY` `PLACE` `TRIGGER` `DECISION` `SWITCH` `RECO` `STATS` `FILE`.
- 번호는 enum 안에서 001부터 순서대로. 지운 번호는 재사용하지 않는다.
- 코드는 전역에서 유일해야 한다. [빌드]
- 메시지는 사용자에게 보여도 되는 한국어 문장. 내부 정보(SQL, 클래스 이름, 다른 사용자 정보)를 넣지 않는다.
- BReady 현황: 정수 코드이고 `4001`·`4002`·`4104`·`4105`가 모듈 사이에 중복된다(`plan/exception/PlanErrorCase.java:13`, `user/exception/UserErrorCase.java:15`, 핸들러의 검증 오류 `global/exception/GlobalExceptionHandler.java:47`). 전환은 응답 계약(`errorCode` 타입) 변경이므로 **ADR과 함께 별도 조각**에서 한다. 그 전까지 새 ErrorCase는 기존 enum 형식을 따르되 중복 번호를 만들지 않는다.

## 3. 던지는 법 [린트]

```java
throw ApplicationException.from(PlanErrorCase.PLAN_NOT_FOUND);
.orElseThrow(() -> ApplicationException.from(PlanErrorCase.PLAN_NOT_FOUND));
```

- `new ApplicationException(...)` 대신 `ApplicationException.from(...)`만 쓴다.
  - BReady 현황: `new` 89회, `from` 36회.
- 원인 예외를 보존해야 하면(외부 연동 실패 등) `ApplicationException.from(errorCase, cause)`. (현재는 생성자만 있다. 처음 필요해지는 조각에서 정적 팩토리를 추가한다.)
- 도메인 규칙 위반에 `IllegalArgumentException`·`IllegalStateException`·`RuntimeException`을 던지지 않는다(`entity.md` 2번).

## 4. HTTP 상태 고르는 법

| 상태 | 언제 |
|---|---|
| 400 | 요청 형식은 맞지만 값이 규칙에 어긋남 (순서 중복, 범위 밖) |
| 401 | 인증 안 됨, 토큰 무효 |
| 403 | 인증됐지만 남의 자원 |
| 404 | 대상이 없음 (soft delete 포함) |
| 409 | 현재 상태와 충돌 (중복 생성, 이미 결정됨, 이미 전환됨) |
| 502 | 외부 API(카카오·네이버·OpenAI)가 오류를 돌려줌 |
| 503 | 외부 API·하위 서비스에 닿지 못함 (타임아웃, 연결 실패) |
| 500 | 예상하지 못한 오류. **ErrorCase로 500을 만들지 않는다** |

## 5. GlobalExceptionHandler

- 응답 본문은 항상 `CommonResponse.error(errorCase)`.
- **도메인 모듈의 ErrorCase를 import하지 않는다.** [빌드]
  - 모듈이 공통 예외를 자기 에러로 알리고 싶으면 `global`이 정한 인터페이스를 그 모듈이 구현한다. 예: 빠진 multipart 파트는 `global.exception.MissingPartErrorCaseProvider`를 `s3`의 `S3MissingPartErrorCaseProvider`가 구현한다. 핸들러는 인터페이스만 알고, 의존 방향은 모듈 → global 하나다.
  - `@ExceptionHandler` 메서드 안에서 예외를 던지면 다른 핸들러가 받지 않는다. 스프링 기본 처리로 넘어가 본문 없는 응답이 나간다. 에러 응답은 핸들러가 직접 만들어 반환한다.
  - BReady 현황: `handleTypeMismatch`는 받은 예외를 다시 던져 본문 없는 400을 낸다(`global/exception/GlobalExceptionHandler.java`). 잘못된 `period`에 `StatsErrorCase.INVALID_PERIOD`를 주려던 분기가 있었지만 위 이유로 동작하지 않아 지웠다.
- 핸들러를 추가하는 일은 드물다. 새 오류는 ErrorCase 한 줄로 해결되는지 먼저 본다.
- 예상 못 한 예외(500)는 스택트레이스를 ERROR로 남기고, 응답에는 일반 메시지만 준다.

## 6. 잡는 법

- `catch (Exception e)`는 **외부 연동 경계에서만** 쓴다(HTTP 클라이언트, AI 호출). 잡으면 로그를 남기고 ErrorCase로 번역하거나, 대체 동작을 하는 이유를 주석으로 남긴다.
  - BReady 좋은 예: `OpenAiRerankService`는 AI 실패 시 빈 결과를 돌려주고 규칙 기반 결과를 쓴다(`recommendation/ai/OpenAiRerankService.java:91`). 실패해도 추천은 된다는 의도된 대체 동작이다.
- 잡은 뒤 같은 예외를 다시 던지기만 하는 `catch (ApplicationException e) { throw e; }`는 지운다.
- **에러 코드를 비교해서 흐름을 바꾸지 않는다.** 정상적으로 일어날 수 있는 결과(검색 결과 없음)는 예외가 아니라 반환값(빈 리스트, `Optional`)으로 표현한다.
  - 좋은 예: `PlaceSearchApi.searchPlaces`는 결과가 없으면 빈 목록을 돌려준다. 전에는 `PlaceSearchService`가 `PLACE_NOT_FOUND`를 던지고 추천 어댑터가 그 코드를 비교해 빈 목록으로 바꿨다.