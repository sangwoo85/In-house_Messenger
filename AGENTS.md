# Messenger 작업 시작 지침

이 저장소는 Electron/React 메신저와 Spring Boot 서버를 함께 관리한다. 기본 답변 언어는 한국어다.

## 먼저 읽을 문서

1. `docs/CODEX_HANDOFF.md` — 현재 구현, 유지할 정책, 코드 위치, 다음 작업 기준
2. `docs/MACBOOK_SETUP.md` — 새 맥북에서 설치·실행·검증하는 명령
3. `docs/WORK_HISTORY.md` — 사용자 요청과 결정, 날짜별 작업 기록
4. `docs/implementation-status.md` — 세부 구현 및 검증 기록

이 문서들은 기존 작업을 이어가기 위한 맥락이다. 사용자의 새 요청을 우선하며, 기록된 후속 후보를 자동으로 모두 구현하지 않는다. `git status`부터 확인하고 기존 변경을 되돌리지 않는다.

## 유지할 기준

- 실제 제품은 `electron-app/` + `backend/`다. `design-preview/messenger.html`은 서버 없이 열리는 별도의 디자인 참고용 더미다.
- 현재 레이아웃과 기본 1440×920, 최소 1100×720 크기를 유지한다. 상대방 말풍선은 흰 바탕/테두리/검정 글씨, 내 말풍선은 연한 파랑이다.
- 전송한 메시지는 수정할 수 없다. 작성자의 삭제도 상대방이 읽기 전까지만 허용한다. 답장·공감·파일 전송·읽음 동작을 보존한다.
- 사용자 인증은 서버에서 사내 API를 통해 수행한다. 메신저 DB에 사용자 비밀번호를 저장하거나 API 장애 시 인증을 우회하지 않는다.
- 일정은 실제 서버에 저장한다. 시작/종료 시간, 장소 유무, 회의실 선택, 개인/전체 대상, 예약 알림을 유지한다.
- 적용된 Flyway V1–V7 파일을 변경하지 않는다. 새 스키마 변경에는 다음 버전의 마이그레이션을 추가한다. 자동 baseline이나 `ddl-auto=update`로 Messenger DB를 우회하지 않는다.
- Electron의 contextIsolation, 제한된 preload IPC, nodeIntegration=false 및 인증 세션 경계를 유지한다.
- 실제 설정/비밀키/DB/업로드/로그/빌드 결과/개인 Codex 세션 파일은 커밋하지 않는다. 예제와 재현 명령으로 전달한다.
- `Usersdummy/`는 개발용 인증 API이며 해당 폴더 작업에는 그 안의 `AGENTS.md`도 읽는다.

## 확인과 기록

관련 변경에 맞는 검사를 실행한다. 주요 명령은 `backend/`에서 `./mvnw test`, `electron-app/`에서 `npm run typecheck`, `npm run build`, `npm run test:desktop`, `npm run test:notifications`, `npm run test:schedules`다. 일정 UI 검사의 Playwright 준비는 맥북 문서를 따른다.

변경 내용·실제로 실행한 검사·스킵/미확인 사항을 문서에 갱신한다. 코드 구현, 로컬 실행, 운영 배포를 구분해서 보고한다. Git push나 배포는 해당 요청에서 받은 권한 범위 안에서 수행한다.
