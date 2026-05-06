# Decisions

## 2026-04-30 Document Restructure

### Decision

기존 `vibe-coding-master-prompt.md`에 섞여 있던 제품 개요, 기능 명세, 아키텍처 규칙, 디자인 가이드, 작업 단계 정보를 `docs/` 아래 역할별 문서로 분리한다.

### Why

- 한 파일에 너무 많은 레벨의 정보가 섞이면 AI가 목표와 제약을 같은 무게로 처리한다.
- 장기 기준과 이번 작업 지시를 분리하면 반복 작업에서 품질이 안정된다.
- 문서 유지보수가 쉬워지고 변경 영향 범위를 빠르게 찾을 수 있다.

### Consequence

- `vibe-coding-master-prompt.md`는 더 짧은 실행 프롬프트로 유지한다.
- 제품/범위/명세/스타일/검수 기준은 각각의 문서에서 관리한다.
