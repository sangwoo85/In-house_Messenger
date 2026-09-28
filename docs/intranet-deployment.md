# 내부망 설치 및 사내 API 연동

## 구성과 데이터 경계

데스크톱 → 메신저 서버(REST/WebSocket) → 사내 인증·사용자 API 순서로 연결한다. 메신저 서버만 사내 API에 접근한다. 사원 DB에 직접 연결하거나 사용자 비밀번호를 메신저 DB에 저장하지 않는다.

메신저의 MySQL 스키마 기본 이름은 `company_messenger`다. 회사 환경에서는 `database.properties`의 JDBC URL로 별도 이름을 지정할 수 있다. DB 계정은 해당 스키마만 접근하도록 발급한다. `users` 테이블은 외부 사용자 ID와 이름·부서 등 채팅 표시/외래키용 참조이며, 계정 존재 여부의 원본은 사내 API다. 로그인, 토큰 갱신, 사용자 목록, 채널 생성/초대, 개인 알림 대상 확인 때 API를 조회한다. API 장애 시 로컬 사용자 목록으로 인증을 우회하지 않는다. 이미 발급된 Access Token은 세션이 취소되지 않는 한 만료까지 유효하다(기본 15분).

Redis는 세션·Refresh Token·접속 상태를 저장한다. `app.redis.key-prefix` 기본값 `company-messenger:`로 다른 시스템과 키를 구분한다. 환경마다 다른 prefix를 사용한다. 안읽음 수와 읽음 위치는 MySQL이 원본이다. 현재 WebSocket 브로커와 접속 소켓 관리는 단일 백엔드 인스턴스를 전제로 한다.

## 사내 API 계약

`integrations.properties`에 회사 API 계약을 지정한다. 운영 기본 매핑은 `emprId/password`, `emprId/userName/departmentName/departmentId/userGroup`이며 `app.external.fields.*`로 필드와 응답 루트 경로를 바꿀 수 있다. 응답에 설정된 로그인 성공 필드(기본 `success`)가 있으면 기대값(기본 `true`)인지 검사한다. 예제 전체는 `deploy/config/integrations.properties.example`을 따른다.

아래는 **개발용 Usersdummy 호환 모드**다. 운영 기본과 달리 `app.external.legacy-user-api=true`를 명시한다. 회사 API의 추가 인증 헤더가 필요하면 `InternalAuthClient`에 해당 계약을 연결한다.

```properties
app.external.auth-base-url=https://users.internal
app.external.auth-login-path=/api/v1/login
app.external.user-list-path=/api/v1/getUserList
app.external.auth-timeout-seconds=5
app.external.legacy-user-api=true
```

로그인: `POST /api/v1/login`, JSON `{"id":"employee001","password":"..."}`. 2xx는 성공, 4xx는 인증 실패, 통신 장애/5xx는 서비스 이용 불가로 처리한다. 이 개발용 legacy 모드에서만 응답 본문 대신 HTTP 상태로 판단하고 사용자 목록에서 프로필을 조회한다. 운영 기본 모드는 위 필드 매핑과 성공 필드를 사용한다.

사용자 존재 확인: `GET /api/v1/getUserList`, JSON 배열:

```json
[{"id":"employee001","name":"홍길동","dept":"개발팀","group":"직원"}]
```

`id`, `name`은 필수이며 각각 최대 50자다. `dept`, `group`은 선택 항목이다. API가 반환하지 않은 ID는 새 로그인/갱신/초대 대상으로 인정하지 않는다. 이 구현은 전체 사용자 목록 API를 사용한다. 회사에서 단일 사용자 조회 API를 제공한다면 해당 계약에 맞춰 어댑터를 변경하면 된다. 운영에서는 `Usersdummy`를 설치하지 않는다.

## 외부 properties

`deploy/config`의 모든 `*.properties.example`을 같은 디렉터리의 `*.properties`로 복사한다. 운영 서버 설정은 애플리케이션 JAR 밖에 둔다.

| 파일 | 내용 |
| --- | --- |
| `messenger.properties` | 다른 네 파일을 상대 경로로 import |
| `database.properties` | 메신저 전용 MySQL, Redis, Redis 키 prefix |
| `security.properties` | JWT, 내부 연동 API 키, 쿠키, 브라우저 출처 |
| `integrations.properties` | 사내 인증·사용자 API 주소와 경로, 시간 제한 |
| `runtime.properties` | 바인딩 주소/포트, 업로드 저장소/제한, 로그 |

`MESSENGER_CONFIG`에는 `messenger.properties`의 절대 경로를 지정한다. `prod` 프로필은 파일이 없으면 기동을 중단한다. 기본 개발용 비밀키나 localhost 설정이 운영에 자동 적용되지 않는다. 환경 변수 placeholder는 `MESSENGER_DB_PASSWORD`, `MESSENGER_REDIS_PASSWORD`, `MESSENGER_JWT_SECRET`(32바이트 이상), `MESSENGER_INTERNAL_API_KEY`다. 파일에 직접 설정할 수도 있으며 서버 운영 계정만 읽도록 권한을 제한한다.

예시 JDBC는 TLS 인증서 검증을 사용한다. 회사 CA를 JVM과 데스크톱 OS 신뢰 저장소에 등록하고 실제 서버 이름을 인증서와 맞춘다. HTTPS에서 `app.auth.cookie-secure=true`를 사용한다. 내부 정책상 HTTP를 쓰는 환경에서는 이 값을 `false`로 명시해야 한다. 브라우저도 배포한다면 `app.web.allowed-origins`에 정확한 출처를 지정한다. 데스크톱 로그인은 Electron 메인 프로세스에서 실행하며 Refresh Token은 HttpOnly 쿠키 저장소에 보관한다.

## 빌드와 폐쇄망 반입

인터넷 사용이 가능한 빌드 장비에서 Java 21, Node/npm을 준비한다.

```sh
./deploy/build-backend-bundle.sh
cd electron-app
npm ci
npm run test:desktop
npm run dist
```

백엔드 산출물은 `release/company-messenger-backend.tar.gz`다. 실행 JAR, 설정 예시, Linux/macOS 셸 및 Windows PowerShell 실행 파일, systemd 예시와 이 문서를 포함한다. 실제 properties/비밀번호는 포함하지 않는다. 내부망에서는 Maven/npm으로 다시 빌드할 필요가 없다. 백엔드에 Java 21, MySQL 8.4, Redis 7과 회사 CA를 별도로 준비한다. Docker를 사용하는 경우 조직이 승인한 이미지 파일을 외부에서 `docker save` 후 반입하여 `docker load`한다. 저장소의 Compose/Redis ACL은 로컬 개발용이다.

데스크톱은 대상 OS/CPU에서 빌드한다. `npm run dist`는 개발/검증용 패키지다. 정식 배포에는 `npm run dist:release`를 사용한다. `CSC_LINK`, `CSC_KEY_PASSWORD`가 필요하며 macOS는 추가로 `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID`를 요구하고 notarization을 활성화한다. 서명/공증은 인증서와 외부 Apple 서비스 접근이 가능한 빌드 장비에서 수행한 후 산출물을 반입한다. 현재 저장소에 회사 서명 인증서는 없다.

## 백엔드 실행

빈 메신저 전용 DB와 해당 DB의 DDL/DML 권한을 가진 전용 계정을 준비한다. 스키마 기본 문자셋은 `utf8mb4`를 권장한다. 설정의 스키마 이름과 일치시킨다. 업로드·로그 경로를 운영 계정 소유로 만든다.

배포 압축을 해제하고 config 예시를 실제 파일로 복사한 뒤 값을 채운다.

```sh
export MESSENGER_CONFIG=/etc/company-messenger/messenger.properties
/opt/company-messenger/start-backend.sh
```

Windows에서는 동일한 JAR/설정 묶음을 사용한다. `runtime.properties`의 저장/로그 경로는 `C:/CompanyMessenger/data/uploads`처럼 Windows 경로로 변경한다.

```powershell
$env:MESSENGER_CONFIG = 'C:/CompanyMessenger/config/messenger.properties'
& 'C:/CompanyMessenger/start-backend.ps1'
```

systemd를 사용하는 경우 `deploy/systemd/company-messenger.service`를 참고한다. `messenger` 계정, `/var/lib/company-messenger`, `/var/log/company-messenger`를 먼저 준비하고 `/etc/company-messenger/secrets.env`에 위 환경 변수를 설정한다. TLS 프록시에서 `/api/`와 `/ws`의 WebSocket Upgrade를 전달한다. 업로드 프록시 제한은 앱의 52MB 요청 제한 이상으로 설정한다. `/actuator/health`로 기동 상태를 확인한다. 데스크톱에 필요한 것은 메신저 HTTPS/WSS 접근이며 DB/Redis는 서버에서만 접근한다.

## DB 마이그레이션과 기존 데이터

새 빈 DB는 Flyway가 V1–V7을 적용한 뒤 Hibernate가 스키마를 검증한다. V1/V2는 초기 테이블·중복 전송 방지·DM 키·인덱스, V3는 조직/프로필, V4는 공감/읽음, V5는 알림 전달 옵션, V6는 답장, V7은 일정/개인별 알림이다. `ddl-auto=update`를 사용하지 않는다. 적용된 마이그레이션 파일은 수정하지 않고 새 버전을 추가한다.

이전 버전의 Hibernate로 만든 DB는 자동 baseline하지 않는다. 먼저 DB와 업로드 디렉터리를 백업하고 복제 DB에서 V1의 테이블/컬럼/타입/제약 조건과 기존 스키마를 비교한다. **V1과 호환되는 기존 스키마임을 확인한 경우에만** 최초 한 번 `--spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=1`을 추가해 실행한다. 그러면 기존 V1 데이터를 유지하고 V2부터 현재 버전까지 적용한다. 첫 실행 후 두 옵션은 제거한다. 이미 변경된 스키마나 다른 회사 업무 DB에 baseline을 적용하지 않는다. Flyway 이력이 없는 DB에 V2 컬럼이 이미 있다면 운영 DBA가 실제 상태에 맞춰 이행해야 한다.

DB 이름 변경만으로 이전 데이터가 이동하지 않는다. 기존 메신저 데이터를 유지하려면 메신저 스키마를 새 전용 DB로 복원한 뒤 위 절차를 수행한다. 업로드 파일도 DB의 저장 경로에 맞춰 함께 이동한다. 롤백은 데이터 백업·업로드 백업·호환되는 JAR를 함께 복원하는 절차로 준비한다.

## 데스크톱 실행 설정

패키지에 포함된 `messenger.properties.example`을 복사해 아래 값을 채운다. 회사 사용자 API 주소가 아니라 **메신저 서버** 주소다.

```properties
api.origin=https://messenger.internal
websocket.origin=wss://messenger.internal
```

`MESSENGER_DESKTOP_CONFIG`에 파일의 절대 경로를 지정하면 앱을 다시 빌드하지 않고 서버 주소를 바꿀 수 있다. 지정하지 않으면 Electron `userData` 디렉터리의 `messenger.properties`를 읽는다. 패키지 실행 시 파일이 없으면 필요한 경로를 오류 창으로 안내한다. 회사 배포 도구에서 설정 파일과 환경 변수를 함께 설치한다. 파일은 UTF-8 `key=value` 형식이며 경로에 `/api`, `/ws`를 붙이지 않는다. 변경 후 앱을 완전히 종료하고 다시 실행한다.

## 설치 확인

- 실제 사내 계정으로 로그인, 잘못된 비밀번호/존재하지 않는 계정 거부, 앱 재실행 후 로그인 복구 확인
- 두 계정으로 새 DM 첫 메시지, 그룹 생성·초대·퇴장, 메시지 수정 거부·읽기 전 삭제와 이전 이력 확인
- 제3자 파일 다운로드 거부, 탈퇴 후 메시지/파일 접근 거부, 로그아웃한 소켓의 새 메시지 수신 차단 확인
- 백그라운드 알림·배지와 화면에 보이는 메시지의 읽음 처리, 2MB 이상 파일 업로드 확인
- 사내 연동 키로 전체 공지·개인 알림을 전송한 뒤 이력과 다음 페이지 확인
- 서버 재기동 후 Flyway 현재 버전 v7/변경 없음, 메시지·첨부파일 보존 확인
