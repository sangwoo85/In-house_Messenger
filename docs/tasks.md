# Tasks

## Goal

현재 프로젝트 상태와 다음 작업 단위를 작은 조각으로 관리한다.

## Completed Milestones

- 프로젝트 초기 세팅
- 인증 시스템
- 채널 및 실시간 채팅 기반
- 파일 전송
- 알림 및 Presence 관리
- 외부 연동 공지 및 사용자 알림
- Electron 패키징 설정
- 사용자 디렉토리 및 DM 진입 흐름

## Current Focus

- 문서 구조를 AI 친화적으로 재정리
- 구현 기준을 `docs/`에 고정
- 이후 작업을 기능 단위로 잘게 쪼개서 진행할 준비

## Suggested Next Work Items

- 백엔드 테스트 범위 보강
- 프론트엔드 타입 및 상태 흐름 검증
- 채팅 UX 세부 개선
- 실패 케이스와 오프라인 복구 시나리오 점검
- 배포와 운영 문서 분리

## Task Writing Template

각 작업은 아래 형식으로 작성한다.

```md
## Task: 작업 이름

### Goal


### Context
관련 파일, 관련 문서, 현재 상태

### In Scope
- 이번 작업에 포함할 것

### Out Of Scope
- 이번 작업에서 하지 않을 것

### Requirements
- 기능 요구사항
- 예외 처리 요구사항

### Done
- 완료 판단 기준
```

## Working Rules For AI

- 한 번에 큰 기능 전체보다 작은 작업 하나를 우선 처리한다.
- 범위가 모호하면 새 기능 확장보다 기존 동작 보존을 우선한다.
- 완료 후에는 어떤 요구사항을 만족했는지 `Done` 기준으로 다시 확인한다.
