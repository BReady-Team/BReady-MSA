# 3단계 Strangler facade

> 2026-10-14. 이 단계의 결정은 [ADR-0003](../adr/0003-strangler-facade-gateway.md)에 모았다.

## 이 단계의 목표

서비스를 떼어내기 전에 앞단에 입구(Gateway)를 세운다. 지금은 모든 경로를 모놀리식으로 보낸다. 증명할 것은 하나다: **Gateway를 끼우기 전과 후의 응답이 같다.** 4단계에서 Place Catalog를 떼면 라우트만 바꾸고 클라이언트는 주소 하나만 계속 안다.

| 조각 | 내용 | PR | 상태 |
|---|---|---|---|
| 3-1 | 모놀리식을 `monolith/`로 이동(동작 변경 없음), 하네스 경로 수정 | PR1 `feat/gateway-routing` (#10) | 완료 |
| 3-2 | `gateway-service` + 전체 경로를 모놀리식으로 라우팅 + 전후 비교 검증 + CORS를 Gateway로 | PR2 `feat/gateway-service` (#12) | 완료 |
| 3-3 | compose `app` 프로필, CI, 옛 배포 자산 삭제, CodeRabbit·README 정리, ADR-0003 | PR3 `chore/local-compose` | 구현·검증 완료 |

## 왜 지금

모놀리식 안에서는 아직 Gateway가 없어서 생기는 문제가 없다. 4단계에서 생길 문제를 미리 막는 준비다.

- 클라이언트는 모놀리식 주소(`:8080`) 하나만 안다.
- Place Catalog를 떼면 `/api/v1/places/**`의 일부가 다른 프로세스로 가야 한다. 앞단 입구가 없으면 클라이언트가 주소 두 개를 알아야 한다. 외부 계약이 바뀐다.
- 그래서 떼기 전에 입구를 세우고 "끼워도 아무것도 바뀌지 않는다"를 먼저 증명한다.

팀 레포의 `nginx/bready.conf`도 앞단 프록시였지만, 같은 앱 두 벌을 번갈아 띄우는 blue/green 배포용(upstream 하나)이었다. 여기서 세우는 것은 경로마다 목적지가 다른 라우팅이다.

## 설계 (선택지 · 결정)

| 결정 | 선택지 | 결정 | 이유 |
|---|---|---|---|
| Gateway 구현 | Spring Cloud Gateway / nginx / 별도 Gateway 없이 모놀리식이 넘겨 줌 | **Spring Cloud Gateway** | 라우팅을 자동 테스트로 검증할 수 있다. 5단계 서킷브레이커, 6단계 JWT 필터가 같은 자리에 붙는다 |
| 레포 구조 | 루트가 모놀리식 + `include 'gateway'` / 루트 `settings.gradle`로 묶은 멀티 모듈 / 서비스마다 독립 Gradle 프로젝트 | **서비스마다 독립 프로젝트**(`gateway-service/`, `monolith/`, 4단계 `place-catalog-service/`). 루트에는 빌드 파일이 없다 | 루트가 모놀리식이면 떼어낸 서비스가 모놀리식의 부속처럼 보인다. 독립 프로젝트는 서로의 코드를 가져다 쓸 방법이 아예 없어 독립이 구조로 드러난다. 모놀리식은 지금 루트의 빌드 파일과 `src/`를 그대로 한 층 내리면 된다. 대가: 전체 빌드를 한 명령으로 돌릴 수 없고(하네스가 서비스 단위로 빌드), 빌드 설정이 서비스마다 중복된다. 공통 코드 문제는 4단계에서 실제로 생길 때 정한다 |
| 인증(JWT) | 모놀리식 그대로, Gateway는 통과 / Gateway에서도 JWT 검증 | **모놀리식 그대로** | 6단계 전에는 JWT를 건드리지 않는다 |
| CORS | 모놀리식 / Gateway / 양쪽 + 중복 헤더 제거 | **Gateway** (처음에는 모놀리식으로 정했다가 3-2 실제 기동 비교에서 바꿈) | Gateway는 preflight를 라우트로 보내지 않고 자기 CORS 설정으로 판정한다. 모놀리식에 두면 허용된 Origin의 preflight도 403이 된다. CORS 헤더는 한 곳에서만 붙인다(두 곳이면 `Access-Control-Allow-Origin`이 두 번 나가 브라우저가 거부한다). 양쪽에 두면 허용 Origin을 두 곳에서 똑같이 유지해야 한다. 대가: 브라우저가 `:8080`을 직접 부르면 CORS 헤더가 없다. Gateway가 유일한 입구라는 전제와 맞다 |
| 3-2 의존성 | 최소(gateway + actuator + test) / commerce-msa처럼 서킷브레이커·추적까지 | **최소** | 서킷브레이커는 5단계, 추적은 6단계에서 필요가 생길 때 |
| 3-2 코드 규칙 도구 | Spotless만 / Spotless + ArchUnit / 없음 | **Spotless만** | 포맷은 모든 Java에 같게. Gateway에는 도메인이 없어 구조 규칙이 검사할 대상이 없다 |
| 3-2 검증 | 가짜 모놀리식(WireMock) 자동 테스트 / 실제 기동 diff / 둘 다 | **둘 다** | 자동 테스트가 계약을 고정하고, 실제 기동 diff는 증거로 한 번 남긴다 |
| 3-2 X-Forwarded 헤더 | 끈 채로 / `trusted-proxies`로 켬 / `PreserveHostHeader` | **끈 채로**(Gateway 4.3 기본값) | 모놀리식에 Host로 URL을 만들거나 클라이언트 IP를 쓰는 코드가 0건이고, Gateway 앞에 다른 프록시가 없다. 켜지는 순간 테스트가 깨지게 고정해 두었다 |
| 3-2 하네스 | verify는 전부·Stop은 고친 서비스만 / 둘 다 전부 / 목록 직접 | **verify는 전부, Stop은 고친 서비스만** | `gradlew`가 있는 디렉터리를 서비스로 본다. 서비스가 늘어도 고칠 곳이 없고, 고친 서비스를 검사하지 않고 통과하는 일이 없다 |
| 3-3 로컬 실행 | 인프라만 compose / `app` 프로필로 앱까지 / 전부 기본으로 | **`app` 프로필** | `up`이면 인프라만(IDE 디버깅), `--profile app up`이면 전체. 모놀리식은 호스트에 포트를 열지 않아 Gateway가 유일한 입구가 된다. Dockerfile은 멀티 스테이지로 이미지 안에서 빌드 |
| 3-3 옛 배포 자산 | 삭제 / `legacy/`로 이동 / 그대로 | **삭제** | 팀 리소스(ghcr 이미지, EC2 네트워크)를 가리켜 이 레포에서는 실행할 수 없다. 보존은 git 히스토리와 원본 레포가 한다. `.coderabbit.yaml`은 지금 쓰는 리뷰 도구라 남기고 경로와 기준을 고쳤다 |
| 3-3 CI | 서비스 목록 직접 적기 / `gradlew` 있는 디렉터리 자동 탐색 | **자동 탐색** | 새 서비스를 목록에 빠뜨려 조용히 검사에서 빠지는 일이 없다. 검증 스크립트·Stop 훅과 같은 규칙 |
| 3-3 README | 새로 쓰고 원본은 `docs/`로 / 기존 위에 섹션 추가 | **새로 쓰고 원본 보존** | 이 레포의 결과물은 전환 과정이다. 이동 커밋과 새 README 커밋을 나눠 원본의 히스토리가 rename으로 이어지게 했다 |
| PR 분할 | 1개 / 3개 | **3개** | PR1은 "파일만 옮겼다", PR2는 "끼워도 응답이 같다", PR3는 정리와 기록. 이동과 새 기능이 섞이면 수백 줄의 이동 diff 속에서 진짜 변경을 찾을 수 없다 |

## 흐름 (before → after)

```
[before]
Client ──► :8080 Monolith
             JwtFilter → Controller → Service ─BEGIN─ DB ─COMMIT─
           ◄── CommonResponse

[after]
Client ──► :8000 Gateway ──(라우트: /** → http://localhost:8080)──► :8080 Monolith
             (JWT 안 봄, CORS 판정,           JwtFilter → Controller → Service ─BEGIN─ DB ─COMMIT─
              헤더·본문 그대로)
       ◄── 상태코드·헤더·본문 그대로 ◄──────────────────────────── CommonResponse
```

4단계에서는 라우트가 늘어난다. `/api/v1/places/candidates/**`는 Planning 소유라 모놀리식으로 가야 하므로 `/api/v1/places/**`보다 먼저 매칭돼야 한다. 라우트는 위에서부터 비교하고 먼저 맞은 것이 이긴다.

## 구현 파일

### 3-1 모놀리식을 `monolith/`로 이동

| 파일 | 변경 |
|---|---|
| `build.gradle` `settings.gradle` `gradlew` `gradlew.bat` `gradle/` `Dockerfile` `src/` `db/` | 내용 그대로 `monolith/` 아래로 이동 |
| `.gitattributes` | `/gradlew` → `gradlew`. 루트에 고정된 패턴이라 `monolith/gradlew`의 줄바꿈 규칙(LF)이 빠질 뻔했다 |
| `.claude/scripts/verify.sh`, `.claude/hooks/stop-verify-gate.sh` | Gradle을 `monolith/`에서 실행. 서비스가 하나라 디렉터리를 고정했다(서비스가 늘면 고친 파일의 서비스마다) |
| `.claude/hooks/guard-write.sh`, `post-edit-lint.sh`, `guard-bash.sh` | 경로 패턴을 `{서비스}/src/...`로. 특정 서비스에 고정하지 않아 새 서비스도 같은 보호를 받는다 |
| `.claude/hooks/test-hooks.sh` | 케이스 경로를 `monolith/` 아래로, 새 구조 케이스 11개 추가 |
| `CLAUDE.md`, `.claude/README.md`, `.claude/rules/`, `.claude/skills/`, `.claude/agents/`, `docs/architecture/context-map.md` | 현재 위치를 가리키는 경로만 수정. 지난 단계의 기록(2단계 설계서, 트러블슈팅)은 그 시점의 사실이라 두었다 |

### 3-2 gateway-service와 라우팅

| 파일 | 변경 |
|---|---|
| `gateway-service/` (`build.gradle`, `settings.gradle`, wrapper, `GatewayApplication`) | Spring Cloud 2025.0.0, `spring-cloud-starter-gateway-server-webflux`, actuator, Spotless. 포트 8000 |
| `gateway-service/src/main/resources/application.yml` | 라우트 하나 `Path=/** → ${MONOLITH_URI:http://localhost:8080}`, `globalcors` |
| `gateway-service/src/test/.../GatewayRoutingTest.java` | WireMock을 가짜 모놀리식으로: 경로·쿼리·본문·`Authorization`·에러 응답이 그대로 오가는지, Host가 바뀌고 X-Forwarded가 붙지 않는지 |
| `gateway-service/src/test/.../GatewayCorsTest.java` | preflight는 Gateway가 답하고 모놀리식에 가지 않는지, 거절 Origin은 403, 실제 응답에 `Access-Control-Allow-Origin`이 한 번만 |
| `monolith/.../global/config/security/SecurityConfig.java` | `.cors(cors -> cors.disable())`, `CorsConfigurationSource` 빈 제거 |
| `.claude/scripts/verify.sh` | `gradlew`가 있는 최상위 디렉터리마다 포맷·컴파일·테스트. 서비스가 0개면 실패 |
| `.claude/hooks/stop-verify-gate.sh` | 고친 Java가 속한 서비스만 검사. ArchUnit은 `architecture` 테스트가 있는 서비스만. `gradlew`가 없는 곳의 Java는 막지 않고 알림 |
| `.claude/hooks/test-hooks.sh`, `.claude/README.md` | 위 동작의 케이스 2개, 설명 |

### 3-3 로컬 compose · CI · 정리

| 파일 | 변경 |
|---|---|
| `monolith/Dockerfile`, `gateway-service/Dockerfile`, 각 `.dockerignore` | 멀티 스테이지(JDK에서 `bootJar`, JRE로 실행), Gradle 캐시 마운트 |
| `docker-compose-local.yml` | `app` 프로필에 `monolith`(포트 비공개, `DB_URL`·`REDIS_HOST`를 서비스 이름으로)와 `gateway`(`MONOLITH_URI=http://monolith:8080`, 8000 공개). 쓰지 않는 `version` 줄 제거 |
| `.github/workflows/ci.yml` | job 1이 `gradlew`가 있는 디렉터리로 서비스를 찾아 JSON으로 내보내고, job 2가 matrix로 서비스마다 `./gradlew build`. `build.gradle`·`src/`는 있는데 `gradlew`가 없으면 실패. 실패하면 테스트 리포트 업로드 |
| `.claude/scripts/verify.sh` | CI와 같은 조건으로 `gradlew` 없는 서비스 디렉터리를 실패로 |
| `deploy/`, `nginx/`, `docker-compose-{blue,green,prod,infra}.yml`, `monolith/.../application-cd.yml` | 삭제 |
| `CLAUDE.md`, `.claude/hooks/guard-bash.sh`, `guard-write.sh`, `test-hooks.sh`, `.claude/README.md` | 옛 배포 자산을 지키던 규칙과 케이스 제거 |
| `.coderabbit.yaml` | 리뷰 경로를 `**/src/**` 등 서비스 구조로, 리뷰 기준을 `.claude/rules/*.md`(code_guidelines)로. 규칙과 부딪히던 지침 삭제 |
| `README.md` → `docs/bready-original-README.md`, 새 `README.md` | 원본 이동(rename), MSA 전환 레포 소개 |
| `docs/adr/0003-strangler-facade-gateway.md` | 3단계 결정 모음 |

## 검증 증거

### 3-1

- 이동한 추적 파일 304개 전부 커밋된 내용과 바이트 단위로 같다(`git hash-object` 비교). `gradlew` 실행 권한 755 유지. 그래서 git은 전부 rename으로 기록한다.
- `monolith/`에서 포맷·컴파일·전체 테스트: 189개 통과, 실패 0(이동 전과 같다).
- 훅 회귀 테스트: 163개 통과, 실패 0.
- Stop 훅: 포맷이 깨진 임시 파일을 `monolith/src`에 만들자 `포맷 위반이 있다(monolith)`로 막았다. 확인 뒤 지웠다.

### 3-2

- Gateway 테스트 8개(라우팅 4, CORS 3, 컨텍스트 1) 통과. 전체 검증(`verify.sh`): gateway-service 8개, monolith 189개, 실패 0.
- CORS 테스트는 설정 전에 3개 중 2개가 실패하는 것(`expected:<200 OK> but was:<403 FORBIDDEN>`, `Access-Control-Allow-Origin not found`)을 먼저 확인했다. X-Forwarded 테스트는 `trusted-proxies: ".*"`를 잠깐 켜면 실패하는 것을 확인했다.
- 실제 기동 비교: 같은 요청 12종을 모놀리식 직접(`:8080`)과 Gateway 경유(`:8000`)로 보냈다. 목록·상세·생성·404·401 세 종류·400·로그인 9종은 상태·헤더·본문이 같다. 남은 차이는 상태 문구(`200` ↔ `200 OK`), 헤더 이름 대소문자, 연결 관리 헤더(`Connection: close`)로 클라이언트가 보는 결과에 영향이 없다.
- 첫 비교에서 허용된 Origin(`http://localhost:5173`)의 preflight가 `:8080`은 200, `:8000`은 403이었다. CORS를 Gateway로 옮긴 뒤 `:8000` preflight는 200 + `Access-Control-Allow-Origin: http://localhost:5173`, 실제 요청은 그 헤더가 한 줄이다.
- 훅 회귀 테스트 165개 통과. Stop 훅은 gateway의 포맷 위반·컴파일 오류와 monolith의 경계 위반(plan이 `PlaceRepository` 필드를 가짐)을 각각 막았다. 이 조각 전에는 gateway Java를 검사하지 않고 통과시켰다.

### 3-3

- `docker compose --profile app config --services`: 프로필 없이는 mysql·redis·prometheus·grafana, `app`이면 monolith·gateway까지.
- 이미지 빌드 77초(첫 빌드). 기동: MySQL·Redis가 healthy가 된 뒤 monolith 시작, Gateway 경유 첫 응답(401)까지 6초.
- 포트: `bready-gateway 0.0.0.0:8000->8000`, `bready-monolith 8080/tcp`. 호스트에서 `curl localhost:8080`은 연결 실패.
- 3-2의 비교 요청 12종을 컨테이너 Gateway(`:8000`)로 다시 보냈다. 3-2에서 `bootRun`으로 띄운 Gateway의 응답과 12종 모두 같다. 허용 Origin preflight는 200 + `Access-Control-Allow-Origin`.
- 같은 Gateway 이미지를 `MONOLITH_URI` 없이 띄우면 500, 로그 `Connection refused: localhost/127.0.0.1:8080`. 컨테이너 안의 `localhost`는 자기 자신이다.
- 옛 자산 삭제 후 `verify.sh`: gateway-service 8개, monolith 189개, 실패 0. 훅 회귀 테스트 158개 통과(지운 자산의 케이스 7개 제외).
- CodeRabbit의 옛 경로 패턴 `src/**`, `Dockerfile`(루트)에 맞는 파일은 3-1 이후 0개였다. 새 패턴은 `monolith/src` 296개, `gateway-service/src` 5개를 잡는다.
- CI의 서비스 찾기 단계를 워크플로우에서 그대로 뽑아 실행했다. 실제 레포: `["gateway-service","monolith"]`. `build.gradle`·`src/`는 있고 `gradlew`가 없는 `place-catalog-service`를 만든 가짜 레포: `::error::gradlew가 없어 빌드할 수 없는 서비스: place-catalog-service`, exit 1. `gradlew`를 넣으면 세 서비스가 목록에 들어간다. `verify.sh`도 같은 조건에서 실패한다.
- GitHub Actions에서의 실제 실행 결과는 확인하지 못했다(PR에서 확인).

## 워크스루 요약

### 3-1

- 파일을 옮기면 경로를 가정한 장치는 에러 없이 꺼진다. Stop 훅은 루트에 `gradlew`가 없으면 "검사할 수 없는 환경"으로 보고 통과시키고, 린트는 경로에서 모듈 이름을 못 뽑으면 경계 검사를 건너뛰고, guard-write는 `build.gradle` 경로가 안 맞으면 승인 없이 쓰게 둔다. 이동보다 "조용히 꺼진 보호 장치 찾기"가 이 조각의 실제 작업이었다.
- 회귀 테스트도 옛 경로로 케이스를 들고 있으면 계속 통과한다. 케이스 경로를 실제 경로로 옮기고, 서비스 디렉터리 아래의 빌드 파일·기준선 케이스를 추가했다.
- git은 이동을 기록하지 않는다. 사라진 파일과 생긴 파일의 내용 유사도로 rename을 추정한다. 이동 커밋에 내용 변경을 섞지 않은 이유다.

### 3-2

- Gateway는 리버스 프록시다. 클라이언트↔Gateway, Gateway↔모놀리식 연결이 따로 있고, Gateway가 받은 요청을 `uri`로 다시 보낸다. 라우트는 위에서부터 비교해 먼저 맞은 하나를 탄다. 지금은 `/**` 하나라 전부 모놀리식이고, 4단계부터 더 좁은 라우트를 위에 끼운다.
- 라우트 주소를 `${MONOLITH_URI:...}`로 뺀 덕분에 테스트가 WireMock 주소를 끼울 수 있고, 3-3 compose에서도 주소만 바꾼다.
- Gateway 4.3은 `trusted-proxies`를 설정하지 않으면 X-Forwarded·Forwarded 헤더를 붙이지 않는다. 클라이언트가 위조한 헤더를 그대로 믿던 문제 때문이다. 모놀리식이 받는 Host는 `localhost:8080`이다.
- preflight는 라우트를 타지 않는다. WebFlux 핸들러 매핑이 preflight를 만나면 그 자리에서 CORS 설정으로 판정한다. 그래서 CORS를 모놀리식에 두는 설계는 Gateway 뒤에서 성립하지 않았다. 자동 테스트에 preflight 시나리오가 없어 실제 기동 비교에서 처음 드러났다.
- 하네스는 `gradlew`가 있는 디렉터리를 서비스로 본다. 검사하지 못한 것은 출력으로 알린다. "건너뜀"과 "통과"가 화면에서 똑같으면 보호가 꺼진 것을 아무도 모른다.

### 3-3

- 컨테이너 안의 `localhost`는 자기 자신이다. 같은 compose 네트워크의 다른 컨테이너는 서비스 이름(`monolith`, `mysql`)과 내부 포트(3306)로 찾는다. 3-2에서 주소를 환경 변수로 뺀 것이 여기서 쓰였다.
- `expose`는 compose 네트워크 안에만, `ports`는 호스트에도 연다. 모놀리식을 `expose`로만 두어 Gateway를 우회할 길을 구조로 막았다.
- 서비스를 찾는 규칙(`gradlew`가 있는 최상위 디렉터리)이 검증 스크립트, Stop 훅, CI에서 같다. 처음에는 규칙만 같고 반응이 달랐다. Stop 훅은 `gradlew` 없는 곳의 Java를 알렸지만 CI는 그 디렉터리를 목록에 넣지 않고 초록을 냈다. `build.gradle`이나 `src/`가 있는데 `gradlew`가 없으면 CI와 검증 스크립트가 실패하도록 맞췄다.
- 경로를 가정한 설정은 파일을 옮기면 에러 없이 꺼진다. 3-1에서 하네스가 그랬고, 리뷰 봇 설정도 같았다(맞는 파일 0개).

## 알려진 한계

- 모놀리식이 죽으면 Gateway가 500을 Spring 기본 에러 형식으로 돌려준다(`Connection refused: localhost:8080`). 의미상 503이 맞고 `CommonResponse` 형식도 아니다. 타임아웃·서킷브레이커와 함께 5단계에서 다룬다.
- 카카오·네이버 `redirect-uri` 기본값이 `http://localhost:8080/...`(`monolith/src/main/resources/application.yml`)이라 OAuth 콜백은 Gateway를 거치지 않는다. 인증은 6단계에서 다룬다.
- 브라우저가 `:8080`을 직접 부르면 CORS 헤더가 없다. 프론트엔드는 Gateway(`:8000`)를 써야 한다.
- 허용되지 않은 Origin의 거절 응답(403)은 Gateway가 만들어 본문과 보안 헤더(`X-Frame-Options` 등)가 없다. 전에는 모놀리식이 `Invalid CORS request`와 보안 헤더를 붙였다. 브라우저가 버리는 응답이라 결과는 같다.
- `app` 모드에서는 Prometheus(`monitoring/prometheus.yml`)가 `host.docker.internal:8080`을 긁는데 모놀리식이 호스트에 포트를 열지 않아 수집되지 않는다. 개발 모드(`bootRun`)에서만 수집된다. 관측성 단계에서 compose 네트워크 안의 주소로 바꾼다.
- 첫 이미지 빌드가 약 1분 20초 걸린다(의존성 다운로드). 이후는 Gradle 캐시 마운트로 줄어든다.
