# BReady-MSA

팀 프로젝트로 만든 일정 플래너 **BReady**(Spring Boot 모놀리식)를 **Strangler Fig 방식으로 MSA로 전환**하는 프로젝트의 레포지토리입니다.

목표는 서비스 개수를 늘리는 것이 아닌, 이미 엉켜 있는 실제 코드를 기능을 깨뜨리지 않고 한 조각씩 떼어내면서, 각 조각을 **왜 그렇게 나눴는지 설명할 수 있는 상태**로 남기는 것

- 원본: [BReady-Backend](https://github.com/BReady-Team/BReady-Backend)

## 지금까지

| 항목 | 출발점 | 현재 | 근거 |
|---|---|---|----|
| 컨텍스트 사이의 Repository·Entity 직접 사용 | 274건 | **0건** | ArchUnit 기준선, [context-map.md 11번](docs/architecture/context-map.md) |
| 공개 API(`{module}.api`) 밖으로 다른 컨텍스트를 쓰는 곳 | 17건 | **0건** | 같은 문서 |
| 공통 패키지(`global`)가 도메인을 아는 곳 | 8건 | 6건 | 남은 6건은 인증이며 6단계에서 |
| 테스트 | 기준 없음 | monolith 189개 · gateway 8개 | 통합 테스트 8종(응답 계약 5종, 인가, 동시성, 핵심 흐름) |
| 찾아서 고친 결함 | | 3건 | 남의 토큰으로 후보·트리거·전환 API 6개가 200을 돌려주던 인가 누락, 삭제된 후보가 대표가 되던 경쟁 상태 2건. 셋 다 실패하는 테스트로 먼저 재현한 뒤 수정 |
| Gateway를 끼우기 전과 후의 응답 | | 같은 요청 12종의 상태 코드가 모두 같음 | 본문·헤더까지 같은 것은 9종이고, 나머지 3종은 CORS 거절 응답의 본문과 `Vary` 헤더 차이입니다. 처음 비교에서 허용된 Origin의 preflight가 403이 되는 문제를 찾아서 수정 |

경계 위반은 "줄기만 하고 늘지 않게" 빌드로 강제하고, 기준선에 없는 새 위반이 생기면 테스트가 실패합니다.

## 진행 단계

| 단계 | 내용 | 상태 | 문서 |
|---|---|---|---|
| 0 | 안전망: 외부 키 없이 로컬 기동, Testcontainers 통합 테스트 | 완료 | [학습일지](docs/LEARNING_JOURNEY.md) |
| 1 | 경계 지도: 바운디드 컨텍스트 6개와 떼어낼 순서 확정 | 완료 | [설계서](docs/design/1-boundary-map.md) · [ADR-0001](docs/adr/0001-bounded-contexts-and-extraction-order.md) |
| 2 | 모듈러 모놀리스: JPA 연관을 ID 참조로, 컨텍스트 사이는 공개 API로만 | 완료 | [설계서](docs/design/2-modular-monolith.md) · [ADR-0002](docs/adr/0002-public-api-package-per-module.md) |
| 3 | Strangler facade: Gateway를 앞에 세우고 모든 요청을 모놀리식으로 | 마무리 중 | [설계서](docs/design/3-strangler-facade.md) |
| 4 | 첫 추출: Place Catalog (공유 DB → 스키마 분리 → DB 분리) | 예정 | |
| 5 | 다음 추출(Recommendation, Stats) + 필요해진 곳에만 Kafka·Outbox·서킷브레이커 | 예정 | |
| 6 | 인증 분리, 관측성 | 예정 | |

인프라는 미리 깔지 않고, 메시지 브로커나 서킷브레이커는 그것 없이 문제가 생기는 단계에서, 그 문제를 재현한 뒤에 추가 

## 구조

```
BReady-MSA/
├── gateway-service/        Spring Cloud Gateway (:8000). 라우팅과 CORS
├── monolith/               기존 BReady 애플리케이션 (:8080). 아직 떼어내지 않은 전부
├── docker-compose-local.yml
├── docs/                   설계서 · ADR · 경계 지도 · 학습일지 · 트러블슈팅
└── .github/workflows/      서비스마다 빌드·테스트하는 CI
```

**요청 흐름 (3단계)**

```
Client ──► gateway-service :8000 ──(/** 전부)──► monolith :8080 ──► MySQL · Redis
           CORS 판정                              JWT 인증
           라우트 매칭                            컨텍스트 6개
```

**바운디드 컨텍스트**

| 컨텍스트 | 패키지 | 떼어낼 순서 |
|---|---|--------|
| Place Catalog | `place` | 1번째    |
| Recommendation | `recommendation` | 2번째    |
| Stats | `stats` | 3번째    |
| Planning | `plan`, `trigger` | 쪼개지 않고, 플랜·후보·트리거가 한 트랜잭션과 한 락으로 함께 바뀌도록 변경 |
| Identity | `auth`, `user` | 인증 분리  |
| File | `s3` |        |

컨텍스트 사이는 내놓는 쪽의 `{module}.api` 패키지(인터페이스, 값 record, enum)로만 오가고, 서비스로 떼어낼 때 이 패키지가 그대로 HTTP 계약

## 실행

필요한 것: JDK 21, Docker. 외부 API 키(OpenAI, S3, 카카오, 네이버)는 없어도 가능 

**전체를 컨테이너로**

```bash
docker compose -f docker-compose-local.yml --profile app up --build
curl -i localhost:8000/api/v1/plans          # 401: Gateway를 지나 모놀리식의 JWT 필터까지 도달
```

호스트에 열리는 앱 포트는 8000 하나로 모놀리식(8080)은 compose 네트워크 안에서만 열려 있어서, Gateway를 거치지 않고는 닿지 않습니다.

```bash
docker compose -f docker-compose-local.yml up -d mysql redis
cd monolith && ./gradlew bootRun              # :8080
cd gateway-service && ./gradlew bootRun       # :8000
```

**테스트**

```bash
cd monolith && ./gradlew test                 # Testcontainers
cd gateway-service && ./gradlew test          # WireMock이 가짜 모놀리식 역할
```

## 무엇이 규칙을 지키게 하나

| 장치 | 하는 일 |
|---|---|
| ArchUnit 래칫 | 컨텍스트 경계, 레이어 방향, 코딩 규칙을 검사합니다. 옛 위반은 기준선에 기록되고, 새 위반은 빌드를 실패시킵니다 |
| 응답 계약 통합 테스트 | 구조를 바꾸기 전에 API 응답을 고정합니다. 경계를 끊은 뒤에도 같은 JSON이 나오는지 확인합니다 |
| CI (GitHub Actions) | `gradlew`가 있는 디렉터리를 찾아 서비스마다 포맷·컴파일·테스트를 돌립니다. 서비스를 추가해도 목록을 고칠 필요가 없습니다 |
| 코드 규칙 문서 | [.claude/rules/](.claude/rules/)의 10개 파일이 규칙의 원본입니다. 작업에 쓰는 Claude Code 훅과 CodeRabbit 리뷰가 같은 파일을 기준으로 삼습니다 |

## 문서

| 문서 | 내용 |
|---|---|
| [docs/LEARNING_JOURNEY.md](docs/LEARNING_JOURNEY.md) | 조각마다 무엇이 문제였고, 무엇을 놓고 고민했고, 무엇을 잘못 알았는지 |
| [docs/architecture/context-map.md](docs/architecture/context-map.md) | 출발점의 경계 지도. 누가 누구의 테이블·엔티티를 쓰는지, 위반 건수의 변화 |
| [docs/design/](docs/design/) | 단계별 설계서. 선택지와 결정, 요청 흐름 before/after, 검증 증거 |
| [docs/adr/](docs/adr/) | 되돌리기 어려운 결정의 근거 |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | 막혔던 것들. 증상 원문, 틀렸던 가설, 원인, 해결 |

## 기술

Java 21 · Spring Boot 3.5.7 · Spring Cloud Gateway (2025.0) · Spring Data JPA · MySQL 8.0 · Redis 7.2 · Testcontainers · WireMock · ArchUnit · Docker Compose · GitHub Actions
