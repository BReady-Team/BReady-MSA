# 규칙 지도

`.claude/rules/`는 "무엇이 옳은가"의 **유일한 원본**이다. 스킬·훅·에이전트는 규칙을 복사하지 않고 `rules/파일.md` 몇 번 항목인지로 가리킨다.
규칙을 바꾸면 이 파일 하나만 고치면 된다.

## 작업 유형별로 읽을 파일

| 작업 | 먼저 읽을 규칙 | 스킬 |
|---|---|---|
| API(엔드포인트) 추가·수정 | `controller-api` · `dto` · `exception` · `service-transaction` | `/new-api` |
| 엔티티·enum·값 객체 작성·수정 | `entity` · `exception` | `/domain-model` |
| 서비스 로직, 트랜잭션, 락, 이벤트 | `service-transaction` · `entity` | — |
| 쿼리 작성, N+1, 페이징 | `repository-query` | — |
| 테스트 작성 | `testing` | `/write-test` |
| 옛 코드 정리 | `java-style` · 해당 계층 규칙 · `testing` | `/refactor-legacy` |
| 모듈 간 호출, 서비스 추출 | `msa-boundary` · `service-transaction` | (2단계에서 추가) |
| 로그 추가 | `logging` | — |
| 모든 Java 코드 | `java-style` | — |

## 파일 목록

| 파일 | 다루는 것 |
|---|---|
| [java-style.md](java-style.md) | 네이밍, 메서드 크기, null, Stream, Lombok 허용 목록, 주석, import |
| [entity.md](entity.md) | 엔티티 골격, 정적 팩토리, 상태 변경 메서드, 불변식, 연관관계 vs ID 참조, soft delete, 시간 |
| [service-transaction.md](service-transaction.md) | 서비스 책임, 트랜잭션 선언, 외부 I/O, 동시성, 이벤트, Clock |
| [controller-api.md](controller-api.md) | 위임만 하는 컨트롤러, `{Domain}Api` 인터페이스, URL·상태 코드, 검증 |
| [dto.md](dto.md) | record DTO, 이름, 검증, `from()` 변환, 민감 필드 |
| [exception.md](exception.md) | ErrorCase 체계, 코드 형식, 던지는 법, 잡는 법, 핸들러 |
| [repository-query.md](repository-query.md) | 쿼리 메서드, JPQL, 프로젝션, fetch join, 락, native |
| [testing.md](testing.md) | 테스트 계층, 이름, fixture, BDD, 슬라이스, Testcontainers |
| [msa-boundary.md](msa-boundary.md) | 모듈 경계, 위반 유형, 래칫 운영 |
| [logging.md](logging.md) | 로그 레벨, 금지 대상, 형식 |

## 강제 수준 표기

각 규칙 옆의 표시는 어디서 강제되는지를 뜻한다.

| 표시 | 의미 |
|---|---|
| **[빌드]** | ArchUnit·Spotless로 빌드 실패 |
| **[린트]** | 편집 직후 훅이 알림 |
| **[리뷰]** | 기계로 못 잡음. 작성할 때 지키고 `convention-reviewer`가 검토 |

## 규칙끼리 부딪히면

1. `CLAUDE.md` 절대 규칙
2. `msa-boundary`
3. 해당 계층 규칙(`entity`·`service-transaction`·`controller-api`…)
4. `java-style`

그래도 애매하면 추측하지 말고 사용자에게 묻는다.

## 옛 코드와 새 규칙

BReady 옛 코드는 이 규칙과 다른 곳이 많다(근거는 각 파일의 "BReady 현황"). 정리 원칙:
- **새 코드는 처음부터 규칙대로.** 주변 옛 코드를 흉내 내지 않는다.
- **옛 코드는 건드릴 때 정리**(보이스카우트). 단, 그 동작을 지키는 테스트가 있을 때만(`/refactor-legacy`).
- 정리하지 않고 지나가는 옛 위반은 늘리지 않는다.
- 규칙의 "BReady 현황" 항목은 근거 위치(`파일:줄`)를 단다. 그 코드를 고치면 **해당 항목을 지운다**. 포맷·리팩터링으로 줄이 밀렸으면 번호를 갱신한다. 낡은 근거는 규칙 전체를 믿지 못하게 만든다.
