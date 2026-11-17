# 5단계 다음 추출 — Recommendation, Stats

## 이 단계의 목표

Recommendation과 Stats를 모놀리식에서 떼어낸다. 메시지 브로커·서킷브레이커 같은 장치는 미리 깔지 않고, 그것 없이 생기는 문제를 먼저 재현한 뒤 그 자리에만 붙인다.

| 조각 | 내용 | PR | 상태 |
|---|---|---|---|
| 5-1 | 관측 기반: 세 서비스(Gateway·모놀리식·Catalog)의 지표·추적을 Prometheus·Grafana·Zipkin으로 | | 구현 완료 |
| 5-1b | 부하에서 드러난 첫 행 생성 교착(`category_states`·`plan_stats`) 수정 | | |
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

## 흐름 (before → after)

### 5-1

```
before: 느리다 → 로그의 "[API 실행 시간] Nms"를 눈으로 훑는다 (요청마다 INFO 한 줄)
after:  Grafana p95가 오른다
        → 2줄에서 서비스·URL을 고른다
        → 그래프의 exemplar 점을 누른다 (버킷에 붙은 trace_id)
        → Zipkin에서 그 요청의 구간(gateway → monolith → SQL·place-catalog)을 본다
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

## 워크스루 요약

### 5-1

- 요청이 Gateway에 들어오면 관측(observation)이 열리고 traceId가 생긴다. 다음 서비스로 갈 때 `traceparent` 헤더로 넘어가서 세 서비스가 같은 traceId를 쓴다. `@Async`로 넘어갈 때는 `ContextPropagatingTaskDecorator`가 옮긴다.
- actuator 요청과 보안 필터 구간은 `ObservationPredicate`로 지표·추적 모두에서 뺀다. 빼지 않으면 5초마다 오는 수집 요청이 요청 수에 섞인다.
- 요청이 끝나면 걸린 시간이 히스토그램 버킷에 들어가고, 그 버킷에 trace_id가 exemplar로 붙는다. 버킷이 없으면 p95도 exemplar도 없다.
- Prometheus는 `exemplar-storage`를 켜야 exemplar를 저장하고, Grafana 데이터소스의 `exemplarTraceIdDestinations`가 그 trace_id를 Zipkin으로 잇는다.

## 알려진 한계

### 5-1

- Gateway의 `http_server_requests`는 `uri=UNKNOWN`이다. 라우트로 넘기기만 해서 URL 패턴을 모른다. 엔드포인트 분석은 모놀리식·Catalog 지표로 한다.
- Gateway의 전달 호출 지표는 이름표가 다르다(`http_status_code`, `spring_cloud_gateway_route_id`). 라우트가 하나뿐이라 대시보드에서 뺐고, 5-3에서 라우트가 늘면 라우트별 패널을 만든다.
- Grafana 10.2의 Zipkin 데이터소스는 상태 확인에 `Plugin unavailable`을 돌려준다. 브라우저 전용 플러그인이라서다. 연결은 프록시 조회로 확인했다.
- 새 시계열의 첫 증가분은 `rate()`가 보지 못한다(T-014). 짧은 폭주 실험은 결과가 0으로 보일 수 있다.
- 추적 표본 100%는 로컬 실험용이다.
