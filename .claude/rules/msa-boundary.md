# MSA 경계

이 프로젝트에서 가장 중요한 규칙이다. 지금은 모놀리식이지만, **새 코드는 나중에 서비스로 떼어낼 수 있는 모양으로** 쓴다.
현재 경계 상태(누가 누구를 어떻게 쓰는지)는 [docs/architecture/context-map.md](../../docs/architecture/context-map.md)가 원본이다.

## 1. 모듈

- 모듈 = `com.bready.server` 바로 아래 패키지: `auth` `user` `plan` `place` `trigger` `recommendation` `stats` `s3`.
- `global`은 **기술 공통**(응답 봉투, 예외 뼈대, 보안 필터, 설정, BaseEntity)만 담는다. 도메인 지식(특정 모듈의 ErrorCase, 엔티티, 규칙)을 넣지 않는다. [빌드]
- 잠정 바운디드 컨텍스트(1단계에서 ADR로 확정): Identity(auth+user) · Planning 코어(plan+후보+trigger) · Place Catalog · Recommendation · Stats · File(s3).

## 2. 새 코드의 규칙 [빌드]

1. **다른 모듈의 Repository를 주입하지 않는다.**
2. **다른 모듈의 Entity를 필드·파라미터·반환 타입으로 쓰지 않는다.** enum처럼 값만 담은 타입도 공개 API 패키지로 옮겨 놓은 것만 쓴다.
3. **다른 모듈의 데이터는 ID로 참조**하고(`entity.md` 6번), 필요한 값은 그 모듈의 **공개 API**를 호출해 받는다.
4. **다른 모듈의 테이블을 JOIN하지 않는다**(`repository-query.md` 7번).
5. **이벤트는 발행자가 소유한다**(`service-transaction.md` 6번).
6. **한 트랜잭션에서 다른 모듈의 데이터를 쓰지 않는다.** 필요하면 그 모듈의 공개 API를 부르고, 일관성이 깨질 수 있는 지점을 주석으로 남긴다.

**공개 API의 모양**은 2단계(모듈러 모놀리스)에서 정한다(ADR). 그 전까지 새로 생기는 모듈 간 호출은 상대 모듈의 **서비스**를 부르는 것까지만 허용하고, 리포지토리·엔티티를 직접 쓰지 않는다.

## 3. 위반 유형

| 코드 | 유형 | 예 (BReady 현황) | 분리하면 무엇이 되나 |
|---|---|---|---|
| **V1** | 남의 Repository 주입 | `PlaceCandidateService`가 `CategoryStateRepository` 사용 | 남의 DB에 직접 붙음 → 불가능 |
| **V2** | 모듈을 넘는 JPA 연관 | `Trigger.plan`, `SwitchLog.fromCandidate` | 다른 DB의 FK → 불가능 |
| **V3** | 모듈을 넘는 JOIN | `SwitchLog ⋈ Decision ⋈ Trigger ⋈ Plan` | API 합성 또는 데이터 복제 필요 |
| **V4** | 모듈을 넘는 트랜잭션 | `SwitchService.executeSwitch` | Saga·보상 또는 경계 재설계 |
| **V5** | 소비자 패키지의 이벤트 | `stats.event.*`를 trigger가 import | 이벤트 스키마를 누가 소유하나 |
| **V6** | global → 도메인 의존 | `GlobalExceptionHandler`가 `S3ErrorCase` import | 공통 모듈이 모든 서비스에 의존 |

## 4. 래칫 — 위반은 늘지 않고 줄기만 한다

- ArchUnit `FreezingArchRule`로 **현재 위반 전부를 기준선으로 기록**한다. 기준선에 없는 위반이 새로 생기면 빌드가 실패한다.
- 위반을 하나 끊으면 기준선 파일에서 그 줄이 빠진다. 그 diff가 진척도다.
- 기준선 파일을 손으로 고쳐 새 위반을 "인정"하지 않는다. 피할 수 없는 위반이면 사용자 결정을 받고 ADR이나 설계서에 이유를 남긴다. (기준선 편집은 guard-write가 승인을 요구한다)
- 위치: 테스트 `src/test/java/com/bready/server/architecture/`, 기준선 `src/test/resources/archunit_store/`, 현재 수치 `docs/architecture/context-map.md` 11번.
- 기준선은 **규칙 설명 문자열**을 키로 위반을 기억한다. `because(...)` 문구를 바꾸면 새 규칙으로 취급된다.
- 새 규칙을 추가할 때만 `archunit.properties`의 `allowStoreCreation`을 잠깐 `true`로 바꿔 기준선을 만들고 바로 `false`로 돌린다(사용자 승인).
- 위반을 고쳤다면 `context-map.md` 11번 표의 숫자도 갱신한다.

## 5. 옛 코드를 건드릴 때

- 옛 위반이 있는 메서드를 고치더라도 **위반을 늘리지 않는다**(남의 리포지토리 호출을 하나 더 추가하는 식).
- 지금 끊을 수 없는 위반 옆에는 표식을 남긴다: `// BOUNDARY(V1): 2단계에서 plan 공개 API로 교체`.
- 경계를 끊는 작업은 2단계 조각에서 한다. 다른 조각에서 끼워서 하지 않는다(범위가 커지고 검증이 흐려진다).

## 6. "쪼개지 않는다"도 결정이다

- 한 트랜잭션·한 락으로 함께 바뀌어야 하는 것은 같은 경계 안에 둔다. 모든 모듈을 서비스로 만드는 것이 목표가 아니다.
- 경계를 정하거나 바꾸는 결정은 반드시 ADR로 남긴다(맥락: 어떤 트랜잭션·락·JOIN 때문에).