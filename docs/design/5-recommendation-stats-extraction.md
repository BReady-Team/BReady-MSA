# 5단계 다음 추출 — Recommendation, Stats

## 이 단계의 목표

Recommendation과 Stats를 모놀리식에서 떼어낸다. 메시지 브로커·서킷브레이커 같은 장치는 미리 깔지 않고, 그것 없이 생기는 문제를 먼저 재현한 뒤 그 자리에만 붙인다.

| 조각 | 내용 | PR | 상태 |
|---|---|---|---|
| 5-1 | 관측 기반: 세 서비스(Gateway·모놀리식·Catalog)의 지표·추적을 Prometheus·Grafana·Zipkin으로 | | 구현 완료 |
| 5-1b | 부하에서 드러난 첫 행 생성 교착(`category_states`·`plan_stats`) 수정 | | 구현 완료 |
| 5-2 | 추천의 외부 호출이 DB 커넥션을 쥐는 고통 재현 + 트랜잭션 경계 정리(관측으로 전·후 비교) | | |
| 5-3 | `recommendation-service` 추출 | | |
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

## 알려진 한계

### 5-1

- Gateway의 `http_server_requests`는 `uri=UNKNOWN`이다. 라우트로 넘기기만 해서 URL 패턴을 모른다. 엔드포인트 분석은 모놀리식·Catalog 지표로 한다.
- Gateway의 전달 호출 지표는 이름표가 다르다(`http_status_code`, `spring_cloud_gateway_route_id`). 라우트가 하나뿐이라 대시보드에서 뺐고, 5-3에서 라우트가 늘면 라우트별 패널을 만든다.
- Grafana 10.2의 Zipkin 데이터소스는 상태 확인에 `Plugin unavailable`을 돌려준다. 브라우저 전용 플러그인이라서다. 연결은 프록시 조회로 확인했다.
- 새 시계열의 첫 증가분은 `rate()`가 보지 못한다(T-014). 짧은 폭주 실험은 결과가 0으로 보일 수 있다.
- 추적 표본 100%는 로컬 실험용이다.

### 5-1b

- 백필을 돌리지 않은 DB에서는 옛 카테고리에 후보를 추가하면 500이다. 상태 행이 없는 것을 있을 수 없는 상태로 보기 때문이다.
- 통계 갱신은 여전히 메모리에만 있는 `@Async` 이벤트다. 교착으로 빠지는 일은 없어졌지만 커밋 직후 프로세스가 죽으면 사라진다. 5-5(Outbox)의 대상이다.
