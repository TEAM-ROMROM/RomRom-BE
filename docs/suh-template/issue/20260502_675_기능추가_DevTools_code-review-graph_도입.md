---
title: "⚙️[기능추가][DevTools] code-review-graph 도입 (코드베이스 지식 그래프)"
labels: 작업전
assignees: Cassiiopeia
---

📝 현재 문제점
---

- AI 코드 리뷰/탐색 시 매번 전체 코드베이스를 Grep/Read로 스캔 → 토큰 낭비 + 속도 저하
- 함수/클래스 간 호출 관계, 영향 범위(blast radius), 테스트 커버리지를 구조적으로 파악할 수단이 없음
- 변경 사항이 어떤 파일에 영향을 주는지 수동 추적 필요
- 모노레포(RomRom-Web, RomRom-Application, RomRom-Domain-* 등 다중 모듈)에서 토큰 효율이 특히 떨어짐

🛠️ 해결 방안 / 제안 기능
---

[code-review-graph](https://github.com/tirth8205/code-review-graph)를 도입한다. Tree-sitter로 코드베이스를 파싱해 SQLite 기반 구조 그래프로 저장하고, MCP 도구를 통해 AI가 필요한 부분만 정확히 읽도록 한다.

**도입 효과**
- 평균 토큰 사용량 8.2배 감소 (벤치마크 기준)
- 변경 영향도(blast radius) 분석으로 리뷰 정확도 향상
- 호출 관계, 의존성, 테스트 커버리지를 그래프 쿼리로 즉시 조회
- 파일 변경 시 자동 증분 업데이트 (2초 이내)

**도입 방식**
- 도구 설치: `pip install code-review-graph` 또는 `pipx install code-review-graph`
- 프로젝트 설정: `code-review-graph install` 1회 실행 → MCP 설정 + 훅 자동 구성
- 초기 그래프 빌드: `code-review-graph build`

⚙️ 작업 내용
---

- [ ] `.mcp.json` 추가 — Claude Code가 `uvx code-review-graph serve` MCP 서버를 띄우도록 설정
- [ ] `.claude/settings.json` 추가 — Edit/Write/Bash 후 `code-review-graph update --skip-flows` 자동 실행, SessionStart 훅에 `code-review-graph status` 등록
- [ ] `CLAUDE.md` 업데이트 — Grep/Read 이전에 graph 도구(semantic_search_nodes, query_graph, get_impact_radius 등)를 우선 사용하도록 가이드 추가
- [ ] `.gitignore` 업데이트 — `.code-review-graph/`, `.gstack/` (그래프 데이터 디렉토리) 추적 제외
- [ ] 팀원 도입 가이드 작성 — 각자 로컬에서 `code-review-graph install && code-review-graph build` 1회 실행 안내

🙋‍♂️ 담당자
---

- 백엔드: 찬
- DevOps: 찬
