# 5단계 다음 추출 — Recommendation, Stats

## 이 단계의 목표

Recommendation과 Stats를 모놀리식에서 떼어낸다. 메시지 브로커·서킷브레이커 같은 장치는 미리 깔지 않고, 그것 없이 생기는 문제를 먼저 재현한 뒤 그 자리에만 붙인다.

| 조각 | 내용 | PR | 상태 |
|---|---|---|---|
| 5-1 | 관측 기반: 세 서비스(Gateway·모놀리식·Catalog)의 지표·추적을 Prometheus·Grafana·Zipkin으로 | | 설계 중 |
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

## 흐름 (before → after)

## 구현 파일

## 검증 증거

## 워크스루 요약

## 알려진 한계
