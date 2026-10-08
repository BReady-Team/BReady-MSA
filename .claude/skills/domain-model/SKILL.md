---
name: domain-model
description: 엔티티·enum·값 객체를 새로 만들거나 고칠 때 사용한다. 정적 팩토리, 상태 변경 메서드, 불변식 검증, 연관관계 vs ID 참조 판단을 BReady 규칙에 맞게 한다. "엔티티 추가", "필드 추가", "도메인 로직 옮기기", "상태 enum" 같은 요청에 사용.
---

# /domain-model — 엔티티 · enum · 값 객체

먼저 읽는다: `rules/entity.md`, `rules/exception.md`, (다른 모듈 데이터가 얽히면) `rules/msa-boundary.md`.

## 1. 이 객체가 지키는 규칙부터 적는다
코드 전에 짧게 정리한다.
```
Plan
- 생성: ownerId·title·planDate 필수, status = ACTIVE
- 불변식: 소유자만 수정·삭제·공유 가능 (PLAN_002)
- 상태 전이: ACTIVE → DELETED (soft delete), DELETED에서는 수정 불가 (PLAN_001)
- 공유 토큰: 처음 요청 시 1회 발급, 이후 같은 값
```
규칙이 서비스에 흩어져 있다면 그 위치(`파일:줄`)를 같이 적는다. 그것들이 이번에 엔티티로 옮겨올 대상이다.

## 2. 골격
- `entity.md` 1번: `@NoArgsConstructor(PROTECTED)`, `BaseEntity` 상속, `public static create(...)`.
- 상태 값은 enum + `EnumType.STRING` + `length`(5번).

## 3. 연관관계 판단 (`entity.md` 6번)
필드마다 묻는다.
1. 다른 모듈 소유인가? → **`Long xxxId`**, 의도 주석.
2. 같은 애그리거트의 하위 객체인가? → `@ManyToOne(LAZY)`(자식 → 부모). 부모 쪽 `@OneToMany`는 부모를 통해서만 생명주기를 관리할 때만.
3. 둘 다 아니면 → ID 참조.

판단이 애매하면(특히 plan·place·trigger 사이) 멈추고 사용자에게 묻는다. 경계 결정은 ADR 대상이다.

## 4. 행위
- 변경: 의도가 드러나는 이름(`changeRepresentative`, `issueShareToken`). setter 금지(3번).
- 검증: `validateXxx`는 ErrorCase로 던지고, `isXxx`는 boolean만 돌려준다(4번).
- 시간: 파라미터로 받는다(8번).
- 같은 `switch`가 여러 서비스에 있으면 enum 메서드로(5번).

## 5. 테스트 (도메인 단위, 스프링 없음)
- 생성 성공, 필수값 누락마다 실패, 상태 전이 성공/실패, 검증 메서드 성공/실패.
- fixture에 이 엔티티의 `defaultXxx()`와 상황별 메서드를 추가한다(`testing.md` 5번).

## 6. 스키마
- 현재 `ddl-auto`에 기대고 있다. 컬럼 추가·변경이 있으면 기존 데이터에 어떤 영향이 있는지(NOT NULL 추가, enum 문자열 길이) 사용자에게 알린다.
- 마이그레이션 도구 도입은 로드맵에서 따로 다룬다. 지금 들이지 않는다.

## 체크리스트
- [ ] public 생성자·setter·`@Setter`·`@Data`·`@ToString` 없음
- [ ] 도메인 규칙 위반이 `IllegalArgumentException`이 아니라 ErrorCase
- [ ] 다른 모듈 엔티티를 연관으로 갖지 않음
- [ ] `LocalDateTime.now()` 직접 호출 없음
- [ ] 도메인 단위 테스트로 규칙마다 성공/실패 검증