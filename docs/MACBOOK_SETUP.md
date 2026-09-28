# 맥북에서 코드와 작업 이어받기

2026-09-29 기준. 이 문서의 표준 로컬 포트는 MySQL 3306, Redis 6379, Usersdummy 9090, Messenger API 8082다. 이전 장비의 개별 설정이나 `deploy/web/`의 8083/8084 구성과 구분한다.

## 1. 저장소 받기

새 경로에 처음 받는 경우:

```sh
mkdir -p ~/Developer/WorkSpace
cd ~/Developer/WorkSpace
git clone --branch main https://github.com/sangwoo85/In-house_Messenger.git Messanger
cd Messanger
git status --short --branch
git log -3 --oneline
```

이미 같은 저장소가 있다면 그 폴더에서 상태를 확인하고, 로컬 변경이 없는 `main`에서 `git pull --ff-only origin main`으로 갱신한다. 작업 중인 변경이 있으면 보존한 다음 합친다. `reset --hard`로 덮어쓰지 않는다.

맥북 Codex에서 이 **저장소 루트 폴더**를 프로젝트로 열고 [인수인계 문서의 시작 요청](CODEX_HANDOFF.md#바로-붙여-넣을-시작-요청)을 입력한다. `electron-app`만 열면 서버와 루트 문서를 놓치기 쉬우므로 루트 기준으로 시작한다.

## 2. 개발 도구 확인

Java 21 JDK, Node.js 22.12 이상(22 계열 권장), npm, Git, 실행 중인 Docker Desktop/Compose가 필요하다. Maven은 저장소의 wrapper를 사용한다. Node 버전 하한은 현재 Vite 7 의존성의 요구사항에 따른다.

```sh
java -version
/usr/libexec/java_home -V
node --version
npm --version
git --version
docker version
docker compose version
```

Java가 다른 버전이면 각 서버 실행 터미널에서 `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`로 JDK 21을 지정한다. 패키지별 `node_modules`, Electron 실행 파일, Java target 디렉터리는 다른 장비에서 복사하지 않고 이 장비에서 다시 설치/빌드한다.

## 3. 디자인만 바로 확인

저장소 루트에서:

```sh
open design-preview/messenger.html
```

브라우저로 열리는 더미 시안이다. 서버·로그인·npm 설치 없이 볼 수 있다. 실제 앱 적용 화면 캡처는 `docs/previews/messenger-chat.png`, `messenger-schedule.png`, `messenger-users.png`에 있다. 더미는 화면 참고용이므로 실제 데이터가 저장되지는 않는다.

## 4. 실제 앱 로컬 실행

아래는 빈 개발 DB에서 시작하는 표준 구성이다. 이미 MySQL/Redis가 있으면 중복 실행하지 말고 포트와 접속 값을 맞춘다. 개발용 기본 비밀번호 `1234` 및 `change-me` 값은 로컬 예제다.

### 터미널 A: MySQL/Redis

저장소 루트에서:

```sh
docker compose up -d mysql redis
docker compose ps
docker compose logs --tail=30 mysql redis
```

MySQL이 접속 준비를 마친 뒤 다음 단계로 진행한다. Compose가 `company_messenger` 스키마를 생성한다. Redis ACL은 `infra/redis/users.acl`에 있으며 앱의 기본 계정은 `messenger_user`다. 기존 볼륨을 삭제하는 `docker compose down -v`는 이어받기 과정에서 필요하지 않다.

### 터미널 B: 개발용 사용자 API

```sh
cd ~/Developer/WorkSpace/Messanger/Usersdummy
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw spring-boot:run
```

기본 설정은 `localhost:3306`, root/1234이며 시작할 때 별도 `DummyUsers` 스키마를 만든다. 비어 있는 Users 테이블에 더미 사용자 30명을 생성한다. 기본 로그인 계정 `ksswy`, `ksswy1`, `ksswy2`의 비밀번호는 `1234`다. 이미 데이터가 있으면 seed를 다시 넣지 않는다.

설정 변경이 필요하면 `Usersdummy/config/usersdummy.properties.example`을 참고해 gitignore된 실제 파일을 만든다. **이 예제는 이전 장비용 포트 3307과 `USERSDUMMY_DB_PASSWORD` placeholder를 사용하므로 그대로 복사하면 위의 기본 3306 구성과 다르다.** 표준 구성에서는 별도 파일 없이 실행하거나, JDBC/부트스트랩 URL을 모두 3306으로 맞춘다. 외부 파일은 `USERSDUMMY_CONFIG`로 지정할 수 있다.

### 터미널 C: Messenger 서버

```sh
cd ~/Developer/WorkSpace/Messanger/backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

`dev` 기본값으로 MySQL 3306의 `company_messenger`, Redis 6379, 사용자 API 9090에 연결하고 API 8082로 실행한다. 시작 시 Flyway V1–V7을 적용한다. Usersdummy 스키마를 Messenger datasource로 지정하지 않는다.

개별 설정이 필요할 때만 `backend/config/messenger.properties.example`을 실제 `messenger.properties`로 복사해 수정한다. `MESSENGER_CONFIG`로 다른 절대 경로를 지정할 수도 있다. 루트 `.env`는 Docker Compose용이며 Maven 실행에 자동으로 로드되지 않는다. 서버에 값을 전달하려면 외부 properties 또는 터미널 환경 변수를 사용한다.

별도 터미널에서 확인:

```sh
curl --fail http://localhost:8082/actuator/health
curl --fail http://localhost:9090/api/v1/getUserList
```

### 터미널 D: Electron

```sh
cd ~/Developer/WorkSpace/Messanger/electron-app
npm ci
```

맥북에 이전 Electron 사용자 설정이 있을 수 있으므로 명시적인 로컬 설정을 쓰면 연결 대상이 분명하다. 저장소 루트에서 아래 명령은 파일이 없을 때만 생성한다.

```sh
if [ ! -f electron-app/messenger.properties ]; then
  cat > electron-app/messenger.properties <<'CONFIG'
api.origin=http://localhost:8082
websocket.origin=ws://localhost:8082
CONFIG
fi
cd electron-app
MESSENGER_DESKTOP_CONFIG="$PWD/messenger.properties" npm run dev
```

이미 파일이 있다면 내용이 실제 로컬 API 주소인지 확인한다. `api.origin`에는 `/api`를, `websocket.origin`에는 `/ws`를 붙이지 않는다. 앱이 경로를 추가한다. 이 실제 파일은 Git에서 제외된다.

`ksswy` / `1234`로 로그인해 사용자 목록, 대화방, 일정 패널을 확인한다. 실제 앱의 기능에는 위 서버들이 필요하다. 디자인 더미와 달리 실제 앱은 HTML만으로 서버 저장 기능을 실행하지 않는다.

## 5. 검증 명령

```sh
# 저장소 루트에서
(cd backend && ./mvnw test)
(cd electron-app && npm run typecheck && npm run build && npm run build:web)
(cd electron-app && npm run test:desktop)
(cd electron-app && npm run test:notifications)
```

Electron 검사는 전용 테스트 프로필/로컬 API fixture를 사용하고 앱 창을 띄운다. 정상 데이터에 메시지를 보내는 검사가 아니다. 실행 중인 실제 앱과 구별한다.

백엔드 검사 중 Redis에 연결할 수 없으면 기존 Presence/안읽음 검사 2개가 스킵된다. `backend/target/surefire-reports/`에서 통과·실패·스킵을 구분한다. 테스트용 Redis 연결은 `application-test.properties`의 기본 계정과 실제 실행 환경이 맞아야 한다. 공유 Redis 대신 별도 테스트 인스턴스를 사용하는 것이 좋다.

### 일정 UI 검사에 필요한 Playwright

현재 Playwright는 앱 의존성이 아니다. 별도 테스트 런타임 폴더에 설치해 사용할 수 있다.

```sh
UI_TEST_RUNTIME="$HOME/.cache/messenger-ui-tests"
npm install --prefix "$UI_TEST_RUNTIME" playwright
cd ~/Developer/WorkSpace/Messanger/electron-app
PLAYWRIGHT_PATH="$UI_TEST_RUNTIME/node_modules/playwright" \
CHROME_PATH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
npm run test:schedules
```

Chrome이 없다면 위 런타임의 `node_modules/.bin/playwright install chromium`으로 테스트용 Chromium을 설치한 후 `CHROME_PATH`를 생략한다. 검사 항목은 사용자 필터, 일정 등록/수정/취소, 시간 오류, 장소 해제, 다시 알림/대화방 열기, 말풍선 대비, 1440/1100 너비다. 테스트 캡처는 `/tmp/messenger-*-applied.png`에 저장된다.

## 6. 이전 장비의 실제 데이터가 필요한 경우

Git clone은 소스와 문서를 이동한다. 기존 채팅 메시지, 일정, 업로드 첨부, DB 볼륨, Redis 세션, 로그인 쿠키, Codex의 원문 작업 기록은 복사되지 않는다.

- 개발을 새로 시작하는 데 기존 데이터는 필요하지 않다. 위의 빈 개발 DB와 더미 계정으로 실행할 수 있다.
- 기존 Messenger 데이터를 유지하려면 해당 전용 MySQL 스키마를 별도로 백업/복원하고 업로드 저장소를 함께 옮긴다. 파일 경로도 새 장비에 맞춘다. DB dump와 첨부 원본은 Git에 올리지 않는다.
- 사내 연결 설정은 예제에서 새로 만들거나 승인된 별도 경로로 전달한다. 인증서, API 키, 터널 credentials를 저장소에 넣지 않는다.
- 이번 인수인계 문서로 작업 맥락을 이어간다. 기존 Codex 작업을 앱 안에서 같은 대화로 복구하는 기능을 이 저장소가 제공하는 것은 아니다.

## 7. 빠른 문제 해결

| 증상 | 확인할 것 |
| --- | --- |
| 로그인 실패/인증 서버 연결 오류 | Usersdummy 9090 실행, `ksswy` seed 여부, backend의 `app.external.legacy-user-api=true`(dev 기본값) |
| MySQL 연결 실패 | 3306/3307 혼동, 실제 properties 및 환경 변수 우선순위, Docker 준비 상태 |
| Redis 연결/ACL 오류 | `messenger_user`와 ACL, 비밀번호, 호스트 포트 |
| Flyway에서 기존 스키마 오류 | 기존 DB 이력과 schema 확인. 자동 baseline/테이블 삭제로 우회하지 않고 [이행 문서](intranet-deployment.md#db-마이그레이션과-기존-데이터) 확인 |
| Electron이 예전 서버로 연결 | `MESSENGER_DESKTOP_CONFIG`와 userData의 `messenger.properties` 확인 후 완전히 재실행 |
| 일정 API 404/테이블 없음 | 새 backend 코드로 재시작, Flyway V7 적용 여부 |
| 디자인 파일은 열리는데 API가 없음 | 더미와 실제 앱 구분. 실제 앱은 backend/Redis/DB/사용자 API 필요 |
| 브라우저용 빌드 파일을 직접 열면 안 됨 | `out/web`은 서버용 번들. 파일로 여는 시안은 `design-preview/messenger.html` |

웹/Tunnel 실행은 로컬 개발의 필수 단계가 아니다. 필요할 때 [기존 웹 배포 기록](../deploy/web/README.md)을 읽고 해당 장비의 포트·권한·설정을 별도로 확인한다.
