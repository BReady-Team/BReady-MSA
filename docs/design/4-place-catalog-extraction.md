# 4단계 첫 서비스 추출 — Place Catalog

> 2026-10-15 시작. 이 단계의 결정은 단계가 끝날 때 ADR로 모은다.

## 이 단계의 목표

`place` 패키지(장소 저장·조회, 카카오 장소 검색)를 `place-catalog-service`로 떼어낸다. 클라이언트는 계속 Gateway 주소 하나만 안다.

| 조각 | 내용 | PR | 상태 |
|---|---|---|---|
| 4-1 | 장소 검색(DB 없음)을 `place-catalog-service`로. 모놀리식은 HTTP 어댑터로 부른다. 외부 입구는 모놀리식 그대로 | | 구현·검증 완료 |
| 4-2 | 장소 저장(`register`)을 HTTP로, 후보에 장소 스냅샷을 두어 조회(`getPlaces`)를 없앤다. DB는 공유하되 `places`를 쓰는 쪽은 Catalog뿐 | | 구현·검증 완료 |
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
| 4-2 스냅샷 타입의 위치 | `@Embeddable` record / 클래스 | **`@Embeddable` record `PlaceSnapshot`** (Planning `plan/domain`) | 값 객체라 불변이 맞다. `PlaceInfo → PlaceSnapshot` 변환은 후보 서비스 한 곳에서 한다. 엔티티가 `place.api`를 모르게 하기 위해서다 |
| 4-2 추천이 기준 위치를 얻는 방법 | `getRepresentativePlaceId` + Catalog 조회 유지 / 새 record로 교체 | **`TriggerContextApi.getRepresentativePlace` + `RepresentativePlace` record** | 대표 후보의 스냅샷에서 바로 만든다. 추천이 Place Catalog에 의존하지 않게 되고 Planning 호출 한 번으로 끝난다 |
| 4-2 ④ 진행 순서 | HTTP 어댑터 먼저 / 트랜잭션 분리 먼저 | **트랜잭션 분리 먼저** | 어댑터를 먼저 바꾸면 그 사이 커밋에서 HTTP가 `@Transactional` 안에 들어간다. 분리를 먼저 하면 동작이 바뀌지 않는 구조 변경이라 기존 통합 테스트가 그대로 안전망이 된다 |
| 4-2 통합 테스트에서 Catalog 대신 | `PlaceCatalogHttpClient`를 가짜로 / `PlaceCatalogApi`를 mock / WireMock 같은 가짜 서버 | **HTTP 클라이언트를 가짜로**(`FakePlaceCatalog`) | 후보를 만드는 통합 테스트 7개가 모두 등록을 지나간다. 가장 바깥 경계만 바꿔 어댑터까지 통합 테스트가 지나가게 했다. "같은 외부 ID면 같은 id" 같은 Catalog 규칙은 Catalog 테스트가 지킨다. 직렬화·타임아웃은 어댑터 단위 테스트(`MockRestServiceServer`)와 실제 기동에서 본다 |
| 4-2 `places` 스키마의 주인 | Catalog `ddl-auto: update` / Flyway 지금 도입 / compose MySQL 초기화 SQL | **Catalog `ddl-auto: update`(임시)** | 모놀리식 `Place`를 지우면 테이블을 만드는 쪽이 사라진다(Catalog는 `validate`). 데이터의 주인이 스키마도 만든다. 4-3에서 Flyway로 바꾸고 `validate`로 되돌린다 |

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

```
before (4-2)
후보 추가  PlaceCandidateService.createCandidate  [@Transactional ── DB 커넥션 점유]
             검증 → PlaceCatalogService.register(REQUIRES_NEW, places INSERT) → 후보 저장 → 대표 보장
플랜 상세  PlanService → placeCatalogApi.getPlaces(ids) → places
추천      triggerContextApi.getRepresentativePlaceId → placeCatalogApi.getPlaces → 좌표

after (4-2)
후보 추가  PlaceCandidateService.createCandidate                      트랜잭션 없음
             ├ PlaceCandidateTransactionHandler.validateCreatable   읽기 트랜잭션
             ├ PlaceRegisterHttpAdapter ─HTTP POST /internal/v1/places─▶ place-catalog-service (places)
             └ PlaceCandidateTransactionHandler.create              쓰기 트랜잭션: 재확인 → 후보·스냅샷 → 대표 → 선택 로그
플랜 상세  PlanService → candidate.getPlaceSnapshot()                Catalog 호출 없음
추천      triggerContextApi.getRepresentativePlace → 스냅샷 좌표   Catalog에는 주변 검색만
```

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

### 4-2 장소 저장·조회를 HTTP로

| 묶음 | 위치 | 내용 |
|---|---|---|
| ① | Catalog `place/` | `Place` 엔티티(`uk_places_external_id`), `PlaceRepository`, `PlaceRegisterService`(트랜잭션 없음, T-011), `PlaceRegisterInternalController`(`POST /internal/v1/places`) |
| ② | 모놀리식 `plan/domain/PlaceSnapshot` | `@Embeddable` record. `place_candidates`에 `place_external_id`·`place_name`·`place_address`·`place_latitude`·`place_longitude`·`place_is_indoor` |
| ② | `monolith/db/backfill/2026-10-26-place-candidate-snapshot.sql` | 기존 후보 행의 스냅샷을 `places`에서 채우는 일회성 SQL. 빈 행만 고쳐서 다시 실행해도 0행 |
| ③-가 | `plan/service/PlanService` | 플랜 상세·공유 상세가 `candidate.getPlaceSnapshot()`을 읽는다 |
| ③-나 | `trigger/api/RepresentativePlace`, `TriggerContextApi.getRepresentativePlace`, `TriggerContextService` | 대표 후보의 스냅샷으로 기준 위치를 내준다. `PlaceRecommendationService`의 `PlaceCatalogApi` 의존과 `IllegalStateException` 삭제 |
| ③-다 | `place/api/PlaceCatalogApi` | `getPlaces` 삭제. 남은 공개 API는 `register` 하나 |
| ④-가 | `plan/service/PlaceCandidateService`, `PlaceCandidateTransactionHandler`, `global/config/ClockConfig` | 후보 추가를 "검증(읽기) → 등록(트랜잭션 없음) → 저장(쓰기)"으로 나눴다. 선택 로그 시각은 `Clock` 주입 |
| ④-나 | `place/external/PlaceRegisterHttpAdapter`, `PlaceCatalogHttpClient.registerPlace`, `CatalogErrorTranslator` | `PlaceCatalogApi` 구현을 HTTP로. 오류 번역은 검색·등록 어댑터가 함께 쓴다. 모놀리식 `PlaceCatalogService` 삭제 |
| ④-나 | 테스트 `support/IntegrationTestSupport`, `support/FakePlaceCatalog` | 통합 테스트 전체가 가짜 HTTP 클라이언트로 장소 등록에 답한다 |
| ④-다 | 모놀리식 삭제 | `Place`, `PlaceRepository`, `PlaceFixture`, 에러 코드 4108·4109. 모놀리식 `place` 패키지에는 `api`·`external`·`controller`·`exception`만 남았다 |
| ④-다 | Catalog `application.yml` | `ddl-auto: validate` → `update`(4-3에서 Flyway로) |
| ⑤ | `docker-compose-local.yml` | `place-catalog`에 `DB_URL`·`DB_PASSWORD`와 MySQL 대기(`depends_on`) 추가. 4-1 때는 DB를 쓰지 않아서 빠져 있었다 |

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

### 4-2

- 테스트: 모놀리식 199개, Catalog 53개, Gateway 8개, 훅 161개 모두 통과. 래칫 기준선은 줄기만 했다(도메인 표준 예외 10 → 8 → 5).
- 옛 코드에서 먼저 실패하는 것을 확인한 테스트:

| 테스트 | 옛 코드 결과 | 무엇을 증명하나 |
|---|---|---|
| `PlaceCatalogContractIntegrationTest.getPlanDetail_readsSnapshotWithoutCatalog` | 500 (`places` 행을 지우면) | 플랜 상세가 Catalog 없이 스냅샷을 읽는다. ④-다 이후에는 "조회 중 Catalog 호출 0회"로 검증 |
| `PlaceRecommendationContractIntegrationTest.recommendPlaces_readsSnapshotWithoutCatalog` | 500 `IllegalStateException` | 추천의 기준 위치가 스냅샷 좌표(37.5445, 127.0557)이고, Catalog에는 주변 검색만 한다 |
| `CandidateCreationTransactionIntegrationTest` | register 순간 트랜잭션 열림 | 장소 등록 동안 DB 커넥션을 쥐지 않는다 |
| `PlaceCatalogContractIntegrationTest.createCandidate_catalogUnavailable` | 200 (HTTP를 타지 않음) | 등록이 HTTP로 바뀌었고, Catalog에 닿지 못하면 503 `5031`이며 후보가 생기지 않는다 |

- 새 환경 재현(빈 MySQL 컨테이너): Catalog `validate`는 `Schema-validation: missing table [places]`로 기동 실패 → `update`로 바꾼 뒤 기동, `places`와 유니크 키 생성, 같은 외부 ID 두 번 등록 시 같은 id.
- 실제 기동(`--profile app`, 카카오 키는 더미), Gateway `:8000` 경유. 요청·응답 원문은 PR에 첨부했다.

| 시나리오 | Catalog 켜짐 | Catalog 꺼짐 |
|---|---|---|
| 후보 추가 | 200. `places`는 Catalog가, `place_candidates`(스냅샷 포함)는 모놀리식이 쓴 행 | 503 `5031`, 장소·후보 0건 |
| 플랜 상세 | 200, 첫 후보가 대표, 좌표는 스냅샷 | **200** (같은 응답) |
| 추천 | 502 `4103` (카카오 오류를 Catalog가 번역, 모놀리식이 그대로 전달) | 503 `5031` |

## 워크스루 요약

### 4-1

- 부르는 쪽(검색 컨트롤러, 추천)은 `PlaceSearchApi`만 안다. 구현이 메서드 호출(`PlaceSearchService`)에서 HTTP 어댑터로 바뀌었고 부르는 쪽 코드는 그대로다.
- 네트워크가 끼면서 생긴 실패 세 가지를 어댑터가 맡는다: 상대가 없음(503 `5031`), 오래 걸림(타임아웃), 상대의 오류 응답(본문을 읽어 상태·코드·문구를 그대로 전달).
- "없음 → 404"는 외부 입구인 모놀리식 컨트롤러의 판단이라 남겼다. Catalog 내부 API는 빈 목록을 돌려주고 추천은 그 빈 목록을 그대로 쓴다.
- 타임아웃은 합으로 본다. Catalog의 카카오 호출은 최악 7초(연결 2 + 읽기 5), 모놀리식이 Catalog를 기다리는 시간은 8초다. 거꾸로면 카카오 오류가 도착하기 전에 모놀리식이 `5031`(Catalog 연결 불가)을 내서 원인이 다르게 보고된다. 처음에는 연결 타임아웃(5초)을 합산하지 않아 최악 10초 > 7초였고, 리뷰를 보다 찾았다.
- 오류 분류(닿지 못함 → 503, 상대가 이상한 답 → 502)는 지금 한다. 재시도·차단·대체 응답 같은 장애 대응은 5단계 서킷브레이커에서 한다.

### 4-2

- 읽기는 복사로 끊었다. 장소는 바뀌지 않는 데이터라 후보에 스냅샷을 두면 낡지 않는다. 플랜 상세와 추천의 기준 위치는 Catalog를 부르지 않고, Catalog가 죽어도 플랜 상세는 200이다.
- 쓰기는 트랜잭션 밖으로 뺐다. `createCandidate`는 트랜잭션이 없고, DB 작업은 별도 빈(`PlaceCandidateTransactionHandler`)의 메서드 두 개가 맡는다. 같은 클래스의 메서드로 두면 `this` 호출이라 프록시를 거치지 않아 `@Transactional`이 무시된다.
- 검증을 두 번 한다. 등록 전 검증은 남의 플랜으로 Catalog에 쓰는 것을 막고, 저장 트랜잭션 안의 재검증은 Catalog를 기다리는 사이 카테고리가 지워진 경우를 막는다. 트랜잭션이 끊긴 곳의 검증 결과는 그때의 사실일 뿐이다.
- 등록이 성공하고 저장이 실패하면 쓰이지 않는 장소 한 줄이 남는다. 장소는 공용이고 다시 등록하면 재사용되므로 보상하지 않는다. 후보를 먼저 저장했다면 없는 장소를 가리키는 후보가 남아 보상이 필요했다.
- 부르는 쪽 코드는 그대로이고 `PlaceCatalogApi`의 구현만 바뀌었다(4-1과 같은 방식). 테스트는 가장 바깥의 HTTP 클라이언트만 가짜로 바꿔 어댑터까지 지나가게 했다.

## 알려진 한계

- 4-1: 모놀리식 기준선의 Place·User 에러 코드 중복(4104·4105)은 사라졌지만, Catalog가 돌려주는 4104·4105를 모놀리식이 그대로 전달하므로 클라이언트가 받는 응답에서는 여전히 겹친다. 에러 코드 형식 전환(7단계, ADR 필요) 때 함께 푼다.
- 4-2: 모놀리식 `PlaceCatalogService.register`의 동시 등록 경로가 실제 DB에서 실패하던 문제(T-011)는 ④에서 그 코드를 지우면서 사라졌다. 남은 등록 경로는 Catalog의 고친 코드 하나다.
- 4-2: Catalog의 `ddl-auto: update`는 임시다. 컬럼 삭제·이름 변경을 못 하고 변경 이력이 남지 않는다. 4-3에서 Flyway로 바꾼다.
- 4-2: 로컬 DB에는 `place_candidates.place_id → places` 외래키가 남아 있다(예전 `@ManyToOne`이 만든 것). 지금은 Catalog가 장소를 먼저 커밋해서 걸리지 않지만, DB를 나누는 4-3에서 지운다.
- 4-2: 장소 등록 후 후보 저장이 실패하면 쓰이지 않는 `places` 행이 남는다. 무해하다고 보고 정리하지 않는다.
