# 로깅

## 1. 레벨

| 레벨 | 언제 | 예 |
|---|---|---|
| ERROR | 사람이 봐야 하는 실패. 예상 못 한 예외, 데이터 정합성이 깨졌을 가능성 | 통계 재계산 실패, 500 |
| WARN | 예상한 이상 상황을 처리했다 | 외부 API 4xx, AI 응답 파싱 실패 후 대체 동작 |
| INFO | 운영에서 의미 있는 사건. 요청마다 찍지 않는다 | 애플리케이션 시작, 배치 완료 |
| DEBUG | 개발 중 흐름 확인 | 검색어, 캐시 히트 여부 |

- 비즈니스 예외(`ApplicationException`, 4xx)는 WARN 한 줄이면 충분하다. 스택트레이스를 남기지 않는다.
- 요청마다 찍히는 INFO 로그를 새로 만들지 않는다.
  - BReady 현황: `PlaceSearchService`가 검색마다 `log.info("카카오 검색어 = …")` (`place/service/PlaceSearchService.java:38`).

## 2. 남기지 않는 것 [린트]

- 비밀번호, 토큰(access·refresh·OAuth 코드), API 키, 개인정보(이메일, 이름, 전화번호), 요청·응답 본문 전체.
- DTO·엔티티를 통째로 로그에 넣지 않는다(`log.info("{}", request)`). 필요한 ID만: `log.warn("플랜 접근 거부 planId={}, userId={}", planId, userId)`.

## 3. LoggingAspect

- 현재 `LoggingAspect`는 **모든 서비스 메서드의 인자를 `toString()`으로 INFO 로깅**한다(`global/aop/LoggingAspect.java:24,62`).
  - DTO가 record가 되는 순간 `LoginRequest[email=…, password=평문]`이 로그에 남는다.
  - 요청 하나에 서비스 호출 수만큼 START/END 로그가 쌓인다.
- 결정: **서비스 인자 로깅을 없앤다.** 0단계에서 안전망을 갖춘 뒤 제거하고, auth DTO의 record 전환은 그 다음에 한다.
- 요청 단위 추적은 나중에 traceId(MDC)로 한다(5~6단계 관측성).

## 4. 형식

- 문자열 연결이 아니라 `{}` 자리표시자: `log.warn("Kakao API 4xx status={}", status)`.
- 예외는 마지막 인자로 넘겨 스택트레이스를 남긴다: `log.error("PlanStats 갱신 실패 planId={}", planId, e)`.
- `e.getMessage()`만 남기면 원인이 사라진다. ERROR에는 예외 객체를 넘긴다.
- 메시지는 한국어로 "무엇이 실패했나 + 식별자 key=value".

## 5. 외부 연동 로그

- 대상, 결과(상태 코드), 걸린 시간만. 응답 본문은 DEBUG에서도 개인정보가 없을 때만.
- 재시도·대체 동작을 했다면 WARN으로 그 사실을 남긴다.