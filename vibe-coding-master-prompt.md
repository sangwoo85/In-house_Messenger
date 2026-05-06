# Internal Messenger Vibe Coding Master Prompt

이 문서는 장기 명세를 직접 품지 않고, AI가 어떤 순서로 문서를 읽고 어떤 방식으로 작업해야 하는지 정의하는 실행용 프롬프트다.

## Goal

현재 저장소의 문서를 기준으로, 범위를 넘지 않으면서 안정적으로 기능을 구현, 수정, 검증한다.

## Read Order

작업 전 아래 문서를 순서대로 확인한다.

1. `README.md`
2. `docs/product.md`
3. `docs/scope.md`
4. `docs/spec.md`
5. `docs/style-guide.md`
6. `docs/tasks.md`
7. `docs/acceptance.md`
8. `docs/decisions.md`

## Working Rules

- 장기 요구사항은 `docs/`를 기준으로 삼고, 현재 작업 지시는 별도 프롬프트에서 받는다.
- 범위가 모호하면 `docs/scope.md`를 우선 기준으로 삼는다.
- 구현 전 관련 파일과 기존 패턴을 먼저 읽고, 현재 구조를 최대한 존중한다.
- 보안, 인증, IPC, 세션 처리 규칙은 임의로 완화하지 않는다.
- 새 기능이나 정책이 추가되면 관련 문서를 함께 갱신한다.

## Task Prompt Template

AI에게 실제 작업을 요청할 때는 아래 형식을 사용한다.

```md
# Goal
이번 작업의 목표 한 줄

# Context
관련 파일, 현재 상태, 참고 문서

# In Scope
- 이번 작업에서 할 것

# Out Of Scope
- 이번 작업에서 하지 않을 것

# Requirements
- 반드시 만족해야 하는 요구사항

# Constraints
- 유지해야 하는 구조, 보안, 디자인, 성능 제약

# Output
- 수정 파일
- 필요한 테스트 또는 검증
- 짧은 변경 설명

# Done
- 완료 판단 기준
```

## Output Expectations

- 변경은 작은 단위로 나누어 진행한다.
- 완료 후에는 무엇을 바꿨는지와 어떤 기준을 만족했는지 설명한다.
- 테스트나 빌드를 못 돌렸다면 이유를 명확히 남긴다.
