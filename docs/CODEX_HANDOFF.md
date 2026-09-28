# 맥북 Codex 인수인계

기준: 2026-09-29, 저장소 `https://github.com/sangwoo85/In-house_Messenger.git`, 전달 브랜치 `main`.

## 새 작업에서 읽는 순서

루트 `AGENTS.md` → 이 문서 → [맥북 설치](MACBOOK_SETUP.md) → [작업 내역](WORK_HISTORY.md) → [구현 상태](implementation-status.md).

이 문서는 확인 가능한 대화의 요구사항, 코드, 기존 작업 기록을 정리한 인수인계다. Codex 대화 원문·도구 로그·앱 내부 작업 ID를 가져오는 세션 백업은 아니다. 새 맥북의 Codex가 저장소를 열고 이 문서를 읽어 같은 구현 맥락에서 이어가도록 구성했다. Git에는 전체 소스, 테스트, 마이그레이션, 디자인 더미와 캡처, 실행/배포 설정 예제가 들어 있다.

## 바로 붙여 넣을 시작 요청

```text
이 저장소의 Messenger 개발을 이어가자.
먼저 AGENTS.md, docs/CODEX_HANDOFF.md, docs/MACBOOK_SETUP.md,
docs/WORK_HISTORY.md, docs/implementation-status.md를 읽어줘.
git status와 현재 브랜치를 확인하고 기존 변경을 보존해줘.

2026-09-29까지 대화창 디자인, 사용자 목록, 일정 등록/수정/취소,
시작·종료 시간, 장소 유무와 7층/8층 중 회의실 선택,
서버 저장 및 예약 알림이 실제 앱에 적용되어 있어.
기존 답장·공감·파일 업로드·읽음 정책도 유지해야 해.

우선 이 맥북의 Java 21, Node/npm, Docker 및 포트 상태를 확인하고,
로컬 개발 환경을 문서대로 실행해줘. 기존 DB나 Docker 볼륨은 삭제하지 마.
실제 접속 설정과 회사 비밀값은 Git에 넣지 마.
검사한 결과와 남은 환경 의존 사항을 짧게 알려줘.
아직 정해지지 않은 기능이나 운영 배포는 임의로 진행하지 마.
그 다음 내가 요청하는 작업을 현재 구현 위에서 이어가자.
```

맥북 환경이 이미 준비되어 있으면 실행 준비 부분을 생략하고 마지막에 새 요청을 붙이면 된다.

## 제품과 확정한 정책

- 사내 Windows/macOS 메신저. Electron + React 18/TypeScript, Spring Boot 3.5.5/Java 21, MySQL 8.4, Redis 7.4, REST + STOMP.
- 데스크톱 기본 1440×920, 최소 1100×720. 왼쪽 네이비 사이드바와 기존 레이아웃 유지. Messenger 제목 15px, 기존 보조 문구 삭제.
- 대화는 카카오톡을 참고한 자연스러운 말풍선 배치. 상대방은 흰 바탕·회색 테두리·검정 글씨, 본인은 연한 파랑. 인용 답장 글씨의 대비도 조정했다.
- 사용자 목록에는 검색, 부서 필터, 아바타, 소속/접속 상태, DM 진입이 있다. 사이드바 조직 트리도 유지한다.
- 사용자 비밀번호는 사내 인증 API가 검증한다. 회사 사용자 DB와 메신저 DB는 분리한다. Usersdummy는 로컬 개발용 별도 서버다.
- 메시지는 전송 후 수정 불가. 본인 메시지 삭제는 상대방이 읽기 전만 허용. 답장 원문 이동, 공감 변경/취소, 파일 업로드 진행률과 재시도, 방별 초안을 유지한다.
- 디자인 더미는 `design-preview/messenger.html` 단일 파일로 서버 없이 연다. 더미에 실제 서버 연결을 추가하라는 뜻이 아니다. 이후 사용자가 별도로 실제 `messenger`에 디자인과 기능 적용을 요청했고 그 구현이 현재 코드다.

## 일정의 현재 동작

1. 대화방 헤더 **일정** → 우측 패널 → **새 일정 등록**.
2. 제목, 시작 및 종료 일시 입력. 미래 시작, 종료 > 시작을 UI/서버에서 검증한다. 여러 날짜에 걸친 일정도 가능하다.
3. 장소 없음/있음을 선택한다. 있음이면 7층 중 회의실 또는 8층 중 회의실을 선택해야 한다. 없음으로 바꾸면 장소 값이 지워진다.
4. 알림은 시작 시각 또는 5/10/30/60분 전. 공개 및 알림 대상은 대화방 전체 또는 나만이다.
5. 등록자만 수정·취소할 수 있다. revision으로 오래된 수정/취소를 거부하고 이전 알림을 교체·삭제한다.
6. 전체 알림 수신자는 등록/수정 시점의 활성 참여자다. 이후 새로 참여한 사람에게 기존 알림을 생성하지 않는다. 퇴장자는 조회/알림 처리에서 제외된다.
7. 서버가 도래한 미확인 알림을 반환한다. 앱 전체에서 5초 간격으로 조회하며 최소화 중에도 확인한다. 데스크톱 팝업은 15초 뒤 닫히고 클릭하면 해당 대화방으로 이동한다.
8. 앱 내부 알림은 확인/대화방 열기/5분 뒤 다시 알림을 지원한다. 미확인 상태와 다시 알림 시각은 서버에 남는다. 앱 종료·절전·통신 단절 중에는 팝업을 띄우지 못하고 재접속 시 조회한다.

회의실 선택은 장소 표시 기능이다. 회의실 실제 예약, 중복 예약 방지, 반복 일정, OS가 앱을 깨우는 예약 알림, 외부 캘린더 연동은 구현 범위에 없다. 시간은 epoch milliseconds로 저장하고 기기의 현지 시간대로 표시한다.

## 코드 위치

| 영역 | 시작할 파일/폴더 |
| --- | --- |
| 화면 구조/대화 | `electron-app/src/features/chat/ChatShell.tsx`, `ChatLayout.tsx`, `ChatMessages.tsx` |
| 공감/답장/업로드 | 같은 폴더의 `MessageInteractions.tsx`, `FileUploadProgress.tsx`, `message-interactions.css` |
| 테마/사이드바 | `electron-app/src/styles.css`, `src/components/Sidebar.tsx`, `Avatar.tsx`, `Icon.tsx` |
| 조직/사용자 | `electron-app/src/features/users/` |
| 일정 화면/API/알림 | `electron-app/src/features/schedules/` |
| 실시간 수신/세션 | `src/features/chat/useChatRealtime.ts`, `src/socket/socketService.ts`, `src/stores/`, `src/services/http.ts` |
| Electron 알림/설정 | `electron-app/electron/desktopNotifications.ts`, `main.ts`, `preload.ts`, `runtimeConfig.ts`, `resources/notification.*` |
| 인증/외부 사용자/조직 | `backend/src/main/java/com/company/messenger/domain/user/`, `domain/organization/`, `global/external/` |
| 메시지/채널/첨부 | 같은 Java 루트의 `domain/message/`, `domain/channel/`, `domain/file/` |
| 일정 서버 | `domain/schedule/ScheduleController.java`, `ScheduleService.java` |
| DB 변경 | `backend/src/main/resources/db/migration/V1__...sql` ~ `V7__channel_schedules.sql` |
| 테스트 | `backend/src/test/`, `electron-app/scripts/*smoke.cjs` |
| 더미/실제 화면 캡처 | `design-preview/`, `docs/previews/` |

일정 서버는 `JdbcTemplate`과 기존 채널/멤버 서비스·저장소를 사용한다. 별도의 스케줄러 프로세스는 없고 알림 조회 시 서버 시간으로 계산한다. 변경 트랜잭션은 채널을 먼저 잠그며 실시간 이벤트는 커밋 후 전달한다.

주요 일정 API는 `/api/v1` 아래에 있다.

| Method | Path | 목적 |
| --- | --- | --- |
| GET | `/schedule-rooms` | 선택 가능한 회의실 |
| GET / POST | `/channels/{channel}/schedules` | 조회 / 등록 |
| PUT / DELETE | `/channels/{channel}/schedules/{id}` | 수정 / 취소(DELETE는 revision 쿼리 필요) |
| GET | `/schedule-reminders` | 현재 사용자의 도래한 미확인 알림, 최대 20개 |
| POST | `/schedule-reminders/{id}/ack` | 확인 처리 |
| POST | `/schedule-reminders/{id}/snooze` | 5분 뒤로 연기 |

## 검증 기준선과 남은 확인

2026-09-29 디자인 적용 때 전체 backend 검사 87개 중 85개 통과, Redis 연결이 필요한 기존 2개 스킵. 새 일정 검사 5개는 실제 Flyway V1–V7을 H2 MySQL 모드에서 적용해 통과했다. H2의 기존 LONGTEXT 타입 검증 차이 때문에 이 테스트는 Hibernate ddl-auto=none이며 운영은 validate를 유지한다.

타입 검사, Electron/웹 빌드, 기존 Electron 메시지·공감·답장·첨부 회귀, 알림 창 및 최소화 시 채널 이동, Playwright 일정 CRUD·장소 해제·다시 알림·사용자 필터·1440/1100 레이아웃 검사가 통과했다. UI 테스트는 사내 계정 대신 API fixture를 쓴다. `docs/previews/` 이미지도 fixture 화면이다.

남은 환경 확인은 새 맥북에서 실제 개발 스택을 실행해 두 사용자로 확인하는 것, Redis 검사 2개를 실제 연결 환경에서 실행하는 것, V7의 대상 MySQL 적용 확인이다. 회사 인증 API·실제 내부망·Windows·회사 인증서 서명/공증은 해당 환경에서 검증해야 한다. 새 일정에 대한 운영 배포는 수행하지 않았다. 기존 공개 웹 배포 문서는 과거 설치 기록이므로 현재 최신 코드를 서비스한다고 가정하지 않는다.

## 이후 작업 원칙

별도로 진행 중인 미완성 기능은 없다. 다음 사용자 요청을 현재 코드 위에서 구현한다. 성능/권한/알림의 기능 변경 시 관련 테스트를 함께 확인한다. 코드 생성만으로 서버/앱 재실행 또는 운영 배포까지 완료했다고 보고하지 않는다.

`docs/implementation-status.md`와 `docs/tasks.md`의 과거 섹션은 당시 기록이다. 메시지 수정 허용, 오래된 Flyway 버전/검사 수 등은 이후 날짜의 변경으로 대체되었다. 현재 코드와 이 문서의 확정 정책을 기준으로 판단하고 기록 차이는 검증 후 정정한다.
