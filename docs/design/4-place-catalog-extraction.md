# 4단계 첫 서비스 추출 — Place Catalog

> 2026-10-15 시작. 이 단계의 결정은 단계가 끝날 때 ADR로 모은다.

## 이 단계의 목표

`place` 패키지(장소 저장·조회, 카카오 장소 검색)를 `place-catalog-service`로 떼어낸다. 클라이언트는 계속 Gateway 주소 하나만 안다.

| 조각 | 내용 | PR | 상태 |
|---|---|---|---|
| 4-1 | 장소 검색(DB 없음)을 `place-catalog-service`로. 모놀리식은 HTTP 어댑터로 부른다. 외부 입구는 모놀리식 그대로 | | 구현 중 |
| 4-2 | 장소 저장·조회(`register`·`getPlaces`)를 HTTP로. DB는 공유하되 `places`에 쓰는 쪽은 Catalog뿐 | | |
| 4-3 | Catalog 전용 DB 분리 + 데이터 이관 | | |
| 4-4 | 모놀리식에 남은 place 코드 삭제 | | |

## 왜 지금

3단계 Gateway의 라우트는 `Path=/**` → 모놀리식 하나다. 4단계는 입구 뒤에 두 번째 목적지를 만든다.

Place Catalog는 나가는 의존이 0이고 호출 방향이 모놀리식 → Catalog 한쪽뿐이다(ADR-0001). 2단계에서 공개 API(`place.api`)로 경계를 끊어 두어서 부르는 곳이 넷뿐이다.

| 부르는 곳 | 쓰는 API | 떼면 바뀌는 것 |
|---|---|---|
| `plan/service/PlaceCandidateService` 후보 추가 | `PlaceCatalogApi.register` | `@Transactional` 안에서 부른다. 원격이 되면 DB 커넥션을 쥔 채 네트워크를 기다린다 |
| `plan/service/PlanService` 플랜 상세 | `getPlaces(ids)` | Catalog가 죽으면 플랜 상세는? |
| `recommendation/service/PlaceRecommendationService` | `getPlaces` | 같은 문제 |
| `recommendation/adapter/RuleBasedRecommendationAdapter` | `PlaceSearchApi.searchPlaces` | DB 없음, 카카오만 |
| `GET /api/v1/places/search` (외부 공개) | `PlaceSearchController` | Gateway 라우트 |

장소 검색은 DB를 쓰지 않는다. 그래서 첫 조각에서 원격 호출과 라우팅만 따로 다룰 수 있다.

## 설계 (선택지 · 결정)

| 결정 | 선택지 | 결정 | 이유 |
|---|---|---|---|
| 조각 순서 | 한 번에 / 코드 먼저, 데이터는 나중 / 데이터 먼저(모놀리식 안에서 데이터소스 분리) | **코드 먼저, 데이터는 나중** | 한 조각에 새 문제가 하나씩만 생긴다(4-1 원격 호출·라우팅, 4-3 데이터). 4-2~4-3 사이 DB를 공유하지만 `places`에 쓰는 쪽은 처음부터 Catalog뿐이라 소유권은 Catalog에 있다 |
| 모놀리식 → Catalog HTTP 클라이언트 | `RestClient` + `@HttpExchange` / OpenFeign / `WebClient` + `block()` | **`RestClient` + `@HttpExchange`** | 의존성 추가 없음, 동기 코드와 맞음. 공개 API 인터페이스는 그대로 두고 구현만 HTTP 어댑터로 바꾸면 부르는 쪽은 바뀌지 않는다. OpenFeign은 유지보수 모드 |
| 공통 코드(`CommonResponse`, `ErrorCase` 등) | 필요한 것만 복사 / 공유 라이브러리 | **복사** | 서비스 사이에서 지킬 것은 클래스가 아니라 응답 JSON 모양이다. 공유 라이브러리는 한 줄 고쳐도 모든 서비스를 다시 빌드해야 하고 도메인 코드가 흘러 들어가기 쉽다(분산 모놀리스) |
| 4-1 장소 검색의 로그인 확인 | 모놀리식이 계속 입구 / Catalog가 JWT 직접 검증 / Gateway JWT 검증을 앞당김 | **모놀리식이 계속 입구** | `GET /api/v1/places/search`는 로그인이 필요하다(`SecurityConfig`의 `anyRequest().authenticated()`). Catalog에는 인증이 없어서 Gateway가 바로 보내면 누구나 검색할 수 있게 된다. 인증은 6단계 전에 손대지 않는다. 그래서 외부 요청은 모놀리식이 받아 로그인을 확인하고 Catalog를 내부 호출한다. 대가: 한 번 더 거친다(Gateway → 모놀리식 → Catalog). Gateway가 Catalog로 바로 보내는 라우트는 6단계에서 Gateway가 JWT를 확인하게 될 때 만든다 |
| 4-1 Catalog의 코드 규칙 도구 | Spotless만 / Spotless + ArchUnit | **Spotless + ArchUnit** | Catalog에는 도메인 코드(4-2부터 엔티티·리포지토리)가 있다. Stop 훅도 ArchUnit을 건너뛸 때 그 사실을 출력하게 고친다 |

## 흐름 (before → after)

```
before (4-1)
브라우저 ─GET /api/v1/places/search─▶ Gateway ─(/**)─▶ monolith
                                                       └ PlaceSearchController → PlaceSearchService → 카카오
추천: monolith 안 RuleBasedRecommendationAdapter ─(메서드 호출)─▶ PlaceSearchService → 카카오

after (4-1)
브라우저 ─GET /api/v1/places/search─▶ Gateway ─(/**)─▶ monolith (JWT 확인)
                                                       └ PlaceSearchController
                                                         ─(PlaceSearchApi)─▶ HTTP 어댑터
                                                         ─HTTP GET (타임아웃)─▶ place-catalog-service → 카카오
추천: monolith 안 RuleBasedRecommendationAdapter
        ─(PlaceSearchApi 인터페이스 그대로)─▶ 같은 HTTP 어댑터 ─▶ place-catalog-service
        트랜잭션 없음, DB 없음
```

Catalog는 호스트에 포트를 열지 않고 Gateway 라우트도 없다. 모놀리식만 컨테이너 네트워크로 부른다.

## 구현 파일

## 검증 증거

## 워크스루 요약

## 알려진 한계