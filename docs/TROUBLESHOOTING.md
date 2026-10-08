# 트러블슈팅

> 막힌 순간 바로 기록한다. 우회하지 않고 원인을 이해한 것만 "해결"로 적는다.
> 형식: **증상(원문 로그) → 원인 → 해결 → 재발 방지 → 연결(commerce-msa 등 비슷했던 경험)**

| # | 날짜 | 증상 한 줄 | 단계 |
|---|---|---|---|
| [T-001](#t-001) | 2026-10-08 | `./gradlew` 실행 시 permission denied | 출발점 |
| [T-002](#t-002) | 2026-10-08 | `contextLoads` 실패: `cloud.aws.credentials.access-key` 플레이스홀더 해석 불가 | 출발점 |

---

## T-001

**증상**
```
$ ./gradlew test
(eval):1: permission denied: ./gradlew
```

**원인**: git에 `gradlew`가 실행 권한 없이(`100644`) 저장돼 있었다. `git ls-files -s gradlew` → `100644 …`. 원본 레포를 클론할 때부터 그랬다.

**해결**: `chmod +x gradlew` → 커밋하면 git이 모드 변경(`100644 → 100755`)으로 기록한다(`core.fileMode=true`).

**재발 방지**: 없음(한 번 고치면 끝). CI를 새로 만들 때 `./gradlew` 직접 실행으로 검증된다.

---

## T-002

**증상**
```
BReadyApplicationTests > contextLoads() FAILED
    Caused by: PlaceholderResolutionException
Could not resolve placeholder 'cloud.aws.credentials.access-key' in value "${cloud.aws.credentials.access-key}"
```
`./gradlew test` → 100개 중 99개 통과, 이 1개만 실패.

**원인**: 앱 전체를 띄우는 테스트는 모든 빈을 만든다. `S3Config`가 `cloud.aws.credentials.*`를 요구하는데,
- 운영용 `application.yml`은 `.gitignore` 대상이라 레포에 없고,
- `application-test.yml`에는 S3 설정이 없다.
나머지 99개는 Mockito 단위 테스트나 컨트롤러 슬라이스라 S3 빈을 만들지 않아서 통과했다.

**해결**: (0단계 안전망 첫 작업에서 처리 예정) 외부 의존(S3·OpenAI·카카오·네이버)이 **실제 키 없이도** 로컬·테스트에서 뜨도록 설정을 정리한다.

**재발 방지**: (0단계에서 작성)

**연결**: commerce-msa에서는 처음부터 `${VAR:-default}`로 외부화해서 이 문제가 없었다. 팀 프로젝트는 운영 서버의 `application.yml`에 기대고 있었기 때문에, 그 파일이 없는 곳에서는 뜨지 않는다. → "12-factor: 설정은 환경에서"의 반대 사례.