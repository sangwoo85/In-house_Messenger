# Cloudflare Tunnel 웹 진입점

`messenger.sangwoo.site`의 연결 대상은 `http://localhost:8084`다. Nginx가 웹 빌드를 제공하고 `/api/`, `/ws`를 Mac의 백엔드 `8083`으로 전달한다. Electron도 외부 런타임 설정으로 `https://messenger.sangwoo.site`와 `wss://messenger.sangwoo.site`를 사용한다.

## 빌드와 실행

저장소 루트에서 실행한다.

```sh
cd electron-app
npm run build
npm run build:web
cd ..
docker compose -f deploy/web/compose.yml up -d
```

백엔드는 `backend/config/messenger.properties`의 `server.port=8083`으로 실행한다. 브라우저 출처에는 `https://messenger.sangwoo.site`, `http://localhost:8084`, `http://127.0.0.1:8084`를 허용한다. 인증 비밀키는 실제 로컬 설정 파일에만 보관하며 저장소에 커밋하지 않는다.

웹 빌드는 현재 페이지의 출처를 REST와 WebSocket에 공통 적용하므로 로컬 HTTP와 도메인 HTTPS에서 같은 파일을 사용할 수 있다. Electron 빌드와 웹 빌드는 각각 `out/renderer`와 `out/web`에 보관한다.

Nginx의 공개 진입점은 호스트 loopback에만 바인딩한다. `/api/v1/internal`은 외부 진입점에서 차단하고, 웹 인증 쿠키에는 Secure/HttpOnly/SameSite=Strict를 적용한다. 인증은 기존 백엔드 정책을 그대로 사용한다.

## Tunnel 연결

Cloudflare에 로그인하고 `sangwoo.site` 영역에 대한 Tunnel 권한을 승인한 뒤, 전용 named tunnel의 ingress에 다음을 설정한다. 터널 인증서와 credentials JSON은 저장소 밖 `~/.cloudflared`에 보관한다.

```yaml
ingress:
  - hostname: messenger.sangwoo.site
    service: http://localhost:8084
  - service: http_status:404
```

해당 named tunnel에 DNS hostname을 연결하고 cloudflared를 실행한다. 연결 완료 여부는 로컬 상태와 별도로 공개 HTTPS 주소에서 확인한다.

### 현재 Mac 설정 (2026-09-23)

- Tunnel: `messenger` (`b6eb519e-b2d2-45c3-a679-4fb31d3a3418`)
- DNS: `messenger.sangwoo.site` → 해당 Tunnel CNAME
- 설정 파일: `~/.cloudflared/messenger.yml`
- 로그인 시 자동 실행 및 프로세스 재시작: `~/Library/LaunchAgents/site.sangwoo.messenger-tunnel.plist`
- 터널 로그: `~/Library/Logs/messenger-tunnel/`
- 데스크톱 설정: `~/Library/Application Support/messenger-electron-app/messenger.properties`
- 백엔드 로그: `/tmp/messenger-backend-local.log`
- 웹 프록시: `messenger-web` Docker 컨테이너 (`unless-stopped`)

```sh
cloudflared tunnel info messenger
launchctl print gui/$(id -u)/site.sangwoo.messenger-tunnel
```

터널은 Mac 로그인 시 다시 실행된다. 웹 프록시에는 Docker Desktop, API에는 백엔드·MySQL·Redis·사내 인증 API가 실행 중이어야 한다. 백엔드와 사내 인증 API 자체를 자동 시작하는 설정은 별도로 관리한다.

## 확인

```sh
curl --fail http://localhost:8084/actuator/health
curl --fail https://messenger.sangwoo.site/actuator/health
```

로컬 웹 화면은 `http://localhost:8084/`다. `/api/v1/channels`는 비로그인 시 401, `/api/v1/internal/notice/broadcast`는 404, 허용된 Origin의 `/ws` WebSocket Upgrade는 101이어야 한다.
