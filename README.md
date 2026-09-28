# Internal Messenger

Electron + React 데스크톱 클라이언트와 Spring Boot 백엔드로 구성된 사내 메신저 프로젝트입니다. 현재 저장소는 구현 코드와 함께, AI 협업을 위한 문서 구조를 `docs/` 아래에 분리해 관리합니다.

## 맥북에서 이어서 작업

- [맥북 설치·실행 안내](docs/MACBOOK_SETUP.md)
- [Codex 시작 요청과 현재 구현 인수인계](docs/CODEX_HANDOFF.md)
- [사용자 결정과 전체 작업 내역](docs/WORK_HISTORY.md)
- 프로젝트 작업 지침: [AGENTS.md](AGENTS.md)
- 서버 없이 보는 디자인 시안: `design-preview/messenger.html`

현재 일정 기능까지 포함한 소스가 저장소에 있다. 실제 DB/업로드/접속 비밀값과 Codex 원문 세션은 Git에 포함하지 않으며, 새 환경 재현과 데이터 이전의 차이는 맥북 안내를 따른다.

## Read First

- 제품 목표: `docs/product.md`
- 현재 범위: `docs/scope.md`
- 기능/아키텍처 명세: `docs/spec.md`
- 구현 규칙과 UI 방향: `docs/style-guide.md`
- 작업 단위 관리: `docs/tasks.md`
- 완료 판단 기준: `docs/acceptance.md`
- 현재 구현 상태: `docs/implementation-status.md`
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

### 2. Local user API (development)

별도 터미널에서 실행한다. 로컬 기본 DB 설정과 예외는 [맥북 안내](docs/MACBOOK_SETUP.md)를 참고한다.

```bash
cd Usersdummy
./mvnw spring-boot:run
```

### 3. Backend

```bash
cd backend
./mvnw spring-boot:run
```

### 4. Electron App

```bash
cd electron-app
npm ci
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

## 내부망 배포

[내부망 설치 가이드](docs/intranet-deployment.md)를 기준으로 사내 인증 API와 메신저 전용 DB를 설정한다. 운영 설정은 `deploy/config/*.properties.example`을 복사하여 JAR 밖에 분리하고 `MESSENGER_CONFIG`로 지정한다. 데스크톱 서버 주소도 `MESSENGER_DESKTOP_CONFIG`로 외부 파일을 읽는다.

```sh
./deploy/build-backend-bundle.sh
```

## Environment

기본 로컬 환경은 아래 기준을 사용한다.

- Backend: `http://localhost:8082`
- WebSocket: `ws://localhost:8082/ws`
- MySQL: `localhost:3306 / company_messenger / root / 1234`
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

브라우저 개발 환경은 `electron-app/.env.example`의 `VITE_API_ORIGIN`, `VITE_WS_ORIGIN`을 사용한다. Electron은 외부 `messenger.properties`의 `api.origin`, `websocket.origin`을 우선 사용한다.
