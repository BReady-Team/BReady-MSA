# 5단계 다음 추출 — Recommendation, Stats

## 이 단계의 목표

Recommendation과 Stats를 모놀리식에서 떼어낸다. 메시지 브로커·서킷브레이커 같은 장치는 미리 깔지 않고, 그것 없이 생기는 문제를 먼저 재현한 뒤 그 자리에만 붙인다.

| 조각 | 내용 | PR | 상태 |
|---|---|---|---|
| 5-1 | 관측 기반: 세 서비스(Gateway·모놀리식·Catalog)의 지표·추적을 Prometheus·Grafana·Zipkin으로 | | 구현 완료 |
| 5-1b | 부하에서 드러난 첫 행 생성 교착(`category_states`·`plan_stats`) 수정 | | 구현 완료 |
| 5-2 | 추천의 외부 호출이 DB 커넥션을 쥐는 고통 재현 + 트랜잭션 경계 정리(관측으로 전·후 비교) | | 구현 완료 |
| 5-3 | `recommendation-service` 추출 (a Planning 조회 HTTP 입구 → b 서비스와 코드 이동 → c 라우트 전환·삭제·관측) | | 5-3a 구현 완료 |
| 5-4 | 타임아웃·서킷브레이커·폴백 | | |
| 5-5~ | Stats 추출, Kafka + Outbox | | |

관측성은 로드맵상 6단계였다. 5단계의 장치(서킷브레이커, Outbox)는 "그것 없이 생기는 고통을 재현한 뒤 붙인다"가 원칙인데, 고통을 로그로만 보면 무엇이 무엇을 막았는지 보이지 않는다. 그래서 지표와 추적을 5단계 첫 조각으로 앞당겼다. 서비스별 상세 대시보드·알림·k8s 위의 수집은 6단계에 그대로 둔다.

## 왜 지금

4단계로 첫 서비스(Place Catalog)가 떨어져 나가면서, 모놀리식이 다른 프로세스를 기다리는 자리가 생겼다. 추천은 그 기다림이 가장 긴 곳이다.

- `PlaceRecommendationService.recommendPlaces`와 `CategoryRecommendationService.recommendCategories`는 `@Transactional(readOnly = true)` 안에서 Catalog 검색(읽기 타임아웃 8초)과 OpenAI 재정렬(타임아웃 설정 없음)을 부른다.
- Recommendation은 자기 테이블이 없다(Redis 캐시만). Planning 데이터는 `TriggerContextApi` 두 호출로만 얻는다.

## 설계 (선택지 · 결정)

### 5-1 관측 기반

| 결정 | 고른 것 | 고르지 않은 것과 이유 |
|---|---|---|
| 추적 | Micrometer Tracing + Brave → Zipkin | OpenTelemetry Collector: 수집기를 하나 더 띄울 만큼의 고통이 아직 없다 |
| 로그 | 로그 줄에 traceId만 붙인다 | Loki: 로그 검색이 막힌 적이 없다 |
| 실행 | compose `monitoring` 프로필 | 앱과 같이 늘 띄우기: 앱만 볼 때 무겁다 |
| Grafana 설정 | 데이터소스·대시보드를 파일로 프로비저닝, 볼륨 없음 | 화면에서 만들기: git에 남지 않고 다시 띄우면 사라진다 |
| 대시보드 | 직접 작성한 패널 24개 | 커뮤니티 대시보드: 패널이 많아 무엇을 봐야 하는지 흐려진다 |

대시보드는 이후 조각에서 겪을 고통이 바로 보이게 짰다.

| 줄 | 답하는 질문 | 쓰일 조각 |
|---|---|---|
| 1. 입구(Gateway) | 사용자가 느끼는 요청 수·p95·4xx·5xx | 전부 |
| 2. 서비스 / 엔드포인트 | 어느 서비스·URL이 느리거나 실패하나(exemplar → Zipkin) | 전부 |
| 3. 다른 서비스 / 외부 API 호출 | 내가 부른 쪽 때문인가 | 5-3, 5-4 |
| 4. DB 커넥션 풀 | 커넥션을 기다리나, 누가 오래 쥐나(획득 대기·빌려 쓴 시간) | 5-2 |
| 5. 스레드 / JVM | 스레드가 쌓이나, GC 탓인가 | 5-2 |

AI 호출 줄은 5-2로 미뤘다. 더미 카카오 키에서는 추천이 카카오 검색 단계에서 끝나 AI까지 가지 않아 `gen_ai_*` 지표가 없다. 5-2에서 지연을 조절하는 가짜 카카오·OpenAI를 세우면 실제 지표를 보고 추가한다.

### 5-1b 첫 행 생성 교착

5-1의 부하 테스트에서 후보 추가가 500으로 실패했다. "행을 `FOR UPDATE`로 찾고 없으면 insert"하는 코드가 원인이었다. 없는 행을 찾으면 인덱스의 빈 구간에 갭 락이 잡히는데, 새 키는 늘 맨 끝에 붙어서 서로 다른 카테고리·플랜이 같은 구간에서 만난다(T-015).

| 대상 | 고른 것 | 고르지 않은 것과 이유 |
|---|---|---|
| `category_states` | 카테고리를 만들 때 대표가 빈 상태 행을 함께 만든다 | 후보 추가 때 upsert: "상태 행이 없을 수 있다"는 분기가 코드에 계속 남는다 |
| `plan_stats` | `insert ... on duplicate key update`로 행을 먼저 보장한 뒤 잠근다 | 플랜을 만들 때 미리 만들기: Stats의 테이블을 Planning의 트랜잭션에서 쓰게 된다 |
| 기존 데이터 | 상태 행이 없는 카테고리를 일회성 SQL로 채운다 | 코드에 "없으면 만든다"를 남기기: 교착의 원인이 그 분기다 |
| 공통 | 원인을 없앤다 | 교착이 나면 재시도: 원인은 그대로 두고 증상만 가린다 |

### 5-2 추천의 트랜잭션 경계

추천 서비스는 읽기 트랜잭션을 연 채로 Catalog(그 뒤의 카카오)와 OpenAI를 불렀다. 추천은 자기 테이블이 없어 DB는 맨 앞의 Planning 조회 수 ms만 쓰는데, 커넥션은 외부 응답이 올 때까지 쥐고 있었다. 5-1의 부하에서는 보이지 않았다. 시나리오에 추천이 없었고, 더미 카카오 키라 Catalog가 401로 바로 끝났기 때문이다. 그래서 느린 외부를 먼저 만들었다.

| 결정 | 고른 것 | 고르지 않은 것과 이유 |
|---|---|---|
| 느린 외부 | WireMock 컨테이너 둘(가짜 카카오·OpenAI)을 compose 덮어쓰기 파일로. 주소는 환경 변수로 돌린다 | 가짜 서버를 직접 작성: 서비스가 하나 더 생긴다 / 코드에 지연 플래그: 운영 코드에 실험 코드가 섞이고 실제 HTTP 대기가 아니다 |
| 부하 | 도착률 고정(추천 초당 12 · 플랜 목록 초당 20), 가운데 60초만 카카오 3초 지연 | 가상 사용자 수 고정: 응답이 느려지면 보내는 양이 줄어 전·후가 같은 부하가 아니게 된다 |
| 트랜잭션 | 추천 서비스 둘에서 `@Transactional(readOnly = true)`를 뗀다 | DB 부분을 트랜잭션 빈으로 분리: 추천에는 자기 DB 작업이 없고, Planning 조회는 `TriggerContextService`가 이미 자기 트랜잭션으로 한다 / 풀을 키우거나 타임아웃을 줄이기: 원인은 그대로다 |
| 회귀 방지 | 외부를 부르는 순간 열린 트랜잭션이 없음을 단언하는 통합 테스트 | 부하 테스트로만 확인: 누가 어노테이션을 되살려도 빌드가 알려 주지 않는다 |
| 대시보드 | 실험 전용 대시보드를 새로(원인 → 번지는 자리 → 피해 3줄) | 기존 개요 대시보드에 줄 추가: 이미 24패널이라 전·후 비교가 한 화면에 들어오지 않는다 |

지연 3초는 Catalog의 카카오 읽기 타임아웃(5초)보다 짧게 잡았다. 그보다 길면 503으로 끊겨 "느리지만 성공하는 외부"가 아니라 "실패하는 외부" 실험이 된다.

### 5-3 recommendation-service 추출

5-2로 커넥션은 풀렸지만 외부를 기다리는 요청 스레드는 모놀리식의 것이었다(지연 3초 · 초당 12건에 38개, 초당 70건쯤이면 200개가 다 찬다). 스레드 풀은 프로세스에 하나라서 같은 프로세스 안에서는 추천만 느려지게 가둘 수 없다. Catalog와 달리 추천은 모놀리식(Planning)의 데이터가 필요해서, 떼어낸 서비스가 모놀리식을 부르는 첫 사례다.

| 결정 | 고른 것 | 고르지 않은 것과 이유 |
|---|---|---|
| 입구와 호출 방향 | Gateway가 추천 서비스로 바로 보내고, 추천 서비스가 사용자의 토큰을 그대로 붙여 모놀리식의 Planning 조회를 부른다(Gateway → 추천 → 모놀리식·Catalog) | 모놀리식이 입구가 되어 조회 결과를 실어 보내기: 모놀리식 스레드가 계속 추천을 기다려 떼어낸 효과가 없다 / 모놀리식이 입구이고 추천이 되부르기: 호출이 순환하고 요청 하나가 모놀리식 스레드를 둘 잡는다 |
| 인증 | 손대지 않는다. 추천은 첫 단계에서 Planning에 "이 사용자의 트리거인가"를 물어야 하므로, 그 호출에 실린 토큰을 모놀리식의 JWT 필터와 소유권 검증이 그대로 판정한다 | 추천 서비스가 JWT를 직접 검증: 인증 분리는 6단계다 |
| Planning 조회의 모양 | 엔드포인트 둘(트리거 상황, 대표 장소). 둘 다 트리거 ID로 묻고 둘 다 소유권을 확인한다 | 하나로 묶고 대표 장소를 비워서 주기: "대표가 아직 없다"는 Planning의 오류를 추천 서비스가 대신 판단해야 한다 / 카테고리 ID로 대표 장소 묻기: 혼자 불리면 남의 플랜의 장소를 볼 수 있다 |
| Catalog | 추천 서비스가 직접 부른다 | 모놀리식을 거치기: 느린 구간이 다시 모놀리식 스레드를 잡는다 |
| Redis | 지금 인스턴스를 같이 쓴다(`reco:*`는 추천만 쓴다) | 전용 Redis: 캐시라 옮길 데이터가 없고 아직 서로 방해한 적이 없다 |
| 진행 | 세 조각: Planning 조회 입구(5-3a) → 서비스와 코드 이동(5-3b) → 라우트 전환·삭제·관측(5-3c) | 한 번에: 기계적인 이동과 흐름이 바뀌는 지점이 섞여 실패의 원인을 가리기 어렵다 |

## 흐름 (before → after)

### 5-1

```
before: 느리다 → 로그의 "[API 실행 시간] Nms"를 눈으로 훑는다 (요청마다 INFO 한 줄)
after:  Grafana p95가 오른다
        → 2줄에서 서비스·URL을 고른다
        → 그래프의 exemplar 점을 누른다 (버킷에 붙은 trace_id)
        → Zipkin에서 그 요청의 구간(gateway → monolith → SQL·place-catalog)을 본다
```

### 5-1b

```
before: 후보 추가(카테고리 101)                    후보 추가(카테고리 102)
        상태 행 FOR UPDATE → 없음, 끝 구간 갭 락
                                                  상태 행 FOR UPDATE → 없음, 같은 구간 갭 락
        insert → 상대의 갭 락을 기다림
                                                  insert → 상대의 갭 락을 기다림 → 교착, 한쪽 500

after:  카테고리 생성 → 카테고리 행 + 빈 상태 행 insert
        후보 추가(101) → 상태 행 101 잠금 → 대표 지정    후보 추가(102) → 상태 행 102 잠금 → 대표 지정
        (각자 자기 행만 잠가서 서로 기다리지 않는다)

        통계 계산 → upsert로 행 보장 → 그 행 잠금 → 갱신
```

### 5-2

```
before: 장소 추천 요청
        BEGIN(readOnly) ── 커넥션 빌림 ──────────────────────────┐
          Planning 조회 (바깥 트랜잭션에 합류, 수 ms)             │
          Catalog HTTP → 카카오 (3초)                            │ 쥐고 있음
          (AI를 켜면) OpenAI HTTP                                │
        COMMIT ────────── 커넥션 반납 ──────────────────────────┘
        그동안 풀 30개가 다 차고, 외부를 부르지 않는 플랜 목록 조회도 커넥션을 기다린다

after:  장소 추천 요청 (트랜잭션 없음)
          getTriggerContext       BEGIN → 조회 → COMMIT  (수 ms)
          getRepresentativePlace  BEGIN → 조회 → COMMIT  (수 ms)
          Catalog HTTP → 카카오 (3초)      커넥션 없음, 요청 스레드만 기다린다
          (AI를 켜면) OpenAI HTTP          커넥션 없음
```

### 5-3

```
before: Client → Gateway → 모놀리식 [Tomcat 스레드 하나를 끝까지 잡는다]
                             JWT 필터 → RecommendationController
                             getTriggerContext        BEGIN → 조회 → COMMIT (수 ms, 메서드 호출)
                             getRepresentativePlace   BEGIN → 조회 → COMMIT (수 ms)
                             Redis 캐시
                             Catalog HTTP → 카카오    (느릴 수 있다)
                             (AI를 켜면) OpenAI HTTP

after:  Client → Gateway ─(/api/v1/recommendations/**)→ 추천 서비스 [추천 서비스의 스레드]
                             Authorization 헤더를 그대로 붙여
                             ── GET /internal/v1/triggers/{id}/context → 모놀리식
                                    JWT 필터 → 소유권 확인 → BEGIN → 조회 → COMMIT (수 ms)
                                    401 · 403 · 404면 그대로 전달하고 끝
                             ── GET /internal/v1/triggers/{id}/representative-place → 모놀리식 (필요할 때만)
                             Redis 캐시
                             ── Catalog HTTP → 카카오   (느려도 모놀리식 스레드와 무관)
                             (AI를 켜면) OpenAI HTTP
        Client → Gateway ─(나머지)→ 모놀리식
```

5-3a까지는 before 그대로다. 모놀리식에 조회 입구만 생겼고 아직 아무도 부르지 않는다.

## 구현 파일

### 5-1

| 파일 | 역할 |
|---|---|
| 세 서비스 `application.yml` | 지표에 `application` 이름표, 히스토그램 버킷(요청·호출·Hikari·AI), 추적 표본 100%, Tomcat 스레드 지표 |
| 세 서비스 `build.gradle` | actuator, Prometheus 레지스트리, Brave 브리지, Zipkin 리포터, (DB 있는 둘) datasource-micrometer |
| `docker-compose-local.yml` | Prometheus(exemplar 저장) · Zipkin · Grafana를 `monitoring` 프로필로 |
| `monitoring/prometheus.yml` | 세 서비스 수집 |
| `monitoring/grafana/provisioning/` | 데이터소스(uid 고정, exemplar → Zipkin), 대시보드 로딩 |
| `monitoring/grafana/dashboards/bready-overview.json` | 대시보드 |
| `performance-test/k6/scripts/user_flow.js` | Gateway를 거치는 사용자 흐름 부하(둘러보기 80% · 계획 짜기 20%) |
| (삭제) `global/aop/ExecutionTimeAspect.java` | 요청마다 찍던 실행 시간 INFO. 지표가 대신한다 |

### 5-1b

| 파일 | 역할 |
|---|---|
| `plan/service/PlanCategoryService.java` | 카테고리를 만들 때 빈 상태 행을 함께 저장 |
| `plan/domain/CategoryState.java` | `createEmpty`, `hasRepresentative` |
| `plan/repository/CategoryStateRepository.java` | `getByCategoryIdForUpdate`: 상태 행은 반드시 있다고 보고 잠근다 |
| `plan/service/PlaceCandidateTransactionHandler.java`, `PlaceCandidateService.java` | "상태 행이 없으면 insert" 분기 제거 |
| `stats/repository/PlanStatsRepository.java` | `insertIfAbsent` (upsert) |
| `stats/service/PlanStatsUpdater.java` | 행을 먼저 보장한 뒤 잠근다. 시각은 `Clock`에서 |
| `monolith/db/backfill/2026-11-17-category-states.sql` | 상태 행이 없는 기존 카테고리를 채우는 일회성 SQL |
| `integration/FirstCandidateAcrossCategoriesRaceIntegrationTest.java` | 서로 다른 카테고리의 첫 후보 동시 추가 재현 |
| `integration/PlanStatsFirstRowRaceIntegrationTest.java` | 서로 다른 플랜의 첫 통계 동시 계산 재현 |

### 5-2

| 파일 | 역할 |
|---|---|
| `recommendation/service/PlaceRecommendationService.java`, `CategoryRecommendationService.java` | 읽기 트랜잭션 제거 |
| `integration/RecommendationTransactionBoundaryIntegrationTest.java` | Catalog 검색·추천 포트를 부르는 순간 열린 트랜잭션이 없는지 검증 |
| `docker-compose-fake.yml` | 가짜 카카오(8089)·OpenAI(8090)를 띄우고 Catalog·모놀리식의 주소를 가짜로 돌리는 덮어쓰기 파일 |
| `performance-test/fake/` | WireMock 응답 정의: 키워드 검색(장소 15개), 재정렬 응답 |
| `performance-test/k6/scripts/recommendation_slow_external.js` | 추천과 플랜 목록을 고정 도착률로 보내고 가운데 구간에서 카카오 지연을 켰다 끈다 |
| `monitoring/grafana/dashboards/bready-recommendation-experiment.json` | 실험 대시보드(패널 8개) |

### 5-3a

| 파일 | 역할 |
|---|---|
| `trigger/api/TriggerContextApi.java`, `trigger/service/TriggerContextService.java` | 대표 장소 조회가 카테고리 ID 대신 사용자·트리거 ID를 받아 소유권을 스스로 확인 |
| `recommendation/service/PlaceRecommendationService.java` | 바뀐 시그니처로 호출 |
| `trigger/controller/TriggerContextQueryController.java` | `TriggerContextApi`의 HTTP 입구(`/internal/v1/triggers/{triggerId}/context`, `/representative-place`) |
| `trigger/controller/TriggerContextQueryApi.java` | Swagger 문서와 파라미터 제약 |
| `trigger/controller/TriggerContextQueryControllerTest.java` | 응답 JSON 필드(추천 서비스가 읽을 계약)와 오류 코드 |
| `integration/TriggerContextQueryIntegrationTest.java` | 실제 토큰으로: 소유자 200, 토큰 없음 401, 남의 트리거 403 |

## 검증 증거

### 5-1

```
버킷: 모놀리식 http_server_requests_seconds_bucket 0 → 138줄, hikaricp acquire/usage 버킷 각 69줄
exemplar → 추적: trace_id=6afbfa7d… → Zipkin 3 spans [gateway-service, monolith]
대시보드 쿼리(1:1:1 = 정상 · 장소 검색 502 · 비로그인 401, 25초):
  Gateway 4xx 33% · 5xx 33.2% / 서비스별 5xx gateway 33.2% · monolith 33.3% · place-catalog 100%
연결 실패 표시: Catalog를 멈추자 모놀리식 → place-catalog 호출이 status=CLIENT_ERROR, outcome=UNKNOWN, 응답 503
ExecutionTimeAspect 삭제 후: 요청 30개에 INFO 0줄, 같은 URL p95는 지표로 1.66ms
테스트: gateway 8 · monolith 200 · place-catalog 53 통과
```

k6 `user_flow.js`(최대 50명, 3분 20초): 요청 7072개, p95 18ms, 실패 1.32%(임계값 1% 초과). 실패는 모두 후보 추가 500(47) → 트리거 400(47) 연쇄였고, 원인은 `category_states` 첫 행 생성 교착이었다. 5-1b에서 고친다.

### 5-1b

```
재현 테스트 둘: 수정 전 실패(교착) → 수정 후 통과
백필 뒤 확인 쿼리: 상태 행이 없는 카테고리 0 · 후보가 있는데 대표가 없는 카테고리 0
k6 user_flow.js 같은 조건(최대 50명, 3분 20초):
  요청 7078개 · 실패 1.32% → 0.00% · 체크 7078/7078 · p95 20ms
  후보 추가 500: 47건 → 0건, 트리거 400: 47건 → 0건
모놀리식 로그: Deadlock 0건, ERROR 0건
통계 행: 부하 중 트리거가 생긴 플랜 471개 모두 WEEK · MONTH · ALL 행 있음(누락 0)
```

### 5-2

```
재현 테스트 둘: 수정 전 실패(외부 호출 시점에 트랜잭션이 열려 있음) → 수정 후 통과
테스트: gateway 8 · monolith 207 · place-catalog 53 통과
가짜 카카오: 지연 0 → Catalog 검색 0.07초 / 3초 → 3.02초 / 6초 → 5.02초에 503(읽기 타임아웃)
가짜 OpenAI: AI를 켜고 46회 호출, 전부 200, 재정렬 파싱 실패 0건
```

k6 `recommendation_slow_external.js`(추천 초당 12 · 플랜 목록 초당 20, 평소 60초 → 카카오 3초 지연 60초 → 회복 60초). 아래는 지연 구간의 값이다.

| | 수정 전 | 수정 후 |
|---|---|---|
| 플랜 목록 p95 | 7.81초 | 7.95ms |
| 장소 추천 p95 | 10.73초 | 3.01초 |
| 커넥션 사용 중 (최대 30) | 30 | 1 |
| 커넥션 대기 | 169 | 0 |
| 커넥션을 빌려 쓴 시간 p95 | 3.18초 | 0.06초 |
| 커넥션을 얻기까지 기다린 시간 p95 | 7.94초 | 0.001초 |
| Tomcat 일하는 스레드 (최대 200) | 200 | 38 |
| 실패 | 0% | 0% |

평소·회복 구간은 전·후 모두 플랜 목록 p95 7~9ms, 추천 p95 14~16ms였다. 수정 전에도 실패는 0%였다. 에러율만 보면 이 장애는 보이지 않는다.

### 5-3a

```
테스트: gateway 8 · monolith 207 → 217 · place-catalog 53 통과 (아키텍처 테스트 포함, 기준선 변화 없음)
새 테스트 10개: 컨트롤러 5(응답 필드, 7001 · 7005, 양수가 아닌 ID 400) · 통합 5(소유자 200 둘, 토큰 없음 401, 남의 트리거 403 둘)
추천 응답 계약 테스트(장소 · 카테고리)와 트랜잭션 경계 테스트: 시그니처 변경 뒤에도 통과
```

## 워크스루 요약

### 5-1

- 요청이 Gateway에 들어오면 관측(observation)이 열리고 traceId가 생긴다. 다음 서비스로 갈 때 `traceparent` 헤더로 넘어가서 세 서비스가 같은 traceId를 쓴다. `@Async`로 넘어갈 때는 `ContextPropagatingTaskDecorator`가 옮긴다.
- actuator 요청과 보안 필터 구간은 `ObservationPredicate`로 지표·추적 모두에서 뺀다. 빼지 않으면 5초마다 오는 수집 요청이 요청 수에 섞인다.
- 요청이 끝나면 걸린 시간이 히스토그램 버킷에 들어가고, 그 버킷에 trace_id가 exemplar로 붙는다. 버킷이 없으면 p95도 exemplar도 없다.
- Prometheus는 `exemplar-storage`를 켜야 exemplar를 저장하고, Grafana 데이터소스의 `exemplarTraceIdDestinations`가 그 trace_id를 Zipkin으로 잇는다.

### 5-1b

- 카테고리를 만드는 트랜잭션이 카테고리 행과 빈 상태 행을 함께 저장한다. 찾지 않고 바로 insert하므로 갭 락을 쥔 채 기다리는 구간이 없다.
- 후보 추가는 카테고리 행을 잠그고(같은 카테고리 줄 세우기, T-013), 후보를 저장한 뒤 상태 행을 잠근다. 상태 행은 반드시 있어서 그 행 하나만 잠기고, 대표가 비어 있으면 방금 저장한 후보를 대표로 지정한다.
- 트리거가 커밋되면 `@Async` 리스너가 기간마다 통계를 다시 계산한다. upsert로 행을 먼저 보장하고 그 행을 잠가 갱신한다. 이미 있으면 upsert는 아무것도 바꾸지 않는다.
- 두 곳 모두 "갭 락 → insert" 순서가 "insert → 행 락"으로 바뀌었다. 행 락은 자기 키만 잠가서 다른 카테고리·플랜과 만나지 않는다.

### 5-2

- 추천 요청이 `recommendPlaces`에 들어온다. 전에는 여기서 읽기 트랜잭션이 열렸다.
- Planning 조회(`TriggerContextService`)는 자기 트랜잭션 선언이 있지만, 이미 열린 트랜잭션이 있으면 새로 열지 않고 합류한다. 그래서 조회가 끝나도 커넥션은 바깥 메서드가 끝날 때까지 반납되지 않았다. 바깥 선언을 떼자 조회가 각자 열고 닫는다.
- 캐시에 없으면 추천 포트 → Catalog HTTP → 카카오로 나간다. 이 구간에서 쥔 커넥션이 없다. 기다리는 것은 요청 스레드뿐이다.
- 통합 테스트는 Catalog 클라이언트가 불리는 순간 열린 트랜잭션이 있는지 기록해 단언한다. 선언을 되살리면 이 테스트가 실패한다.

### 5-3a

- 요청(`GET /internal/v1/triggers/{triggerId}/context`)은 다른 API와 같은 JWT 필터를 지난다. 토큰이 없으면 컨트롤러에 닿기 전에 401이다. 부르는 서비스는 토큰을 해석하지 않고 붙여 보내기만 한다.
- 컨트롤러는 `@CurrentUser`로 받은 사용자 ID와 경로의 트리거 ID를 `TriggerContextApi`에 넘기고 결과를 감싸기만 한다. 응답 본문은 `trigger.api`의 record 그대로라서, 메서드 호출로 주고받던 값과 HTTP로 나가는 값이 같은 모양이다.
- `TriggerContextService`의 두 메서드는 모두 `getOwnedTrigger`로 시작한다. 트리거를 찾고 그 플랜의 소유자인지 확인한다. 대표 장소 조회는 예전에 카테고리 ID만 받아 앞선 호출의 확인에 기대고 있었는데, HTTP로 열리면 혼자 불릴 수 있어 스스로 확인하게 바꿨다.
- 대표 장소는 트리거가 가리키는 카테고리에서 찾는다. 부르는 쪽이 카테고리를 고를 수 없으므로 "내 트리거로 남의 카테고리를 묻는" 요청이 성립하지 않는다.
- 파라미터 제약(`@Positive`)은 문서 인터페이스에 둔다. 구현한 컨트롤러 쪽에 붙이면 Bean Validation이 선언 오류로 500을 낸다(T-017).

## 알려진 한계

### 5-1

- Gateway의 `http_server_requests`는 `uri=UNKNOWN`이다. 라우트로 넘기기만 해서 URL 패턴을 모른다. 엔드포인트 분석은 모놀리식·Catalog 지표로 한다.
- Gateway의 전달 호출 지표는 이름표가 다르다(`http_status_code`, `spring_cloud_gateway_route_id`). 라우트가 하나뿐이라 대시보드에서 뺐고, 5-3에서 라우트가 늘면 라우트별 패널을 만든다.
- Grafana 10.2의 Zipkin 데이터소스는 상태 확인에 `Plugin unavailable`을 돌려준다. 브라우저 전용 플러그인이라서다. 연결은 프록시 조회로 확인했다.
- 새 시계열의 첫 증가분은 `rate()`가 보지 못한다(T-014). 짧은 폭주 실험은 결과가 0으로 보일 수 있다.
- 추적 표본 100%는 로컬 실험용이다.

### 5-1b

- 백필을 돌리지 않은 DB에서는 옛 카테고리에 후보를 추가하면 500이다. 상태 행이 없는 것을 있을 수 없는 상태로 보기 때문이다.
- 통계 계산은 통계 행을 잠근 채 Planning의 집계를 읽는다(락 전에 읽으면 늦게 끝난 계산이 그사이 커밋된 전환을 덮어쓴다. `PlanStatsStaleOverwriteRaceIntegrationTest`). Stats를 떼어내면 이 조회가 원격 호출이 되어 락을 쥔 채 네트워크를 기다리게 된다.
- 통계 갱신은 여전히 메모리에만 있는 `@Async` 이벤트다. 교착으로 빠지는 일은 없어졌지만 커밋 직후 프로세스가 죽으면 사라진다. 5-5(Outbox)의 대상이다.

### 5-2

- 트리거 조회와 대표 장소 조회가 서로 다른 트랜잭션이다. 그 사이에 대표가 바뀌면 옛 트리거 정보와 새 대표 장소를 함께 볼 수 있다. 추천은 제안이라 받아들였다.
- 외부를 기다리는 동안 요청 스레드는 여전히 잡힌다. 지연 3초 · 초당 12건에서 38개였고, 추천이 초당 70건쯤이면 200개가 다 찬다. 그러면 커넥션이 남아도 플랜 목록이 다시 느려진다. 5-4(타임아웃·서킷브레이커)의 대상이다.
- OpenAI 호출에는 타임아웃이 없다. AI를 켠 상태의 지연 실험과 대시보드의 AI 패널은 5-4로 넘겼다. 이번에는 가짜 OpenAI에 연결되는 것만 확인했다.
- Redis 캐시 조회·저장도 트랜잭션 밖이 됐다. 캐시가 느려져도 커넥션을 쥐지 않지만 스레드는 잡힌다.
- Catalog의 장소 검색은 트랜잭션이 없어 같은 문제가 없다(Catalog 풀은 10개).

### 5-3a

- 장소 추천은 Planning을 두 번 부른다(트리거 상황, 대표 장소). 지금은 메서드 호출이라 차이가 없지만 5-3c부터는 네트워크 왕복 두 번이고, 두 번 다 트리거 조회와 소유권 확인을 한다.
- `/internal/**` 경로도 Gateway의 `/**` 라우트를 타서 밖에서 닿는다. 토큰과 소유권 확인이 있어 남의 데이터는 볼 수 없지만, 화면이 부를 경로가 아니다. Gateway에서 막는 것은 라우트를 손보는 5-3c에서 한다.
- 실제 기동 후 curl로는 확인하지 않았다. 통합 테스트가 실제 보안 필터와 토큰으로 검증하고, 다른 프로세스가 부르는 것은 5-3c에서 확인한다.
