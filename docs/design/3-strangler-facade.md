# 3단계 Strangler facade

> 진행 중 (2026-10-14 시작). 이 단계의 결정은 단계가 끝날 때 ADR-0003으로 남긴다.

## 이 단계의 목표

서비스를 떼어내기 전에 앞단에 입구(Gateway)를 세운다. 지금은 모든 경로를 모놀리식으로 보낸다. 증명할 것은 하나다: **Gateway를 끼우기 전과 후의 응답이 같다.** 4단계에서 Place Catalog를 떼면 라우트만 바꾸고 클라이언트는 주소 하나만 계속 안다.

| 조각 | 내용 | PR | 상태 |
|---|---|---|---|
| 3-1 | 모놀리식을 `monolith/`로 이동(동작 변경 없음), 하네스 경로 수정 | PR1 `feat/gateway-routing` (#10) | 완료 |
| 3-2 | `gateway-service` + 전체 경로를 모놀리식으로 라우팅 + 전후 비교 검증 + CORS를 Gateway로 | PR2 `feat/gateway-service` | 구현·검증 완료 |
| 3-3 | 로컬 compose에 Gateway 추가, 옛 배포 자산 정리 | PR3 `chore/local-compose` | 대기 |

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

## 알려진 한계

- 모놀리식이 죽으면 Gateway가 500을 Spring 기본 에러 형식으로 돌려준다(`Connection refused: localhost:8080`). 의미상 503이 맞고 `CommonResponse` 형식도 아니다. 타임아웃·서킷브레이커와 함께 5단계에서 다룬다.
- 카카오·네이버 `redirect-uri` 기본값이 `http://localhost:8080/...`(`monolith/src/main/resources/application.yml`)이라 OAuth 콜백은 Gateway를 거치지 않는다. 인증은 6단계에서 다룬다.
- 브라우저가 `:8080`을 직접 부르면 CORS 헤더가 없다. 프론트엔드는 Gateway(`:8000`)를 써야 한다.
- 허용되지 않은 Origin의 거절 응답(403)은 Gateway가 만들어 본문과 보안 헤더(`X-Frame-Options` 등)가 없다. 전에는 모놀리식이 `Invalid CORS request`와 보안 헤더를 붙였다. 브라우저가 버리는 응답이라 결과는 같다.
