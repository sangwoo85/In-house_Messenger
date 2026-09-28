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
- 사용자 목록 사이드바 스크롤 동작 수정
- 사용자 목록 부서 묶음 및 이름 정렬
- 로그아웃 기능

## Current Focus

- 2026-09-29: 디자인·사용자 목록·대화방 일정/알림 실제 앱 적용 완료. 맥북으로 코드와 문서를 전달하고 다음 사용자 요청에 따라 이어간다.
- 새 장비에서는 `CODEX_HANDOFF.md`와 `MACBOOK_SETUP.md`부터 읽는다. 아래 작업별 Out Of Scope/Done은 당시 범위 기록이며 최신 요구에 우선하지 않는다.

- 2026-09-14: Astra 검토 결함 수정 및 기존 미구현 기능 구현 완료
- 사내 API 인증/사용자 확인과 메신저 전용 DB 분리, 외부 운영 properties 구성 완료
- 상세 완료 항목과 현장 설정은 `implementation-status.md`, `intranet-deployment.md` 참조

## Task: 사용자 목록 스크롤 동작 수정

### Goal

사이드바 사용자 목록이 화면 높이를 초과할 때 내부에서 스크롤되도록 수정한다.

### Context

관련 파일:

- `electron-app/src/components/Sidebar.tsx`

현재 상태:

- 사용자 목록 컨테이너에 남은 높이 제약과 세로 스크롤 설정이 없어 목록이 화면 밖으로 밀릴 수 있었다.

### In Scope

- 사이드바와 목록 카드에 `min-h-0`, `flex-1`, `overflow-y-auto` 적용
- 사용자 목록과 채널 목록이 같은 방식으로 내부 스크롤되도록 정리

### Out Of Scope

- 사용자 검색, 필터, 페이지네이션 추가
- 사용자 목록 API 변경

### Requirements

- 사용자 수가 많아도 사이드바 밖으로 UI가 밀리지 않아야 한다.
- 상단 사용자 프로필과 탭 영역은 고정되고, 목록 영역만 스크롤되어야 한다.

### Done

- 사용자 목록 컨테이너에 내부 세로 스크롤이 적용됨
- 채널 목록에도 동일한 높이 제약이 적용됨

## Task: 사용자 목록 부서 묶음 및 이름 정렬

### Goal

사용자 목록을 부서 기준으로 묶어서 보여주고, 부서별 접힘/펼침과 사용자 정렬을 제공한다.

### Context

관련 파일:

- `backend/src/main/java/com/company/messenger/global/external/InternalAuthClient.java`
- `backend/src/main/java/com/company/messenger/domain/user/UserProfileResponse.java`
- `backend/src/main/java/com/company/messenger/domain/user/User.java`
- `backend/src/main/java/com/company/messenger/domain/user/UserService.java`
- `electron-app/src/features/users/users.api.ts`
- `electron-app/src/components/Sidebar.tsx`

현재 상태:

- 더미 사용자 API는 `dept`, `group` 값을 제공하지만 메신저 백엔드 사용자 응답에는 포함되지 않았다.
- 프론트 사이드바는 사용자 목록을 단일 리스트로 표시했다.

### In Scope

- 외부 사용자 목록의 부서/그룹 정보를 메신저 사용자 응답에 포함
- 사용자 목록을 `dept`에 대응되는 `department` 기준으로 오름차순 그룹화
- 그룹 헤더를 더블클릭하면 그룹 내부 사용자 목록을 접거나 펼치기
- 그룹 내부 사용자는 온라인 사용자를 먼저 표시
- 같은 온라인 상태 안에서는 이름 기준 오름차순/내림차순 정렬 토글 제공

### Out Of Scope

- 별도 사용자 검색 기능
- 서버 사이드 페이지네이션
- 부서/그룹 관리 화면

### Requirements

- 그룹 순서는 한국어 문자열 기준 오름차순이어야 한다.
- 그룹 접힘 상태는 사용자 목록 화면 안에서 유지되어야 한다.
- 온라인 사용자는 항상 그룹 내부 상단에 표시되어야 한다.
- 이름 정렬 토글은 온라인 우선 규칙을 깨지 않아야 한다.

### Done

- 백엔드 사용자 응답에 `department`, `userGroup` 필드가 추가됨
- 프론트 사용자 목록이 부서 단위로 묶여 표시됨
- 그룹 더블클릭 접힘/펼침과 이름 정렬 토글이 적용됨

## Task: 로그아웃 기능

### Goal

사용자가 사이드바 내 프로필 영역에서 현재 세션을 종료하고 로그인 화면으로 돌아갈 수 있게 한다.

### Context

관련 파일:

- `backend/src/main/java/com/company/messenger/domain/user/AuthController.java`
- `electron-app/src/features/auth/auth.api.ts`
- `electron-app/src/components/Sidebar.tsx`

현재 상태:

- 백엔드에는 `POST /api/v1/auth/logout` 엔드포인트가 이미 있다.
- 프론트에는 로그아웃 API 호출과 세션 정리 UI가 없었다.

### In Scope

- 로그아웃 API 클라이언트 함수 추가
- 사이드바 내 프로필 영역에 로그아웃 버튼 추가
- 로그아웃 시 WebSocket 연결 종료
- 로컬 인증 세션, React Query 캐시, 앱 배지 정리

### Out Of Scope

- 로그아웃 확인 모달
- 계정 전환 화면
- 서버 로그아웃 실패 재시도 UI

### Requirements

- 로그아웃 버튼은 중복 클릭을 방지해야 한다.
- 서버 로그아웃 호출이 실패해도 로컬 세션은 정리되어야 한다.
- 로그아웃 후 앱 배지는 0으로 초기화되어야 한다.

### Done

- `logout()` API 함수가 추가됨
- 사이드바 프로필 영역에서 로그아웃할 수 있음
- 로그아웃 후 세션/소켓/캐시/배지가 정리됨

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
