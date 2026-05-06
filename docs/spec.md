# Spec

## Goal

제품이 어떤 구조로 동작하는지, 어떤 기능을 어떤 방식으로 구현하는지 정리한다.

## System Overview

- 프론트엔드: Electron + React + TypeScript
- 백엔드: Spring Boot + Spring Security + Spring WebSocket + JPA
- 데이터 저장소: MySQL, Redis
- 통신 방식: REST API + STOMP over WebSocket

## Repository Structure

```text
.
├── backend/        # Spring Boot API / WebSocket server
├── electron-app/   # Electron + React desktop client
├── docs/           # Product, scope, spec, guide, tasks, acceptance
├── docker-compose.yml
├── README.md
└── vibe-coding-master-prompt.md
```

## Frontend Structure

```text
electron-app/
├── electron/       # main, preload, tray
└── src/
    ├── app/        # app shell
    ├── components/ # shared UI
    ├── features/   # auth, chat, channels, notifications, users
    ├── services/   # http, runtime, desktop helpers
    ├── socket/     # realtime connection
    ├── stores/     # Zustand stores
    └── types/      # shared types
```

## Backend Structure

```text
backend/src/main/java/com/company/messenger/
├── config/
├── domain/
│   ├── channel/
│   ├── file/
│   ├── message/
│   ├── notice/
│   └── user/
└── global/
    ├── auth/
    ├── exception/
    ├── external/
    └── response/
```

## Functional Spec

### Authentication

- 로그인 요청은 메신저 백엔드로 들어온다.
- 백엔드는 외부 사용자 서비스에 ID와 비밀번호를 프록시 전송한다.
- 인증 성공 시 메신저 자체 JWT Access Token과 Refresh Token을 발급한다.
- Access Token은 메모리에서 사용하고 Refresh Token은 HttpOnly Cookie에 저장한다.
- 동일 계정의 새 로그인은 이전 세션을 만료시킨다.
- 세션 만료 이벤트는 `/user/queue/session-expired`로 전달한다.
- 외부 사용자 서비스의 Base URL과 경로는 설정값으로 변경 가능해야 한다.

### User Directory

- 사용자 목록은 외부 사용자 서비스의 사용자 목록 API를 기준으로 조회한다.
- 메신저는 외부 사용자 정보를 내부 사용자 엔티티와 동기화해 DM 생성과 채널 멤버십에 사용한다.
- 사용자 Presence는 메신저 Redis 상태를 우선 기준으로 반영한다.

### Realtime Chat

- WebSocket 프로토콜은 STOMP를 사용한다.
- 1:1 DM과 그룹 채팅방 모두 메시지 이력을 영구 저장한다.
- 메시지 타입은 텍스트, 이미지, 파일, 시스템, 공지, 외부 알림을 지원한다.
- 메시지 목록은 커서 기반 페이지네이션을 사용한다.
- 타이핑 상태를 실시간으로 전파한다.

### Files

- 업로드 방식은 multipart다.
- 허용 크기는 이미지 10MB, 기타 파일 50MB다.
- 파일은 서버 저장소에 보관하고 다운로드는 인증 사용자를 전제로 한다.

### Presence And Notifications

- 온라인 상태는 Redis TTL 기반으로 관리한다.
- 읽음 상태와 안읽은 메시지 수를 채널별로 관리한다.
- 개인 알림은 앱 내 목록과 OS 네이티브 알림 양쪽으로 노출된다.

### Internal Integrations

- 전체 공지는 내부 API 호출로 브로드캐스트된다.
- 특정 사용자 알림은 온라인 사용자는 실시간 전달, 오프라인 사용자는 저장 후 복구한다.
- 내부 API는 `X-Internal-Api-Key` 헤더로 인증한다.

## Architecture Rules

### Backend

- 패키지 구조는 도메인 중심으로 유지한다.
- 일반 API 응답은 `ApiResponse<T>` 래퍼를 사용한다.
- 예외는 `BusinessException`과 전역 핸들러 패턴으로 통일한다.
- 인증 연동 클라이언트는 별도 클래스로 분리한다.
- Redis는 세션, Refresh Token, Presence, unread count, 대기 알림 저장에 사용한다.

### Frontend

- OS 기능 접근은 preload를 통한 IPC 브리지로만 처리한다.
- 서버 데이터는 React Query로 관리한다.
- UI 상태와 소켓 연결 상태는 Zustand로 관리한다.
- 기능별 코드는 `features/` 중심으로 유지한다.

## API Summary

### Auth And User

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/users/me`
- `GET /api/v1/users`
- `GET /api/v1/users/presence`
- `POST /api/v1/users/presence/heartbeat`

### Channel And Message

- `GET /api/v1/channels`
- `POST /api/v1/channels`
- `POST /api/v1/channels/{id}/members`
- `DELETE /api/v1/channels/{id}/members/{userId}`
- `GET /api/v1/channels/{id}/messages?cursor=&size=30`
- `PATCH /api/v1/channels/{id}/read`
- `PATCH /api/v1/messages/{id}`
- `DELETE /api/v1/messages/{id}`

### File

- `POST /api/v1/files/upload`
- `GET /api/v1/files/{fileId}`

### Notification

- `GET /api/v1/notifications?page=&size=20`
- `PATCH /api/v1/notifications/{id}/read`

### Internal

- `POST /api/v1/internal/notice/broadcast`
- `POST /api/v1/internal/notify/user`

## WebSocket Contracts

### Connect

- `/ws`

### Subscribe

- `/topic/channel/{channelId}`
- `/topic/notice`
- `/user/queue/notifications`
- `/user/queue/session-expired`

### Publish

- `/app/chat.send`
- `/app/chat.typing`

## Data Model

### Core Tables

- `users`
- `channels`
- `channel_members`
- `messages`
- `files`
- `notifications`
- `notices`

### Important State Keys In Redis

- `presence:{userId}`
- `refresh:{userId}`
- `session:{userId}`
- `unread:{channelId}:{userId}`
- `pending-notify:{userId}`
