# 트러블슈팅

> 막힌 순간 바로 기록한다. 우회하지 않고 원인을 이해한 것만 "해결"로 적는다.
> 형식: **증상(원문 로그) → 원인을 좁힌 과정 → 원인 → 해결 → 배운 것**
> 좁힌 과정에는 틀렸던 가설도 적는다. 답만 적으면 다음에 비슷한 문제를 만났을 때 쓸 수 있는 것이 없다.

| # | 날짜 | 증상 한 줄 | 단계 |
|---|---|---|---|
| [T-001](#t-001) | 2026-10-08 | `./gradlew` 실행 시 permission denied | 출발점 |
| [T-002](#t-002) | 2026-10-08 | `contextLoads` 실패: `cloud.aws.credentials.access-key` 플레이스홀더 해석 불가 | 0-1 |
| [T-003](#t-003) | 2026-10-12 | Testcontainers 컨테이너 시작 실패: `client version 1.32 is too old` | 0-2 |
| [T-004](#t-004) | 2026-10-12 | 테스트는 통과하는데 `Table 'test.switch_logs' doesn't exist` 경고가 쏟아짐 | 0-2 |
| [T-005](#t-005) | 2026-10-12 | 락을 먼저 잡도록 고쳤는데도 삭제된 후보가 대표로 지정됨 | 2-3 |
| [T-006](#t-006) | 2026-10-13 | 테스트는 전부 통과하는데 ArchUnit 경계 기준선 9개가 통째로 새로 만들어짐 | 2-6 |
| [T-007](#t-007) | 2026-10-13 | 통계 API에 잘못된 `period`를 주면 에러 코드 6003 대신 본문 없는 400 | 2-6 |
| [T-008](#t-008) | 2026-10-14 | Gateway를 거친 요청에 `X-Forwarded-*` 헤더가 하나도 없음 | 3-2 |
| [T-009](#t-009) | 2026-10-14 | 모놀리식이 허용하는 Origin인데 Gateway를 거치면 CORS preflight가 403 | 3-2 |
| [T-010](#t-010) | 2026-10-14 | Gateway 첫 요청 때 `MacOSDnsServerAddressStreamProvider` ERROR 로그 | 3-2 |

---

## T-001

**증상**
```
$ ./gradlew test
(eval):1: permission denied: ./gradlew
```

**원인**: git에 `gradlew`가 실행 권한 없이(`100644`) 저장돼 있었다. `git ls-files -s gradlew`로 확인했다. 원본 레포를 클론할 때부터 그랬다.

**해결**: `chmod +x gradlew` 후 커밋. git은 파일 내용뿐 아니라 실행 권한 비트도 기록하므로(`core.fileMode=true`) 모드 변경(`100644 → 100755`)이 커밋에 남는다.

**배운 것**: git이 추적하는 권한은 실행 비트 하나뿐이다. 윈도우에서 만든 레포는 이 비트가 빠진 채 올라오는 일이 흔하다.

---

## T-002

**증상**
```
BReadyApplicationTests > contextLoads() FAILED
    Caused by: PlaceholderResolutionException
Could not resolve placeholder 'cloud.aws.credentials.access-key' in value "${cloud.aws.credentials.access-key}"
```
`./gradlew test`에서 100개 중 99개는 통과하고 이 1개만 실패했다.

**원인을 좁힌 과정**

1. 왜 1개만 실패하는지부터 봤다. 통과한 99개는 Mockito 단위 테스트와 컨트롤러 슬라이스라 스프링 빈 전체를 만들지 않는다. 실패한 1개만 `@SpringBootTest`로 앱 전체를 띄운다. 즉 테스트의 문제가 아니라 "이 레포는 클론한 그대로는 뜨지 않는다"는 뜻이었다.
2. 값이 어디에 있어야 하는지 찾았다. `src/main/resources`에 `application.yml`이 없었다. `.gitignore`에 걸려 있었고, 실제 키가 들어 있어서 팀이 무시해 둔 파일이었다. 테스트 설정에는 S3 항목이 없었다.
3. S3만 고치면 끝인지 확인했다. 설정 파일을 고치기 전에 더미 환경변수를 한 겹씩 채워 가며 돌렸다.

| 채운 값 | 결과 |
|---|---|
| 없음 | `Could not resolve placeholder 'cloud.aws.credentials.access-key'` |
| `CLOUD_AWS_*` 5개 | `OpenAI API key must be set` (`openAiAudioSpeechModel`) |
| 위 + `SPRING_AI_OPENAI_API_KEY` | 통과 |

S3 뒤에 OpenAI가 한 겹 더 있었다. 이 실험을 하지 않았다면 S3를 고친 뒤 다시 막혔을 것이다.

**원인**

두 가지가 겹쳤다.

- 설정 값은 빈을 만드는 시점에 해석된다. `S3Config`가 `@Value`로 키를 주입받는데 값이 어디에도 없어서 기동이 멈췄다. S3를 호출하지 않아도 마찬가지다.
- OpenAI 키는 우리 코드가 아니라 Spring AI 자동 설정이 요구했다. `recommendation.ai.enabled=false`로 AI 추천을 꺼 두었지만, 그 스위치는 우리가 조건을 붙인 빈만 끄고 라이브러리가 자동으로 만드는 빈은 끄지 못한다.

**해결**

키 없는 기본 `application.yml`을 커밋했다. 모든 외부 값이 `${환경변수:로컬더미값}` 형태라 환경변수가 없어도 빈이 만들어진다. 실제 키는 gitignore된 `application-local.yml`에 두고 `local` 프로파일로만 읽는다.

결과: 키 관련 환경변수가 하나도 없는 셸에서 127개 전부 통과, 실제 기동 후 가입·로그인·플랜 생성까지 정상.

**배운 것**

- 테스트 대부분이 통과한다고 앱이 뜬다는 뜻은 아니다. 앱 전체를 띄우는 테스트가 하나는 있어야 하고, 그 테스트가 깨져 있으면 가장 먼저 고쳐야 한다.
- 에러 하나를 고치기 전에 그 뒤에 무엇이 더 있는지 먼저 확인하면 왕복이 줄어든다. 환경변수로 임시로 채워 보는 방법은 파일을 고치지 않고도 할 수 있다.
- 새 설정 값을 추가할 때 기본값 없는 `${환경변수}`를 쓰지 않는다. 빠뜨리면 `contextLoads`가 환경변수 없는 곳에서 바로 실패한다. 다만 내 환경에 그 변수가 있으면 내 테스트는 통과하므로, 완전히 막으려면 깨끗한 환경에서 도는 CI가 필요하다.
- commerce-msa에서는 처음부터 설정을 외부화해서 이 문제를 겪지 않았다. 운영 서버에만 있는 파일에 기대는 프로젝트는 그 파일이 없는 곳에서 뜨지 않는다.

---

## T-003

**증상**
```
BReadyApplicationTests > initializationError FAILED
    Caused by: org.testcontainers.containers.ContainerLaunchException: Container startup failed for image mysql:8.0
    Caused by: com.github.dockerjava.api.exception.BadRequestException: Status 400:
      {"message":"client version 1.32 is too old. Minimum supported API version is 1.44, please upgrade your client to a newer version"}
```
Testcontainers를 처음 붙이고 `./gradlew test`를 돌리자 컨테이너가 뜨지 않았다. Docker 29.2.1, Testcontainers 1.21.3.

**원인을 좁힌 과정**

1. 맨 위 메시지는 `Container startup failed for image mysql:8.0`이라 이미지나 MySQL 설정 문제처럼 보인다. 하지만 `docker run mysql:8.0`은 터미널에서 정상으로 떴으므로 이미지 문제가 아니었다.
2. 예외 체인의 가장 아래를 읽었다. HTTP 400이고 메시지는 "client version 1.32 is too old"였다. 컨테이너가 뜨다가 죽은 것이 아니라, 컨테이너를 만들어 달라는 요청 자체를 Docker가 거절한 것이다.

**원인**

Docker 엔진과 클라이언트는 HTTP API로 대화하고 요청마다 API 버전을 붙인다. Testcontainers가 쓰는 docker-java 클라이언트는 기본으로 1.32로 요청하는데, Docker 29부터 엔진이 1.44 미만의 요청을 받지 않는다. 터미널의 `docker` 명령은 최신 버전으로 요청하므로 문제가 없었다.

**해결**

`build.gradle`의 test 태스크에 `systemProperty 'api.version', '1.44'`를 넣었다. docker-java가 이 시스템 프로퍼티를 읽어 요청 버전을 올린다. 테스트 JVM에만 적용되도록 test 태스크에 넣었다.

결과: 컨테이너가 뜨고 130개 통과.

**배운 것**

- 예외 체인은 맨 위가 아니라 맨 아래 `Caused by`가 원인이다. 위쪽은 "무엇을 하다가"이고 아래쪽이 "왜"다.
- "터미널에서는 되는데 코드에서는 안 된다"면 두 클라이언트가 다르다는 뜻이다. 같은 Docker를 쓰더라도 CLI와 라이브러리는 서로 다른 클라이언트다.
- 이 문제는 하네스를 만들 때 알려진 함정으로 미리 적어 두었다. 그래서 메시지를 보고 바로 원인을 알았다. Testcontainers 버전을 올려 기본값이 바뀌면 이 설정은 지운다.

---

## T-004

**증상**
```
Caused by: java.sql.SQLSyntaxErrorException: Table 'test.switch_logs' doesn't exist
Caused by: java.sql.SQLSyntaxErrorException: Table 'test.triggers' doesn't exist
Caused by: java.sql.SQLSyntaxErrorException: Table 'test.user_profiles' doesn't exist
```
테스트를 MySQL 컨테이너로 옮긴 직후, 130개가 전부 통과하는데 테스트 출력에 이런 예외가 테이블마다 찍혔다.

**원인을 좁힌 과정**

1. 테스트가 통과하므로 기능 문제는 아니었다. 하지만 통과하는 테스트의 로그에 예외가 섞여 있으면 진짜 문제가 생겼을 때 묻힌다. 그래서 그냥 넘기지 않았다.
2. 언제 찍히는지 봤다. 테스트 실행 중이 아니라 스프링 컨텍스트가 뜨면서 스키마를 만드는 시점이었다.
3. 설정을 봤다. 테스트 프로파일의 `ddl-auto`가 `create-drop`이었다.

**원인**

`create-drop`은 시작할 때 먼저 기존 스키마를 지우고 새로 만든다. 지우는 단계에서 Hibernate는 FK 제약부터 떼어내려고 `alter table ... drop foreign key ...`를 실행하는데, 컨테이너의 DB는 방금 만들어진 빈 DB라 그 테이블이 없다. MySQL이 오류를 돌려주고, Hibernate는 지우는 단계의 오류를 경고로만 남기고 계속 진행한다. 그래서 테스트는 통과하고 로그만 지저분해졌다.

**해결**

테스트 프로파일의 `ddl-auto`를 `create-only`로 바꿨다. 지우는 단계 없이 만들기만 한다. 컨테이너는 매번 빈 DB로 뜨고 테스트가 끝나면 통째로 버려지므로 지울 것이 애초에 없다.

결과: 경고 0건, 130개 통과.

**배운 것**

- `ddl-auto` 값은 "DB가 어떤 상태에서 시작하는가"에 맞춰 고른다. 매번 빈 DB면 `create-only`, 남아 있는 DB를 재사용하면 `create-drop`이나 `update`가 맞다. H2 인메모리 때 쓰던 값을 그대로 가져온 것이 원인이었다.
- 통과하는 테스트의 경고도 원인을 확인한다. 무해한 것으로 판명되면 없애서, 로그에 예외가 보이면 곧 문제라는 상태를 유지한다.

---

## T-005

**증상**

전환과 후보 삭제가 겹치면 삭제된 후보가 대표로 지정되는 버그가 있었다. 전환이 후보를 확인한 뒤에 락을 잡기 때문이라고 보고, 락을 먼저 잡도록 순서를 바꿨다. 그런데 재현 테스트가 그대로 실패했다.

```
CandidateDeletionRaceIntegrationTest > 전환 대상 후보가 전환 도중 삭제되면 TO_CANDIDATE_NOT_FOUND이고 대표는 바뀌지 않는다 FAILED
    expected: 404
     but was: 200
```

삭제는 200으로 끝났는데 전환도 200으로 성공했다. 삭제된 후보가 대표가 된 것이다.

**원인을 좁힌 과정**

1. 먼저 버그를 매번 재현하는 테스트를 만들었다. 요청 두 개를 동시에 보내는 방식은 순서가 운에 달려 있어서 쓰지 않았다. 테스트가 `category_states` 락을 먼저 쥐고, 삭제 요청과 전환 요청을 차례로 보내 둘 다 락을 기다리게 한 뒤 락을 풀었다. 삭제가 먼저 커밋되고 전환이 그 뒤에 락을 얻는 순서가 매번 만들어진다.
2. 이 테스트로 수정안 세 가지를 차례로 돌렸다.

| 수정안 | 전환 응답 | 결과 |
|---|---|---|
| 수정 전 | 200 | 버그 재현 |
| A. 락 조회를 후보 확인보다 위로 옮김 | 200 | 고쳐지지 않음 |
| B. A + 후보 확인을 잠금 읽기(`FOR UPDATE`)로 | 400 (카테고리 불일치) | 막았지만 에러가 틀림 |
| C. A + 격리 수준을 READ COMMITTED로 | 404 (후보 없음) | 통과 |

3. A가 실패한 것을 보고 "락을 잡았는데 왜 옛 데이터를 보는가"로 질문을 바꿨다. 전환이 락을 얻은 시점에 삭제는 이미 커밋돼 있었다. 그런데도 후보가 살아 있다고 나왔다면, 조회가 최신 데이터를 보지 않는다는 뜻이다.

**원인**

MySQL의 기본 격리 수준은 REPEATABLE READ다. 이 수준에서 트랜잭션은 처음 일반 조회를 하는 순간의 데이터 모습(스냅샷)을 끝까지 본다. 전환은 맨 처음에 결정을 조회하고, 그때 스냅샷이 만들어진다.

```
전환                                         삭제
 1. 결정 조회        ← 스냅샷 생성 (후보: 살아 있음)
                                              2. 락 획득 → 후보 삭제 → 커밋
 3. 락 획득
 4. 후보 확인        ← 스냅샷을 본다: 살아 있음     실제로는 삭제됨
 5. 대표 변경
```

락은 두 트랜잭션이 동시에 들어가지 못하게 줄을 세울 뿐이다. 줄을 서서 들어간 뒤 무엇을 보느냐는 격리 수준이 정한다. 잠금 읽기는 스냅샷이 아니라 최신 커밋을 보지만, 일반 조회는 스냅샷을 본다.

B에서 에러가 틀린 것도 같은 이유였다. 후보 확인은 잠금 읽기로 바꿔 최신을 봤지만, "없는 것인지 다른 카테고리의 것인지"를 가르는 다음 조회가 일반 조회라서 다시 스냅샷을 봤다.

**해결**

`executeSwitch`를 두 가지로 고쳤다. 둘 다 있어야 한다.

- 락 조회를 후보 확인보다 앞으로 옮겼다.
- `@Transactional(isolation = Isolation.READ_COMMITTED)`로 바꿨다. 조회할 때마다 그 시점의 최신 커밋을 본다.

B 대신 C를 고른 이유는 락이 늘지 않기 때문이다. B는 후보 행에 락을 더 잡는데, 카테고리 타입 변경은 후보를 먼저 잠그고 상태를 나중에 잠근다. 전환이 상태를 먼저 잠그고 후보를 나중에 잠그면 순서가 엇갈려 데드락이 날 수 있다. (이 데드락은 재현하지 않았고 락 순서를 보고 판단했다.)

고치는 중에 대표 지정(`setRepresentative`)에도 같은 문제가 있는 것을 찾았다. 같은 방식으로 재현하고, 락을 얻은 뒤 후보가 살아 있는지 다시 확인하도록 고쳤다. 여기서도 다시 확인하는 코드만 넣고 격리 수준을 그대로 두면 테스트가 실패하는 것을 확인했다.

결과: 재현 테스트 2개 통과, 전체 137개 통과.

**배운 것**

- 락과 격리 수준은 서로 다른 문제를 푼다. 락은 순서를 정하고, 격리 수준은 무엇을 볼지를 정한다. "검증과 변경을 같은 락 안에서"라는 원칙에는 "그 검증이 최신 데이터를 봐야 한다"가 숨어 있다.
- 동시성 버그는 고쳤다고 믿기 전에 재현부터 한다. 재현 테스트가 없었다면 A안을 적용하고 고쳤다고 적었을 것이다. 실제로 그렇게 설명한 문서가 이미 있었다.
- 재현할 때는 순서를 운에 맡기지 않는다. 락을 직접 쥐고 요청을 대기시키면 원하는 순서를 매번 만들 수 있다. 대기 중인지는 `performance_schema.data_lock_waits`로 확인했다.
- 한 곳에서 찾은 버그의 모양(읽고, 락 잡고, 다시 확인하지 않음)은 같은 락을 쓰는 다른 곳에도 있을 가능성이 높다. 같은 모양을 찾아보다가 두 번째 버그를 발견했다.
- 격리 수준을 메서드 하나만 낮추면 읽는 사람이 놓치기 쉽다. 이유를 주석으로 남겼고, 그 한 줄을 지우면 재현 테스트가 실패한다.


## T-006

**증상**

enum 두 개의 패키지를 옮긴 뒤 테스트를 돌렸다. 경계 규칙(`ModuleBoundaryTest`)은 전부 통과했는데, 줄어야 할 기준선이 그대로였다.

```
ModuleBoundaryTest   tests=9 failures=0
53fdb0ec-…  105 -> 105     (Recommendation, 40건이 줄어야 함)
e376568c-…   94 -> 94      (Stats, 31건이 줄어야 함)
```

`git status`에는 처음 보는 기준선 파일 9개가 새로 생겨 있었다.

**원인을 좁힌 과정**

1. 테스트가 예전 클래스를 본 것이라고 의심했다. 틀렸다. 같은 실행에서 다른 규칙(`CodingRulesTest`)은 옮긴 패키지를 보고 실패했다.
2. `stored.rules`의 diff를 봤다. 경계 규칙 9개의 줄이 지워지지 않은 채 같은 규칙이 9줄 더 추가돼 있었다. 옛 줄과 새 줄을 비교하니 끝부분만 달랐다. 옛 줄에는 `(msa-boundary.md 2번)`이 있고 새 줄에는 없었다.
3. 직전 커밋에서 규칙의 `because(...)` 문구를 다듬은 것이 원인이었다.

**원인**

ArchUnit의 `FreezingArchRule`은 규칙 설명 문자열을 열쇠로 기준선 파일을 찾는다. 문구가 한 글자라도 바뀌면 처음 보는 규칙이 되고, 처음 보는 규칙은 지금의 위반 전부를 새 기준선으로 저장한 뒤 통과한다. `freeze.store.default.allowStoreCreation=false`는 기준선 폴더가 없을 때만 막고, 이미 있는 폴더에 새 규칙이 추가되는 것은 막지 않는다.

그래서 테스트는 초록이었지만 래칫은 그 순간 초기화된 상태였다. 그사이에 새 위반이 생겼다면 아무 경고 없이 기준선에 들어갔을 것이다.

**해결**

새 기준선의 건수를 옛 기준선과 대조했다. 옛 건수에서 이번에 고친 enum 71건을 뺀 값과 정확히 같았다(105 → 65, 94 → 63, 나머지 동일). 새로 인정된 위반이 없음을 확인한 뒤 옛 파일 9개와 `stored.rules`의 옛 줄 9개를 지웠다.

같은 일이 다시 조용히 일어나지 않도록 저장소를 바꿨다. `RatchetViolationStore`는 기준선에 없는 규칙을 만나면 기준선을 만들지 않고 실패한다. 규칙 문구를 일부러 바꿔 실행해서 빌드가 실패하는 것을 확인했다.

**배운 것**

- 테스트가 통과한다는 것과 안전장치가 살아 있다는 것은 다르다. 기준선처럼 "통과시키기 위한 목록"이 있는 검사는 목록이 조용히 바뀌어도 초록이다.
- 규칙의 설명 문구는 문서가 아니라 식별자다. 고치려면 기준선을 함께 옮겨야 한다.
- 같은 뿌리의 증상이 하나 더 있다. 기준선의 위반 문구에는 메서드 시그니처가 들어 있어서, 파라미터 타입의 패키지만 옮겨도 기존 위반이 새 위반으로 보인다.
- 설정의 주석("false여야 새 위반이 묻히지 않는다")을 믿었다. 실제 동작은 직접 겪고서야 알았다.

## T-007

**증상**

공통 예외 핸들러에는 잘못된 `period` 값에 통계 에러를 주는 분기가 있다.

```java
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<CommonResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    if ("period".equals(e.getName())) {
        throw ApplicationException.from(StatsErrorCase.INVALID_PERIOD);   // 6003
    }
    throw e;
}
```

이 분기를 옮기기 전에 응답을 고정하는 테스트를 썼는데 실패했다.

```
GET /api/v1/stats/summary?period=WRONG
java.lang.AssertionError: No value at JSON path "$.errorCode"
Status = 400, Body = (비어 있음)
```

**원인을 좁힌 과정**

1. 테스트의 요청이 잘못됐다고 의심했다. 틀렸다. 상태 코드는 400으로 맞았고 본문만 없었다.
2. 통계와 무관한 API(`GET /api/v1/plans/abc`)에도 같은 요청을 보냈다. 똑같이 본문 없는 400이었다. `period` 분기를 타든 안 타든 결과가 같다는 뜻이다.

**원인**

`@ExceptionHandler` 메서드 안에서 던진 예외는 다른 `@ExceptionHandler`가 받지 않는다. 같은 클래스에 `ApplicationException`을 처리하는 핸들러가 있어도 거기로 가지 않는다. 스프링은 핸들러가 실패한 것으로 보고 원래 예외를 기본 처리(`DefaultHandlerExceptionResolver`)로 넘기고, 타입 불일치의 기본 응답이 본문 없는 400이다.

이 분기는 만들어진 뒤로 한 번도 6003을 내보낸 적이 없었다.

**해결**

응답을 바꾸지 않는 범위에서, 동작하지 않는 분기만 지웠다(공통 핸들러가 통계 모듈을 import하지 않게 하는 것이 이 작업의 목적이었다). 6003을 실제로 내보내도록 고치는 것은 응답이 달라지는 일이라 이번에는 하지 않았다. 에러 응답을 통일할 때 다른 API의 타입 오류와 함께 고친다.

**배운 것**

- 에러 응답은 핸들러가 직접 만들어 반환한다. 핸들러 안에서 다른 예외를 던져 다른 핸들러에 넘기는 방식은 동작하지 않는다.
- 코드를 읽고 "응답은 그대로다"라고 말할 뻔했다. 구조를 바꾸기 전에 실제 응답을 테스트로 먼저 고정하면, 코드의 의도와 실제 동작이 다른 곳이 이때 드러난다.

---

## T-008

**증상**

Gateway가 원래 Host를 `X-Forwarded-Host`로 넘긴다고 예상하고 테스트를 썼다. 실패했다.
```
GatewayRoutingTest > 성공 - Host는 모놀리식 주소로 바뀌고 원래 Host는 X-Forwarded-Host로 넘어간다 FAILED
expected: "localhost:51020"
 but was: null
```

**원인을 좁힌 과정**

1. WireMock 설정이 헤더를 못 남기는 줄 알았다. 틀렸다. 헤더를 그대로 찍는 서버를 8080에 띄우고 Gateway로 요청을 보내 보니 `host: localhost:8080`, `Authorization`만 있고 `X-Forwarded-*`·`Forwarded`가 하나도 없었다.
2. Gateway 4.3 jar의 `GatewayAutoConfiguration`을 열어 보니 `XForwardedHeadersFilter` 빈에 `TrustedProxies$XForwardedTrustedProxiesCondition` 조건이 붙어 있었다.

**원인**

Spring Cloud Gateway 4.3부터 `spring.cloud.gateway.server.webflux.trusted-proxies`를 설정하지 않으면 X-Forwarded·Forwarded 필터가 만들어지지 않는다. 예전에는 무조건 붙였는데, 클라이언트가 직접 넣어 보낸 `X-Forwarded-For`도 그대로 믿고 넘겨서 뒤쪽 서버가 위조된 IP를 믿는 문제가 있었다.

**해결**

모놀리식에 Host로 URL을 만들거나 클라이언트 IP를 쓰는 코드가 0건이라 켜지 않았다. 테스트를 "Host는 모놀리식 주소로 바뀌고 X-Forwarded 헤더는 붙지 않는다"로 바꿔, 누가 켜면 깨지게 고정했다(`trusted-proxies: ".*"`로 켜서 실패하는 것 확인).

**배운 것**

- 옛 버전 기준 지식으로 기본 동작을 예상하면 틀린다. 예상이 틀렸을 때는 실제로 도착한 요청을 찍어 보고, 라이브러리의 조건을 직접 확인한다.
- 켤 때는 `.*`가 아니라 실제 프록시 주소만 믿게 한다. 모두 믿으면 위조를 막으려고 꺼 둔 의미가 없다.

---

## T-009

**증상**

같은 요청을 모놀리식 직접(`:8080`)과 Gateway 경유(`:8000`)로 보내 비교했다. 모놀리식이 허용하는 Origin의 preflight만 결과가 달랐다.
```
=== :8080  OPTIONS /api/v1/plans, Origin: http://localhost:5173
HTTP/1.1 200
Access-Control-Allow-Origin: http://localhost:5173
X-Frame-Options: DENY ...

=== :8000  같은 요청
HTTP/1.1 403 Forbidden
Vary: Origin ...
content-length: 0
```

**원인을 좁힌 과정**

1. Gateway가 `Origin` 헤더를 지워서 모놀리식이 거절한 줄 알았다. 틀렸다. 403 응답에 모놀리식의 보안 헤더(`X-Frame-Options` 등)가 없었다. 모놀리식까지 가지 않고 Gateway가 답한 것이다.
2. `Origin` 없는 OPTIONS는 `:8000`에서도 200이었다. preflight(Origin + `Access-Control-Request-Method`)만 막힌다.

**원인**

Gateway는 WebFlux 위에서 돈다. WebFlux의 핸들러 매핑은 preflight 요청을 라우트로 넘기지 않고 그 자리에서 자기 CORS 설정으로 판정한다. Gateway에 CORS 설정이 없으니 전부 거절했다. "CORS는 모놀리식에 둔다"는 설계가 Gateway 뒤에서는 성립하지 않았다.

**해결**

CORS를 Gateway(`globalcors`)로 옮기고 모놀리식은 `.cors(cors -> cors.disable())`로 껐다. 양쪽에 두면 실제 응답에 `Access-Control-Allow-Origin`이 두 번 붙어 브라우저가 거부한다. preflight와 실제 요청을 고정하는 `GatewayCorsTest`를 먼저 써서 실패를 확인하고 고쳤다.

**배운 것**

- 프록시를 끼우면 "그대로 전달된다"고 가정한 것 중 일부는 프록시가 직접 처리한다. preflight가 그렇다.
- 자동 테스트에 preflight 시나리오가 없어서 실제 기동 비교에서 처음 드러났다. 가짜 서버 테스트는 내가 떠올린 경우만 검사한다.

---

## T-010

**증상**
```
ERROR ... i.n.r.d.DnsServerAddressStreamProviders : Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider, fallback to system defaults. This may result in incorrect DNS resolutions on MacOS.
```

**원인**: Netty가 macOS용 네이티브 DNS 라이브러리(`netty-resolver-dns-native-macos`)를 찾지 못해 시스템 기본 DNS 설정으로 대신 동작한다는 알림이다. Apple Silicon Mac에서 Gateway(WebFlux·Netty)를 띄우면 나온다.

**해결**: 두었다. 로컬에서 `localhost`만 부르고 라우팅은 정상이다. 없애려면 `runtimeOnly 'io.netty:netty-resolver-dns-native-macos::osx-aarch_64'`를 추가하면 되지만, 의존성을 최소로 두기로 해서 넣지 않았다.

**배운 것**: ERROR 레벨이라도 동작에 영향이 없는 로그가 있다. 레벨이 아니라 내용(무엇을 대신 했는가)을 읽는다.

---

## T-011

**증상** (4-2, 장소 등록을 Catalog로 옮기며 실제 MySQL로 동시 등록을 재현)
```
org.hibernate.AssertionFailure: Entry for instance of 'com.bready.catalog.place.domain.Place' has a null identifier
(this can happen if the session is flushed after an exception occurs)
```

**원인**: 모놀리식 `PlaceCatalogService.register`와 같은 구조였다. 한 트랜잭션 안에서 "조회 → 없으면 `saveAndFlush` → 중복 키로 실패하면 다시 조회"를 한다. 저장이 실패해도 그 `Place`는 영속성 컨텍스트에 남고, 재조회 직전의 자동 flush가 그 객체를 또 쓰려다 터진다. 이 경로는 리포지토리를 mock한 단위 테스트로만 확인돼 있어서 드러나지 않았다.

**해결**: Catalog의 `PlaceRegisterService`에 트랜잭션을 걸지 않았다. 조회·저장·재조회가 각각 리포지토리 트랜잭션으로 돌아서, 실패한 저장은 그것만 롤백되고 재조회는 새 트랜잭션에서 상대가 커밋한 행을 본다. 다른 트랜잭션이 INSERT 하고 커밋하기 전에 등록을 시작시켜 유니크 인덱스에서 기다리게 만든 뒤 커밋하는 테스트(`PlaceRegisterRaceIntegrationTest`)로 실패 → 통과를 확인했다. 모놀리식 쪽 코드는 4-2 ④에서 지운다.

**배운 것**: "저장 실패 → 다시 조회"는 한 트랜잭션 안에서 성립하지 않는다(영속성 컨텍스트에 실패한 엔티티가 남고, REPEATABLE READ 스냅샷도 처음 조회 시점에 고정된다). 동시성 경로는 mock이 아니라 실제 DB에서, 순서를 강제해서 재현해야 한다.

---

## T-012

**증상** 
```
java.lang.NullPointerException: Cannot invoke "PlaceRegisterRequest.externalId()" because "request" is null
```
기대한 실패(503)가 아니라 stub을 거는 줄에서 터졌다.

**원인**: 통합 테스트 베이스가 `registerPlace`에 가짜 응답(answer)을 미리 걸어 두었다. `given(mock.registerPlace(any()))`는 괄호 안의 메서드를 실제로 한 번 호출하므로, 그 answer가 `any()`가 넘긴 `null`로 실행됐다.

**해결**: 이미 answer가 걸린 mock을 다시 설정할 때는 `willThrow(...).given(mock).registerPlace(any())`를 쓴다. 이 형태는 메서드를 호출하지 않고 설정만 한다.

**배운 것**: `given(mock.method())`와 `willXxx().given(mock).method()`는 같은 일을 하는 두 문법이 아니다. 앞의 것은 메서드를 호출한다.

---
