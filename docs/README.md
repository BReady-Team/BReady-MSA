# BReady-MSA 문서

팀 프로젝트였던 모놀리식 BReady를 **Strangler Fig 방식으로 MSA로 전환**하는 과정의 기록이다.
이미 엉켜 있는 실제 코드를 깨뜨리지 않고 한 조각씩 떼어내며, 매 조각의 "왜"와 "흐름"을 남긴다.

## 읽는 순서

| 순서 | 문서 | 무엇을 얻나 |
|---|---|---|
| 1 | [LEARNING_JOURNEY.md](LEARNING_JOURNEY.md) | 전체 이야기. 조각마다 무엇을 왜 했고 무엇을 배웠는지 3~5줄 |
| 2 | [architecture/context-map.md](architecture/context-map.md) | 출발점의 경계 지도. 어디가 엉켜 있고 어디부터 떼는지 |
| 3 | [design/](design/) | 조각별 설계서. 왜 지금 · 선택지와 결정 · 흐름(before → after) · 검증 증거 |
| 4 | [adr/](adr/) | 되돌리기 어려운 결정의 근거 (맥락 · 대안 · 결정 · 결과) |
| 5 | [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | 막혔던 것들: 증상 → 원인 → 해결 → 재발 방지 |

## 문서 규칙
- **같은 내용을 두 번 쓰지 않는다.** 상세는 `design/`, 결정의 근거는 `adr/`, 이야기는 `LEARNING_JOURNEY.md`에만 쓰고 서로 링크한다.
- **증거로 말한다.** "됐다" 대신 테스트 출력, 로그, curl 응답, 쿼리 결과를 붙인다.
- 설계서 이름: `design/{로드맵단계}-{순번}-{slug}.md` (예: `design/0-1-local-boot.md`)
- ADR 이름: `adr/{4자리 번호}-{slug}.md`, 템플릿은 [adr/0000-template.md](adr/0000-template.md)