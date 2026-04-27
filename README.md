# Internal Messenger

Electron + React 데스크톱 클라이언트와 Spring Boot 백엔드로 구성된 사내 메신저 프로젝트입니다.

현재 기준으로 Step 1~7까지 구현되어 있으며, 인증, 채팅, 파일 전송, Presence, 내부 공지/알림, Electron 패키징 설정까지 반영되어 있습니다.

## Tech Stack

- Frontend: Electron, React 18, TypeScript, electron-vite, Zustand, React Query, Tailwind CSS, STOMP
- Backend: Spring Boot 3.5.5, Java 21, Spring Security, JWT, Spring WebSocket(STOMP), Spring Data JPA, Redis
- Infra: MySQL 8, Redis 7, Docker Compose

## Project Structure

```text
.
├── electron-app/   # Electron + React desktop client
├── backend/        # Spring Boot API / WebSocket server
├── docker-compose.yml
└── vibe-coding-master-prompt.md
```

## Current Features

- 사내 인증 서버 프록시 로그인
- JWT Access Token + Refresh Token 기반 인증
- Redis 세션 저장소 기반 중복 로그인 차단
- STOMP 기반 실시간 채팅, 타이핑 인디케이터
- 채널 목록 / 메시지 조회 / 읽음 처리 / unread count
- 파일 업로드, 이미지 미리보기, 다운로드
- Redis Presence TTL 기반 온라인 상태 관리
- Electron 네이티브 알림, 시스템 트레이 배지
- 내부 API Key 인증 기반 전체 공지 브로드캐스트
- 특정 사용자 대상 사내 알림 발송 및 알림 탭 조회
- Electron 외부 링크 열기, Windows/macOS 패키징 설정

## Environment

백엔드 기본값은 아래 기준입니다.

- Backend: `http://localhost:8080`
- WebSocket: `ws://localhost:8080/ws`
- MySQL: `localhost:3306 / messenger / root / 1234`
- Redis: `localhost:6379 / messenger_user / 1234`

주요 환경 변수:

- `JWT_SECRET`
- `INTERNAL_API_KEY`
- `INTERNAL_AUTH_BASE_URL`
- `MYSQL_DATABASE`
- `JDBC_USERNAME`
- `JDBC_PASSWORD`
- `JDBC_URL`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_USERNAME`
- `REDIS_PASSWORD`
- `FILE_STORAGE_PATH`

## Run

### 1. Infra

```bash
docker compose up -d
```

### 2. Backend

```bash
cd backend
./mvnw spring-boot:run
```

### 3. Electron App

```bash
cd electron-app
npm install
npm run dev
```

## REST API

모든 일반 API 응답은 `ApiResponse<T>` 래퍼를 사용합니다.

### Auth / User

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/v1/auth/login` | 사내 인증 연동 로그인 |
| `POST` | `/api/v1/auth/refresh` | Refresh Token 기반 Access Token 재발급 |
| `POST` | `/api/v1/auth/logout` | 로그아웃 및 세션/토큰 제거 |
| `GET` | `/api/v1/users/me` | 내 프로필 조회 |
| `GET` | `/api/v1/users/presence?userIds=user01,user02` | 사용자 온라인 상태 조회 |
| `POST` | `/api/v1/users/presence/heartbeat` | 현재 사용자 상태 하트비트 갱신 |

### Channel / Message

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/v1/channels` | 내가 속한 채널 목록 조회 |
| `POST` | `/api/v1/channels` | 채널 생성 |
| `GET` | `/api/v1/channels/{channelId}/messages?cursor=&size=30` | 메시지 커서 페이징 조회 |
| `PATCH` | `/api/v1/channels/{channelId}/read` | 마지막 읽은 메시지 반영 |

### File

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/v1/files/upload` | 파일 업로드 |
| `GET` | `/api/v1/files/{fileId}` | 인증 사용자 파일 다운로드 |

### Notification

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/v1/notifications?page=0&size=20` | 내 알림 목록 조회 |
| `PATCH` | `/api/v1/notifications/{id}/read` | 알림 읽음 처리 |

### Internal API

`X-Internal-Api-Key` 헤더가 필요합니다.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/v1/internal/notice/broadcast` | 전체 사용자 공지 브로드캐스트 |
| `POST` | `/api/v1/internal/notify/user` | 특정 사용자 알림 전송 |

## WebSocket / STOMP

### Endpoint

- Connect: `/ws`

### Subscribe

- `/topic/channel/{channelId}`: 채널 메시지 수신
- `/topic/notice`: 전체 공지 브로드캐스트 수신
- `/user/queue/notifications`: 개인 알림 수신
- `/user/queue/session-expired`: 중복 로그인 강제 만료 수신

### Publish

- `/app/chat.send`: 채팅 메시지 전송
- `/app/chat.typing`: 타이핑 상태 전송

## Frontend Scripts

```bash
cd electron-app
npm run dev
npm run typecheck
npm run build
npm run dist
```

## Backend Test

```bash
cd backend
./mvnw test
```

현재 검증 기준:

- Backend: `./mvnw test` 통과
- Frontend: `npm run typecheck` 통과
- Frontend: `npm run build` 통과

## Notes

- Redis는 `localhost:6379`, 사용자 `messenger_user`, 비밀번호 `1234` 기준으로 설정되어 있습니다.
- 프론트는 Electron IPC로만 OS 기능에 접근하며 `contextIsolation: true`, `nodeIntegration: false`를 유지합니다.
- 내부 공지/알림 API는 외부 사내 시스템이 호출하는 용도입니다.
