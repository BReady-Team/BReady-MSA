---
name: new-api
description: 새 REST 엔드포인트를 추가하거나 기존 엔드포인트를 크게 고칠 때 사용한다. 계약(URL·DTO·ErrorCase) → 테스트 → 도메인 → 서비스 → Api 인터페이스·컨트롤러 순서로 BReady 규칙에 맞게 만든다. "API 추가", "엔드포인트 만들어줘", "조회 기능 추가" 같은 요청에 사용.
---

# /new-api — 엔드포인트 추가

먼저 읽는다: `rules/controller-api.md`, `rules/dto.md`, `rules/exception.md`, `rules/service-transaction.md`. 이 스킬은 규칙을 반복하지 않고 **순서**만 정한다.

## 0. 위치 확인
- 어느 모듈의 기능인가? 다른 모듈의 데이터가 필요한가?
  - 필요하면 `rules/msa-boundary.md` 2번부터 확인한다. 남의 리포지토리·엔티티를 쓰는 설계라면 **멈추고** 사용자와 경계부터 정한다.
- 비슷한 기존 엔드포인트가 있으면 참고하되, 그 코드의 스타일이 아니라 규칙을 따른다.

## 1. 계약을 먼저 쓴다 (코드 전에 사용자에게 보여준다)
```
POST /api/v1/plans/{planId}/copy          201 Created
요청  PlanCopyRequest(String title)        @NotBlank
응답  PlanCopyResponse(Long planId, LocalDateTime createdAt)
오류  PLAN_001(404) 플랜 없음 · PLAN_002(403) 소유자 아님 · COMMON_001(400) 형식 오류
인증  @CurrentUser 필요
트랜잭션  쓰기 1회, 외부 호출 없음
```
- URL·메서드·상태 코드: `controller-api.md` 3번
- 이름: `dto.md` 2번 · 오류 상태: `exception.md` 4번
- 계약에 설계 선택지가 있으면(동기/비동기, 반환 범위 등) 사용자 결정을 받는다.

## 2. 테스트를 먼저 준비한다
- fixture를 만들거나 재사용한다: `testing.md` 5번.
- 실패할 테스트부터 쓴다.
  1. 도메인 단위: 새 규칙이 있다면 엔티티 메서드 테스트.
  2. 서비스 단위: 성공 1개 + ErrorCase마다 실패 1개.
  3. 컨트롤러 슬라이스: 성공 상태·응답 형태 1개 + 형식 검증 실패 1개 + 인증 필요 여부.

## 3. 도메인
- 규칙 판단은 엔티티 메서드로(`entity.md` 3·4번). 서비스에 `if`로 쓰지 않는다.
- 새 ErrorCase는 해당 모듈 enum에 추가(`exception.md` 2번).

## 4. 서비스
- 클래스 `@Transactional(readOnly = true)`, 쓰기 메서드만 `@Transactional`(`service-transaction.md` 2번).
- 흐름: 조회 → 엔티티 검증 → 엔티티 변경 → `XxxResponse.from(...)`(`service-transaction.md` 1번).
- 외부 호출이 있으면 트랜잭션 밖으로(3번). 동시성 위험이 있으면 4번.

## 5. DTO
- 요청·응답 record, 응답에 `static from(...)`(`dto.md` 1·4번). 민감 필드는 `toString` 가림(5번).

## 6. Api 인터페이스 + 컨트롤러
- 문서는 `{Domain}Api` 인터페이스, 매핑·검증은 컨트롤러(`controller-api.md` 2번).
- `@ApiResponses`에 1단계 계약의 오류를 전부 적는다.
- 해당 모듈에 아직 `{Domain}Api`가 없다면: 이번 엔드포인트만 인터페이스로 옮기지 말고, **그 컨트롤러 전체를 옮길지** 사용자에게 묻는다(반쪽짜리 상태를 만들지 않는다).

## 7. 검증
- 테스트 전부 통과(출력을 붙인다).
- 앱을 띄울 수 있으면 `curl`로 성공 1회·실패 1회 호출해 응답 JSON을 보여준다.
- Swagger UI(`/swagger-ui.html`)에 엔드포인트와 오류 응답이 보이는지 확인.

## 체크리스트
- [ ] 컨트롤러에 비즈니스 로직·리포지토리 없음
- [ ] `ApplicationException.from(...)`만 사용
- [ ] 엔티티를 응답에 노출하지 않음
- [ ] 다른 모듈의 리포지토리·엔티티·테이블에 닿지 않음
- [ ] 사용자 데이터를 바꾸면 소유권 검증
- [ ] 테스트: 성공 + ErrorCase별 실패 + 형식 검증