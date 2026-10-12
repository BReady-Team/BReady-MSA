# 1단계 경계 지도 확정

> 결정은 [ADR-0001](../adr/0001-bounded-contexts-and-extraction-order.md), 근거 자료는 [경계 지도](../architecture/context-map.md).

## 왜 지금

다음 단계(모듈러 모놀리스)는 모듈 사이의 잘못된 참조를 끊는 작업이다. 그런데 잘못된 참조가 무엇인지는 경계를 어디에 긋느냐에 달려 있다.

ArchUnit 기준선은 패키지를 모듈로 보고 위반을 435건으로 센다. 이대로 진행하면 `trigger → plan` 같은 참조도 끊어야 할 대상이 된다. 하지만 전환은 한 트랜잭션에서 trigger와 plan의 데이터를 함께 바꾸므로, 이 둘이 같은 경계라면 끊을 이유가 없다. 경계를 먼저 정하지 않으면 끊지 않아도 되는 것을 끊느라 시간을 쓰고, 끊어서는 안 되는 것을 끊어 정합성을 깨뜨린다.

## 경계를 정한 방법

바운디드 컨텍스트는 "함께 바뀌어야 하는 데이터와 규칙의 묶음"으로 봤다. 서비스를 나누면 DB가 나뉘고, 그러면 트랜잭션과 락과 JOIN이 그 선을 넘지 못한다. 그래서 경계를 긋는 것은 "여기서부터는 한 번에 맞추기를 포기한다"고 정하는 일이다.

경계 후보 둘을 놓고 아래 질문을 차례로 던졌다. 앞의 세 질문은 나누면 안 되는 이유를, 뒤의 네 질문은 나눠도 되는 이유를 찾는다. 

| 질문 | "예"이면 | 이 프로젝트의 근거 |
|---|---|---|
| 한 트랜잭션으로 같이 커밋되어야 하나 | 같은 경계 | 전환은 대표 변경(`category_states`)과 이력(`switch_logs`)이 같이 남아야 한다 |
| 같은 행을 놓고 락으로 줄을 세우나 | 같은 경계 | 후보 등록, 대표 지정, 전환, 타입 변경이 모두 `category_states`를 `FOR UPDATE`로 잠근다 |
| 생명주기가 묶여 있나 | 같은 경계 | 카테고리 타입을 바꾸면 그 카테고리의 후보가 전부 지워진다 |
| 잠깐 틀려도 사용자가 괜찮나 | 나눌 수 있다 | 통계는 이미 커밋 뒤 비동기로 갱신한다 |
| 같은 단어가 다른 뜻으로 쓰이나 | 나눈다 | `Place`는 공용 장소 데이터, `PlaceCandidate`는 특정 플랜의 후보다 |
| 쓰는 쪽이 하나뿐인가 | 그쪽이 소유자 | `places`는 place만 쓰고 나머지는 읽는다 |
| 죽어도 핵심 흐름이 멈추지 않나 | 나눌 가치가 있다 | AI 추천이 실패해도 플랜과 전환은 돌아야 한다 |

질문의 답은 코드에서 찾았다.

1. `@Transactional` 메서드마다 어떤 테이블에 쓰는지 나열했다. 한 메서드가 여러 패키지의 테이블에 쓰면 그 패키지들은 한 덩어리일 가능성이 크다.
2. `@Lock`을 찾아 누가 어떤 행을 잠그는지 봤다.
3. 패키지를 넘는 JPA 연관을 찾았다. DB에서는 FK다.
4. JOIN 쿼리를 찾고 왜 JOIN하는지를 봤다. 통계 쿼리 대부분은 소유자로 거르려고 `plans`까지 JOIN한다. 이런 것은 데이터 복제로 풀 수 있는 약한 결합이다.
5. 호출 방향을 그렸다. 한쪽으로만 가면 떼기 쉽고 서로 가리키면 어렵다.

## 설계 (선택지 / 결정)

선택지 비교는 ADR-0001에 있다. 결정만 옮긴다.

| 결정 | 선택 | 근거 |
|---|---|---|
| trigger의 위치 | Planning에 합친다 | 전환의 본질은 대표 후보 변경이고, 잠그고 바꾸는 `category_states`는 plan 소유다 |
| 장소 후보의 위치 | 후보는 Planning, 장소만 Place Catalog | 후보의 생명주기는 카테고리에 묶여 있다 |
| auth와 user | 하나의 Identity | 가입이 사용자 저장과 토큰 발급을 한 번에 한다 |
| 추출 순서 | Place Catalog → Recommendation → Stats | Place Catalog는 나가는 의존이 없다 |
| 인가 누락 | 모듈러 모놀리스 단계에서 Planning 진입점과 함께 수정 | 같은 컨텍스트 안의 검증이 된다 |
| 소프트 삭제 불일치 | 버그로 기록, Stats 추출 때 수정 | 그 쿼리들은 그때 전부 다시 쓴다 |

확정한 컨텍스트:

| 컨텍스트 | 포함 (현재 패키지) | 소유 테이블 |
|---|---|---|
| Identity | auth + user | `users`, `user_profiles`, Redis 토큰 |
| Planning | plan + 장소 후보 + trigger | `plans`, `plan_categories`, `place_candidates`, `category_states`, `category_selection_logs`, `triggers`, `decisions`, `switch_logs` |
| Place Catalog | 장소 검색 + 장소 | `places` |
| Recommendation | recommendation | 없음 (Redis 캐시) |
| Stats | stats | `plan_stats` |
| File | s3 | 없음 |

## 흐름 (before → after)

trigger를 따로 뗐다면 전환이 어떻게 바뀌는지가 이 결정의 핵심이다.

```
합친 경우 (채택): 지금과 같다
  POST /triggers/{decisionId}/switch → Planning
    BEGIN
      decisions 조회 → 후보 검증 → category_states FOR UPDATE → 대표 변경 → switch_logs INSERT
    COMMIT                                          한 DB, 한 트랜잭션, 한 락

나눈 경우 (버림)
  POST /triggers/{decisionId}/switch → Trigger 서비스
    BEGIN (trigger DB)
      decisions 조회
      ── HTTP ──▶ Planning: 대표를 바꿔 달라
                    BEGIN (planning DB)  category_states FOR UPDATE → 변경  COMMIT
      switch_logs INSERT
    COMMIT (trigger DB)
        여기서 실패하면 대표는 이미 바뀌었는데 이력은 없다 → 보상이 필요하다
```

추출 순서는 이렇다.

```
지금                      첫 추출                     그 다음
┌──────────────────┐     ┌──────────────────┐        ┌──────────────────┐
│ 모놀리식           │     │ 모놀리식           │        │  모놀리식           │
│  Identity        │     │  Identity        │        │  Identity        │
│  Planning        │     │  Planning        │        │  Planning ──이벤트─┼─▶ Stats
│  Place Catalog   │  →  │  Recommendation  │   →    │                  │
│  Recommendation  │     │  Stats           │        └───┬──────────▲───┘
│  Stats           │     └───┬──────────────┘            │ HTTP     │ HTTP
└──────────────────┘         │ HTTP (한 방향)              ▼          │
                             ▼                      Place Catalog  Recommendation
                         Place Catalog
```

## 검증 증거

검증할 것은 "이 결정이 끊을 대상을 실제로 얼마나 바꾸는가"

| 누가 → 누구를 | 건수 | 컨텍스트로 보면 |
|---|---|---|
| auth → user | 67 | Identity 내부 |
| plan → place | 52 | 대부분 Planning 내부. `Place`를 쓰는 부분만 Catalog 경계 |
| place → plan | 32 | Planning 내부 |
| trigger → plan | 27 | Planning 내부 |
| trigger → place | 11 | Planning 내부 |
| recommendation → trigger, plan, place | 131 | 경계 위반 |
| stats → trigger, plan | 109 | 경계 위반 |
| plan → user | 6 | 경계 위반 |

합계 435건 중 약 189건(43%)이 컨텍스트 내부 참조, 약 246건이 실제 경계 위반

## 다음 단계

- ArchUnit 규칙의 모듈 정의를 패키지에서 컨텍스트로 바꾼다. 장소 후보를 Planning 쪽 패키지로 옮기는 작업과 함께 한다.
- Planning이 Place Catalog에 묻는 공개 API를 가장 먼저 만든다. 첫 추출의 경계가 된다.
- 트리거·결정·전환·후보 API에 소유권 검증을 넣는다.
- 쓰이지 않는 `PlanStatsJoinService`와 그것이 끌고 있는 native query를 지운다. 경계 위반 한 건이 코드 삭제만으로 사라진다.

## 알려진 한계

- 189건과 246건은 근삿값이다. `plan → place` 52건 안에 후보를 쓰는 것과 `Place`를 쓰는 것이 섞여 있어, 패키지를 실제로 옮겨 봐야 정확한 수가 나온다.
- Planning 안의 알려진 버그(전환 검증이 락보다 먼저 실행되는 경쟁 상태)는 이 결정으로 고쳐지지 않는다. 같은 컨텍스트에 두기로 했으므로 락 순서를 바꾸는 것으로 고칠 수 있고, 다음 단계에서 재현 테스트와 함께 다룬다.
- 플랜 상세 조회가 장소 정보를 어떻게 가져올지(복사해 둘지, 조회할 때 부를지)는 정하지 않았다. 첫 추출 때 정한다.
