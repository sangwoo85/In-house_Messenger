# Internal Messenger

Electron + React 데스크톱 클라이언트와 Spring Boot 백엔드로 구성된 사내 메신저 프로젝트입니다. 현재 저장소는 구현 코드와 함께, AI 협업을 위한 문서 구조를 `docs/` 아래에 분리해 관리합니다.

## Read First

- 제품 목표: `docs/product.md`
- 현재 범위: `docs/scope.md`
- 기능/아키텍처 명세: `docs/spec.md`
- 구현 규칙과 UI 방향: `docs/style-guide.md`
- 작업 단위 관리: `docs/tasks.md`
- 완료 판단 기준: `docs/acceptance.md`
- 의사결정 로그: `docs/decisions.md`
- AI 작업용 프롬프트: `vibe-coding-master-prompt.md`

## Tech Stack

- Frontend: Electron, React 18, TypeScript, electron-vite, Zustand, React Query, Tailwind CSS, STOMP
- Backend: Spring Boot 3.5.5, Java 21, Spring Security, JWT, Spring WebSocket, Spring Data JPA, Redis
- Infra: MySQL 8, Redis 7, Docker Compose

## Project Structure

```text
.
├── backend/
├── docs/
├── electron-app/
├── docker-compose.yml
├── README.md
└── vibe-coding-master-prompt.md
```

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

## Verification

### Frontend

```bash
cd electron-app
npm run typecheck
npm run build
```

### Backend

```bash
cd backend
./mvnw test
```

## Environment

기본 로컬 환경은 아래 기준을 사용한다.

- Backend: `http://localhost:8080`
- WebSocket: `ws://localhost:8080/ws`
- MySQL: `localhost:3306 / messenger / root / 1234`
- Redis: `localhost:6379 / messenger_user / 1234`

주요 환경 변수:

- `JWT_SECRET`
- `INTERNAL_API_KEY`
- `INTERNAL_AUTH_BASE_URL`
- `INTERNAL_AUTH_LOGIN_PATH`
- `INTERNAL_AUTH_USER_LIST_PATH`
- `MYSQL_DATABASE`
- `JDBC_USERNAME`
- `JDBC_PASSWORD`
- `JDBC_URL`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_USERNAME`
- `REDIS_PASSWORD`
- `FILE_STORAGE_PATH`

프론트엔드 API 주소는 [electron-app/.env.example](/Users/sangwookim/Developer/WorkSpace/Messanger/electron-app/.env.example) 형식으로 `VITE_API_ORIGIN`, `VITE_WS_ORIGIN`을 분리해 변경할 수 있다.
