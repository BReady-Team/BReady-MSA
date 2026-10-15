# 4단계 첫 서비스 추출 — Place Catalog

> 2026-10-15 시작. 이 단계의 결정은 단계가 끝날 때 ADR로 모은다.

## 이 단계의 목표

`place` 패키지(장소 저장·조회, 카카오 장소 검색)를 `place-catalog-service`로 떼어낸다. 클라이언트는 계속 Gateway 주소 하나만 안다.

| 조각 | 내용 | PR | 상태 |
|---|---|---|---|
| 4-1 | 장소 검색(DB 없음)을 `place-catalog-service`로. 모놀리식은 HTTP 어댑터로 부른다. 외부 입구는 모놀리식 그대로 | | 구현·검증 완료 |
| 4-2 | 장소 저장(`register`)을 HTTP로, 후보에 장소 스냅샷을 두어 조회(`getPlaces`)를 없앤다. DB는 공유하되 `places`를 쓰는 쪽은 Catalog뿐 | | 진행 중 |
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
| 4-2 후보 추가의 트랜잭션 구조 | 그대로(HTTP를 트랜잭션 안에서) / 트랜잭션 밖으로 빼기 / 후보 먼저 저장 후 장소 등록 | **트랜잭션 밖으로 빼기** | 소유권 확인(짧은 읽기) → `register` HTTP(트랜잭션 없음) → 별도 빈의 `@Transactional`에서 다시 확인 후 후보 저장. 네트워크를 기다리는 동안 DB 커넥션을 쥐지 않는다. 마지막 단계가 실패하면 쓰이지 않는 장소 한 줄만 남는다(지금의 `REQUIRES_NEW`와 같다, 무해). 후보를 먼저 저장하면 실패 시 없는 장소를 가리키는 후보가 남아 보상 로직이 필요하다 |
| 4-2 플랜 상세·추천의 장소 정보 | 매번 조회 / 조회 + 실패 시 장소 정보 없이 / 후보에 복사(스냅샷) | **후보에 복사** | 장소는 바뀌지 않는다(`Place`에 수정 메서드·API가 없고 `register`는 있으면 그대로 돌려준다). 복사의 단점인 "원본이 바뀌면 낡는다"가 없다. 플랜 상세·공유 링크·추천이 Catalog 장애와 분리되고 `getPlaces` API가 필요 없어진다. 대가는 `place_candidates` 컬럼 추가와 기존 행 백필 |
| 4-2 기존 행 백필 | 일회성 SQL / 마이그레이션 도구(Flyway) 지금 도입 | **일회성 SQL** | 배포 없는 로컬 프로젝트라 환경이 하나이고 지금 고통은 백필 한 번이다. `ddl-auto`가 NOT NULL 컬럼을 추가하면 기존 행을 `''`·`0`으로 조용히 채우므로 백필 후 빈 값 0건을 쿼리로 확인한다. Flyway는 외래키 삭제·새 DB 이관처럼 `ddl-auto`로 못 하는 일이 생기는 4-3에서 도입한다 |

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

### 4-1 장소 검색을 place-catalog-service로

| 위치 | 내용 |
|---|---|
| `place-catalog-service/` | 독립 Gradle 프로젝트(web, validation, actuator). 공통 코드(`global/`)는 모놀리식에서 필요한 것만 다시 썼다: `CommonResponse` record, `ApplicationException.from`, `GlobalExceptionHandler` |
| Catalog `place/` | `PlaceSearchInternalController`(`GET /internal/v1/places/search`, 결과 없으면 200 + 빈 목록), `PlaceSearchService`, `KakaoPlaceClient`(RestClient, 주소는 `kakao.base-url`), `PlaceCategoryType.searchQuery`, `PlaceErrorCase`(4103~4105 번호 유지, 카카오 오류·파싱 실패는 502, 카카오 연결 불가 503 `5032`) |
| Catalog `architecture/ArchitectureTest` | 레이어·코딩 규칙 12개. 기준선 없이 위반 0건 |
| 모놀리식 `place/external/` | `PlaceCatalogHttpClient`(`@HttpExchange`), `PlaceCatalogClientConfig`(이 클라이언트에만 타임아웃 연결 2초/읽기 8초), `PlaceSearchHttpAdapter`(`PlaceSearchApi` 구현, 오류 전달·503 번역), `RelayedErrorCase` |
| 모놀리식 삭제 | `PlaceSearchService`, `KakaoPlaceClient`, `KakaoPlaceDocument`, `place/dto/PlaceSearchResponse`, 에러 코드 4100·4103~4105 |
| `docker-compose-local.yml` | `place-catalog` 컨테이너(`app` 프로필, 호스트 포트 없음), 모놀리식 `PLACE_CATALOG_URI` |
| `.claude/hooks/stop-verify-gate.sh` | architecture 테스트가 없는 서비스는 구조 검사를 건너뛰었다고 알린다 |

## 검증 증거

### 4-1

- 테스트: Catalog 36개(ArchUnit 12 포함), 모놀리식 192개, 훅 161개 모두 통과. 모놀리식 래칫 기준선은 6줄 줄기만 했다(71→70, 41→40, 8→4).
- ArchUnit이 실제로 잡는지: `PlaceSearchService`에 `System.out`을 넣자 `no classes should access standard streams` 위반으로 실패, 되돌림.
- 실제 기동(`--profile app`, 카카오는 WireMock으로 대체), Gateway `:8000` 경유:

| 시나리오 | 응답 |
|---|---|
| 로그인 없이 검색 | 401 |
| 정상 검색 `keyword=성수` | 200, `kakao-111`(도로명 주소), `kakao-222`(도로명 없음 → 지번 주소) |
| 결과 없음 | 404 `4102` (모놀리식 컨트롤러가 빈 목록을 404로) |
| 카카오 401 | 502 `4103` (Catalog가 번역, 모놀리식이 그대로 전달) |
| 위도만 | 400 `4101` |
| 장소 추천 | 200, 지금 대표 `kakao-111`은 빼고 `kakao-222`, `distanceMeters: 62` |
| Gateway로 `/internal/v1/places/search` | 404 (Gateway는 모놀리식으로만 보낸다) |
| 호스트에서 `:8081` 직접 | 연결 거부 (포트를 열지 않음) |
| Catalog 정지 후 검색 | 503 `5031`, 2.03초(연결 타임아웃 2초) |
| 카카오가 6초 걸림 (리뷰 반영 후) | 503 `5032`, 5.18초. Catalog가 5초에 끊고 모놀리식(8초)이 그대로 전달 |
| 카카오가 HTML을 줌 (리뷰 반영 후) | 502 `4105` |
| Catalog 정지 후 플랜 목록 | 200 (무관한 기능은 영향 없음) |
| Catalog 재시작 후 검색 | 200 |

## 워크스루 요약

### 4-1

- 부르는 쪽(검색 컨트롤러, 추천)은 `PlaceSearchApi`만 안다. 구현이 메서드 호출(`PlaceSearchService`)에서 HTTP 어댑터로 바뀌었고 부르는 쪽 코드는 그대로다.
- 네트워크가 끼면서 생긴 실패 세 가지를 어댑터가 맡는다: 상대가 없음(503 `5031`), 오래 걸림(타임아웃), 상대의 오류 응답(본문을 읽어 상태·코드·문구를 그대로 전달).
- "없음 → 404"는 외부 입구인 모놀리식 컨트롤러의 판단이라 남겼다. Catalog 내부 API는 빈 목록을 돌려주고 추천은 그 빈 목록을 그대로 쓴다.
- 타임아웃은 합으로 본다. Catalog의 카카오 호출은 최악 7초(연결 2 + 읽기 5), 모놀리식이 Catalog를 기다리는 시간은 8초다. 거꾸로면 카카오 오류가 도착하기 전에 모놀리식이 `5031`(Catalog 연결 불가)을 내서 원인이 다르게 보고된다. 처음에는 연결 타임아웃(5초)을 합산하지 않아 최악 10초 > 7초였고, 리뷰를 보다 찾았다.
- 오류 분류(닿지 못함 → 503, 상대가 이상한 답 → 502)는 지금 한다. 재시도·차단·대체 응답 같은 장애 대응은 5단계 서킷브레이커에서 한다.

## 알려진 한계

- 4-1: 모놀리식 기준선의 Place·User 에러 코드 중복(4104·4105)은 사라졌지만, Catalog가 돌려주는 4104·4105를 모놀리식이 그대로 전달하므로 클라이언트가 받는 응답에서는 여전히 겹친다. 에러 코드 형식 전환(7단계, ADR 필요) 때 함께 푼다.
- 4-2 ①~③: 모놀리식 `PlaceCatalogService.register`의 동시 등록 경로는 실제 DB에서 실패한다(TROUBLESHOOTING T-011). ④에서 모놀리식 코드를 지우고 Catalog의 고친 코드만 남을 때까지 남는다.
