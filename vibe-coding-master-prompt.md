# 사내 메신저 (Internal Messenger) — 바이브 코딩 마스터 프롬프트

> 이 프롬프트를 AI 코딩 어시스턴트(Cursor, Windsurf, Claude Code 등)에 붙여넣고 바이브 코딩을 시작하세요.

---

## 🎯 프로젝트 개요

사내에서 사용하는 **실시간 메신저 데스크탑 애플리케이션**을 만든다.
Windows와 macOS 모두 동작하는 크로스플랫폼 앱이며, Electron 위에서 React UI를 구동하고 Spring Boot 백엔드와 통신한다.

---

## 🛠️ 기술 스택

### Frontend (Electron Desktop)
- **Electron** (최신 안정 버전) — 크로스플랫폼 데스크탑 앱 셸
- **React 18** + **TypeScript** — UI 컴포넌트
- **Vite** — 번들러 (electron-vite 사용)
- **Zustand** — 전역 상태관리 (Redux 없이 가볍게)
- **TailwindCSS** — 스타일링
- **Socket.IO Client** — WebSocket 실시간 통신
- **Axios** — REST API 통신
- **React Query (TanStack Query)** — 서버 상태 캐싱 및 비동기 처리

### Backend
- **Java 21**
- **Spring Boot 3.5.5**
- **Spring Security** + **JWT** — 인증/인가
- **Spring WebSocket** (STOMP) — 실시간 채팅
- **Spring Data JPA** — DB 접근
- **MySQL 8** — 주 데이터베이스
- **Redis** — 세션 캐시, 온라인 상태 관리, 메시지 큐
- **Maven** — 빌드 도구

---

## 📁 프로젝트 폴더 구조

```
messenger-app/
├── electron-app/                  # Electron + React 프론트엔드
│   ├── electron/                  # Electron 메인 프로세스
│   │   ├── main.ts                # BrowserWindow 생성, 앱 진입점
│   │   ├── preload.ts             # contextBridge IPC 설정
│   │   └── tray.ts                # 시스템 트레이 아이콘
│   ├── src/                       # React 렌더러 프로세스
│   │   ├── app/                   # 라우팅, 전역 레이아웃
│   │   ├── features/              # 기능 단위 모듈
│   │   │   ├── auth/              # 로그인, 회원가입
│   │   │   ├── chat/              # 채팅 UI, 메시지 버블
│   │   │   ├── channels/          # 채널/DM 목록
│   │   │   ├── files/             # 파일 업로드/다운로드
│   │   │   └── notifications/     # 알림, 읽음 처리
│   │   ├── components/            # 공통 UI 컴포넌트
│   │   ├── hooks/                 # 공통 커스텀 훅
│   │   ├── stores/                # Zustand 스토어
│   │   ├── services/              # API 호출 함수 (axios)
│   │   ├── socket/                # WebSocket 연결 및 이벤트 핸들러
│   │   └── types/                 # TypeScript 인터페이스 정의
│   ├── electron-vite.config.ts
│   └── package.json
│
└── backend/                       # Spring Boot 백엔드
    └── src/main/
        ├── java/com/company/messenger/
        │   ├── config/            # Security, WebSocket, Redis, CORS 설정
        │   ├── domain/            # 핵심 도메인
        │   │   ├── user/          # User 엔티티, 서비스, 컨트롤러
        │   │   ├── channel/       # Channel, ChannelMember (1:1 DM + 그룹채팅)
        │   │   ├── message/       # Message 엔티티, 채팅 핸들러
        │   │   ├── file/          # 파일 업로드/다운로드
        │   │   └── notice/        # 공지사항/외부 알림 수신 처리
        │   ├── global/
        │   │   ├── auth/          # JWT 필터, 토큰 유틸, 중복 로그인 차단
        │   │   ├── exception/     # 전역 예외 처리
        │   │   └── response/      # 공통 API 응답 포맷
        │   │   └── external/      # 사내 서버 연동 클라이언트 (depl.co.kr)
        │   └── MessengerApplication.java
        └── resources/
            └── application.yml
```

---

## 🔑 핵심 기능 요구사항

### 1. 사용자 인증 (Auth)

#### 사내 서버 인증 연동
- 로그인 시 메신저 백엔드가 **사내 인증 서버**에 ID/비밀번호를 프록시 전송하여 검증
  ```
  외부 인증 API: POST https://depl.co.kr/login
  Request Body : { "userId": "계정ID", "password": "비밀번호" }
  성공 응답    : HTTP 200
  실패 응답    : HTTP 401 / 403 등
  ```
- 사내 서버 인증 성공 시 메신저 자체 JWT Access Token(15분) + Refresh Token(7일) 발급
- 사내 서버와의 통신은 백엔드에서만 수행 (프론트엔드에서 직접 depl.co.kr 호출 금지)
- Access Token은 메모리에, Refresh Token은 HttpOnly Cookie에 저장

#### 중복 로그인 방지
- 동일 계정으로 다른 기기/인스턴스 로그인 시 **기존 세션 강제 만료**
- Redis에 `session:{userId}` 키로 현재 유효한 세션 ID 1개만 유지
- WebSocket 연결 시점에도 세션 ID 검증, 불일치 시 연결 끊김 + 알림 팝업
- 강제 만료된 기존 기기에는 STOMP 이벤트(`/user/queue/session-expired`)로 통보

#### 앱 재시작 시 자동 로그인
- Refresh Token으로 Access Token 재발급 (사내 서버 재호출 없이 메신저 서버 자체 처리)
- 로그아웃 시 Redis Refresh Token + Session 키 모두 삭제

### 2. 실시간 채팅 (Chat)
- STOMP over WebSocket 프로토콜 사용
- **1:1 Direct Message**: 두 사용자 간 개인 채팅방, DB에 대화 이력 영구 저장
- **단체 채팅방 (Group)**: 다수 사용자 참여, 방장/멤버 역할 구분, 초대/퇴장 기능
- 채팅방별 **대화 상태 저장**: 마지막 읽은 메시지 ID, 입장/퇴장 이력 DB 기록
- 메시지 타입: 텍스트, 이미지, 파일, 시스템 메시지, **공지(NOTICE)**, **외부 알림(EXTERNAL)**
- 메시지 무한 스크롤 (cursor 기반 페이지네이션)
- 메시지 수정/삭제 (Soft Delete)
- 타이핑 인디케이터 (상대방이 입력 중 표시)

### 3. 파일 전송 (File)
- 채팅창에서 파일 드래그 앤 드롭 업로드
- 지원 포맷: 이미지(jpg, png, gif), 문서(pdf, docx, xlsx), 압축파일
- 파일 크기 제한: 이미지 10MB, 기타 50MB
- 파일은 서버 로컬 디스크 or S3 호환 스토리지에 저장
- 다운로드 시 인증된 사용자만 접근 가능한 presigned URL 방식

### 4. 알림 및 상태 관리 (Notifications & Presence)
- 온라인/오프라인/자리비움 상태 — Redis에 TTL로 관리
- 앱이 백그라운드일 때 OS 네이티브 푸시 알림 (Electron Notification API)
- 읽음 확인 (마지막 읽은 메시지 ID 추적)
- 안읽은 메시지 수 배지 (채널별 unread count)
- 시스템 트레이에 미읽음 배지 표시

### 5. 외부 연동 — 공지사항 & 사내 프로그램 알림 수신

#### 전체 공지사항 발송 (사내 서버 → 메신저)
- 사내 서버가 메신저 백엔드 API를 호출하면, 메신저가 **전체 접속자**에게 공지를 브로드캐스트
- API 호출 시 사전 발급한 **API Secret Key** 헤더(`X-Internal-Api-Key`)로 인증
  ```
  POST /api/v1/internal/notice/broadcast
  Header: X-Internal-Api-Key: {secretKey}
  Body  : { "title": "공지 제목", "content": "공지 내용", "sender": "시스템" }
  ```
- 수신된 공지는 모든 채팅방 상단 공지 배너 + 별도 공지 탭에 저장

#### 사내 프로그램 알림 발송 (특정 사용자 대상)
- 사내 프로그램(ERP, 결재시스템 등)이 특정 사용자에게 알림 메시지 전송
  ```
  POST /api/v1/internal/notify/user
  Header: X-Internal-Api-Key: {secretKey}
  Body  : { "targetUserId": "user01", "title": "결재 요청", "content": "휴가 신청 결재 요청이 왔습니다.", "linkUrl": "https://..." }
  ```
- 해당 사용자가 접속 중이면 STOMP로 즉시 전달, 오프라인이면 DB에 저장 후 로그인 시 수신
- 메신저 내 **알림 전용 탭**에서 수신 이력 조회 가능
- 알림 클릭 시 `linkUrl`이 있으면 기본 브라우저로 열기 (Electron `shell.openExternal`)

---

## 🏗️ 아키텍처 원칙

### 백엔드 원칙
- **계층 구조**: Controller → Service → Repository (Domain 중심 패키지)
- **응답 포맷**: 모든 API는 `ApiResponse<T>` 공통 래퍼로 반환
  ```json
  { "success": true, "data": {}, "message": "ok", "timestamp": "..." }
  ```
- **예외 처리**: `@RestControllerAdvice` + `BusinessException(ErrorCode)` 패턴
- **JPA**: 양방향 연관관계 최소화, JPQL/QueryDSL 사용
- **사내 서버 인증 연동**: `WebClient`(또는 `RestClient`) 기반 `InternalAuthClient` 클래스 별도 분리, 타임아웃 3초 설정
- **내부 API 보안**: `X-Internal-Api-Key` 값을 application.yml 환경변수로 관리, 하드코딩 금지
- **Redis 용도**:
  - 사용자 온라인 상태: `presence:{userId}` (TTL 30초, 하트비트로 갱신)
  - Refresh Token: `refresh:{userId}`
  - 현재 유효 세션: `session:{userId}` (중복 로그인 방지)
  - 채널 unread count: `unread:{channelId}:{userId}`
  - 오프라인 사용자 대기 알림: `pending-notify:{userId}` (List 자료구조)

### 프론트엔드 원칙
- **IPC 통신**: Electron main ↔ renderer 간 contextBridge로만 통신, `nodeIntegration: false`
- **상태 분리**:
  - 서버 데이터 → React Query
  - UI 상태 (열린 모달, 선택된 채널 등) → Zustand
  - 소켓 연결 상태 → Zustand
- **소켓 이벤트 규칙**:
  - 구독: `/topic/channel/{channelId}` (채널 메시지)
  - 구독: `/topic/notice` (전체 공지 브로드캐스트)
  - 구독: `/user/queue/notifications` (개인 알림 — 사내 프로그램 알림 포함)
  - 구독: `/user/queue/session-expired` (중복 로그인 강제 만료 수신)
  - 발행: `/app/chat.send`
- **컴포넌트 규칙**: feature 폴더 내 `index.tsx` + `*.hook.ts` + `*.api.ts` 패턴

---

## 📡 핵심 API 설계

### Auth (메신저 자체 API)
```
POST /api/v1/auth/login          # 로그인 (내부에서 depl.co.kr/login 검증 후 JWT 발급)
POST /api/v1/auth/refresh        # 토큰 재발급
POST /api/v1/auth/logout         # 로그아웃 (세션/토큰 삭제)
GET  /api/v1/users/me            # 내 정보 조회
GET  /api/v1/users               # 사용자 목록 조회 (향후 외부 사용자 디렉토리 API 연동 전제)
```

### Channel & Message
```
GET    /api/v1/channels                              # 내가 속한 채널 목록 (1:1 DM + 그룹)
POST   /api/v1/channels                              # 채널 생성 (type: DM | GROUP)
POST   /api/v1/channels/{id}/members                 # 멤버 초대
DELETE /api/v1/channels/{id}/members/{userId}        # 멤버 퇴장
GET    /api/v1/channels/{id}/messages?cursor=&size=30  # 메시지 조회 (커서 페이징)
DELETE /api/v1/messages/{id}                         # 메시지 삭제
PATCH  /api/v1/messages/{id}                         # 메시지 수정
```

### File
```
POST /api/v1/files/upload        # 파일 업로드 (multipart)
GET  /api/v1/files/{fileId}      # 파일 다운로드 (인증 필요)
```

### Presence
```
GET  /api/v1/users/presence?userIds=   # 사용자 온라인 상태 조회
```

### 알림 조회 (사용자)
```
GET    /api/v1/notifications?page=&size=20   # 내 알림 목록 조회
PATCH  /api/v1/notifications/{id}/read       # 알림 읽음 처리
```

### 내부 연동 API (사내 서버 전용 — X-Internal-Api-Key 인증)
```
POST /api/v1/internal/notice/broadcast   # 전체 사용자 공지 브로드캐스트
POST /api/v1/internal/notify/user        # 특정 사용자 알림 전송
```

---

## 🗄️ DB 스키마 (핵심 테이블)

```sql
-- 사용자 (사내 서버 계정 기준, 비밀번호는 메신저 DB에 저장하지 않음)
users (
  id            BIGINT PK AUTO_INCREMENT,
  user_id       VARCHAR(50) UNIQUE NOT NULL,  -- 사내 서버 계정 ID
  nickname      VARCHAR(50),
  profile_image_url VARCHAR(500),
  status        ENUM('ONLINE','OFFLINE','AWAY') DEFAULT 'OFFLINE',
  created_at    DATETIME,
  last_login_at DATETIME
)

-- 채널 (1:1 DM + 단체 채팅방 통합)
channels (
  id          BIGINT PK AUTO_INCREMENT,
  name        VARCHAR(100),                   -- DM은 NULL 허용
  type        ENUM('DM','GROUP') NOT NULL,
  created_by  BIGINT FK → users.id,
  created_at  DATETIME
)

-- 채널 멤버 + 대화 상태
channel_members (
  id                   BIGINT PK AUTO_INCREMENT,
  channel_id           BIGINT FK → channels.id,
  user_id              BIGINT FK → users.id,
  role                 ENUM('OWNER','MEMBER') DEFAULT 'MEMBER',
  joined_at            DATETIME,
  left_at              DATETIME,              -- 퇴장 시 기록 (NULL = 현재 참여 중)
  last_read_message_id BIGINT,               -- 마지막 읽은 메시지 ID (읽음 처리용)
  UNIQUE KEY uq_channel_user (channel_id, user_id)
)

-- 메시지
messages (
  id          BIGINT PK AUTO_INCREMENT,
  channel_id  BIGINT FK → channels.id,
  sender_id   BIGINT FK → users.id,          -- 외부 알림의 경우 NULL
  content     TEXT,
  type        ENUM('TEXT','IMAGE','FILE','SYSTEM','NOTICE','EXTERNAL') NOT NULL,
  file_id     BIGINT FK → files.id,
  is_deleted  TINYINT(1) DEFAULT 0,
  created_at  DATETIME,
  updated_at  DATETIME
)

-- 파일
files (
  id              BIGINT PK AUTO_INCREMENT,
  original_name   VARCHAR(255),
  stored_path     VARCHAR(500),
  mime_type       VARCHAR(100),
  file_size       BIGINT,
  uploader_id     BIGINT FK → users.id,
  created_at      DATETIME
)

-- 알림 (사내 프로그램 → 특정 사용자 알림 이력)
notifications (
  id          BIGINT PK AUTO_INCREMENT,
  user_id     BIGINT FK → users.id,
  title       VARCHAR(200),
  content     TEXT,
  link_url    VARCHAR(500),
  is_read     TINYINT(1) DEFAULT 0,
  created_at  DATETIME
)

-- 공지사항 (전체 브로드캐스트 이력)
notices (
  id          BIGINT PK AUTO_INCREMENT,
  title       VARCHAR(200),
  content     TEXT,
  sender      VARCHAR(100),
  created_at  DATETIME
)
```

---

## 🚀 구현 순서 (Step-by-Step)

아래 순서대로 구현하라. 각 단계가 완료될 때마다 동작을 확인하고 다음으로 넘어간다.

**[Step 1] 프로젝트 초기 세팅** ✅ 완료
- [x] `electron-vite` + React + TypeScript 프론트엔드 프로젝트 생성
- [x] Spring Boot 3.5.5 백엔드 프로젝트 기본 구조 생성
- [x] Maven `mvnw` wrapper 포함
- [x] Docker Compose로 MySQL 8 + Redis 7 로컬 환경 구성
- [x] CORS 설정, application.yml 환경변수 세팅 (dev/prod profile 분리)

**[Step 2] 인증 시스템** ✅ 완료
- [x] User 엔티티, Repository, Service 구현 (비밀번호 컬럼 없음)
- [x] `InternalAuthClient` — WebClient로 `depl.co.kr/login` 호출 구현 (타임아웃 3초)
- [x] Spring Security + JWT 필터 구현 (JwtTokenProvider, JwtAuthenticationFilter)
- [x] 중복 로그인 방지 — Redis `session:{userId}` 세션 관리 + STOMP 만료 이벤트 (`/user/queue/session-expired`)
- [x] 로그인/로그아웃/토큰 재발급 API (Refresh Token → HttpOnly Cookie)
- [x] Electron 로그인 화면 UI (블루 계열, KakaoTalk 스타일) + 로그인 흐름 연결

**[Step 3] 채널 및 채팅 기반** ✅ 완료
- [x] Channel, ChannelMember, Message 엔티티 구현
- [x] WebSocket STOMP 설정 (Spring) — `/app`, `/topic`, `/queue`, `/user` prefix
- [x] 채팅 메시지 발행/구독 핸들러 구현 (ChatMessageHandler, 타이핑 인디케이터 포함)
- [x] React에서 STOMP 클라이언트 연결 (socketService — 지수 백오프 재연결) 및 채팅 UI 구현

**[Step 4] 파일 전송** ✅ 완료
- [x] 파일 업로드 API (Multipart, 이미지 10MB / 기타 50MB 제한)
- [x] 채팅 UI에 파일 드래그앤드롭 + 파일 선택 버튼 추가
- [x] 이미지 인라인 미리보기 및 파일 다운로드 링크 연결

**[Step 5] 알림 & 상태 관리** ✅ 완료
- [x] Redis Presence (온라인 상태 TTL 30초 + 하트비트 갱신) 구현
- [x] 읽음 처리 API 및 unread count 갱신 로직
- [x] Electron 네이티브 알림 구현 (main.ts IPC `notification:show`)
- [x] 시스템 트레이 배지 연동 (tray.ts `tray:setBadge`)

**[Step 6] 외부 연동 (공지 & 사내 알림)** ✅ 완료
- [x] 내부 API 인증 (`X-Internal-Api-Key` 헤더 검증)
- [x] 전체 공지 브로드캐스트 API + STOMP `/topic/notice` 전파 + DB 저장
- [x] 특정 사용자 알림 전송 API (온라인 즉시 STOMP 전달 / 오프라인 DB 저장)
- [x] 프론트 알림 탭 UI (NotificationsPage) + 링크 클릭 시 `shell.openExternal` 연결

**[Step 7] 마무리** ✅ 완료
- [x] electron-builder로 Windows(.exe) + macOS(.dmg) 패키징 설정
- [x] 에러 처리 및 재연결 로직 보완
- [x] 전체 UI 디자인 통일 (블루 계열 / KakaoTalk 레이아웃 패턴)

**[Step 8] 사용자 디렉토리 & DM 진입** ✅ 완료
- [x] 사용자 목록 API 추가 (`GET /api/v1/users`)
- [x] 좌측 탭을 `사용자 목록 / 채팅 / 알림` 구조로 확장
- [x] 사용자 더블클릭 시 기존 1:1 DM 채널로 이동, 없으면 생성 후 우측 채팅창 표시
- [x] DM 생성 시 기존 채널 재사용으로 중복 1:1 채널 방지
- [x] 채팅 입력창에서 `Enter` 입력 시 메시지 즉시 전송
- [x] 좌측 상단에 내 프로필 영역 추가 (프로필 이미지, ID, 이름 자리 표시)

---

## ⚠️ 주의사항 및 코딩 컨벤션

- **보안**: `contextIsolation: true`, `nodeIntegration: false` 반드시 유지
- **TypeScript**: `any` 타입 사용 금지, 모든 API 응답에 타입 정의
- **Java**: Lombok 사용, Record 클래스는 DTO에 활용 (Java 21)
- **외부 API 호출**: `depl.co.kr/login`은 반드시 백엔드에서만 호출, 프론트엔드 직접 호출 금지
- **내부 API 키**: `X-Internal-Api-Key`는 환경변수로 관리, 소스코드/커밋에 포함 금지
- **예외**: 프론트에서 API 에러는 React Query의 `onError`에서 전역 처리
- **소켓 재연결**: 네트워크 끊김 시 지수 백오프(exponential backoff)로 자동 재연결
- **중복 로그인**: 강제 만료 수신 시 프론트에서 즉시 로그아웃 처리 + 안내 다이얼로그 표시
- **환경 분리**: dev/prod profile 분리, 환경변수는 `.env` 파일로 관리 (커밋 금지)

---

## 🎨 디자인 가이드

### 컨셉
- **KakaoTalk 레이아웃 패턴** 참고: 좌측 사이드바(채팅방 목록) + 우측 채팅 영역 2단 구조
- 색상 테마는 **블루 계열** 사용 (카카오 노란색 대신 블루로 교체)

### 색상 팔레트 (TailwindCSS 기준)
```
Primary     : #1D6FE8  (blue-600)   — 버튼, 내 말풍선 배경, 활성 탭
Primary Dark: #1558C0  (blue-700)   — hover 상태
Sidebar BG  : #1E2A3B               — 좌측 사이드바 배경 (다크 네이비)
Chat BG     : #F0F4F8  (slate-100)  — 채팅 영역 배경
Bubble Me   : #1D6FE8  (blue-600)  — 내 메시지 말풍선 (흰 텍스트)
Bubble Other: #FFFFFF               — 상대 메시지 말풍선 (다크 텍스트)
Text Primary: #1A202C  (gray-900)
Text Sub    : #718096  (gray-500)
Notice BG   : #EFF6FF  (blue-50)   — 공지 배너 배경
```

### 레이아웃 구조
```
┌──────────────────────────────────────────────────────┐
│  Titlebar (드래그 가능, 최소화/닫기 버튼)                  │
├──────────┬───────────────────────────────────────────┤
│  사이드바  │  채팅 헤더 (채팅방 이름, 멤버 수)              │
│  (240px) ├───────────────────────────────────────────┤
│          │                                           │
│ [프로필]  │         메시지 영역 (스크롤)                   │
│ ──────── │                                           │
│ 사용자/채팅│  상대 말풍선 (좌측 정렬, 흰 배경)               │
│ /알림 탭  │            내 말풍선 (우측 정렬, 블루 배경)     │
│ 전환 영역 │                                           │
│ ──────── ├───────────────────────────────────────────┤
│ 알림 탭   │  입력창 (파일 첨부 버튼 + 텍스트 + 전송 버튼)    │
└──────────┴───────────────────────────────────────────┘
```

### UI 컴포넌트 규칙
- 말풍선: 내 메시지는 우측 정렬 + 블루, 상대 메시지는 좌측 정렬 + 흰색 + 그림자
- 읽음 표시: 카카오톡처럼 말풍선 옆에 작은 숫자(unread count) 표시
- 공지 메시지: 채팅창 상단 고정 배너 (파란 테두리 카드)
- 외부 알림: 알림 아이콘과 함께 별도 색상(amber) 말풍선으로 구분
- 사이드바 채팅방: 썸네일(프로필/그룹 아이콘) + 채팅방 이름 + 마지막 메시지 + 시간 + 미읽음 배지
- 사용자 목록 탭: 사용자명 + 사번/아이디 + 상태 표시, 더블클릭으로 DM 진입
- 입력창 인터랙션: 전송 버튼 클릭 또는 `Enter` 키 입력으로 메시지 전송
- 내 프로필 카드: 좌측 상단에 프로필 사진, 1줄째 ID, 2줄째 이름 표시 영역 배치
