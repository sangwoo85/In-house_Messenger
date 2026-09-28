# Astra 전체 프로젝트 재검토 — 2026-09-13

검토 대상은 `f16e2e9` 위의 현재 작업 트리다. 기존 미커밋 변경과 신규 파일을 포함하여 백엔드, Electron/React 클라이언트, Usersdummy, Compose, 명세와 테스트를 확인했다. 이번 작업에서는 제품 코드를 수정하지 않았으며 이 보고서만 추가했다.

기존 빌드와 테스트는 통과하지만, 운영 전 해결할 인증·실시간 통신·파일 처리 문제가 남아 있다. 아래 9건 중 6건은 실행으로 확인했고, 3건은 클라이언트와 서버의 호출 흐름을 대조해 확인했다. 파일 다운로드 권한 제한은 기존 구현 상태 문서에도 명시된 위험이다.

P1은 배포 전에 우선 해결할 문제, P2는 일반 수정 우선순위다.

| 번호 | 우선순위 | 발견 사항 | 확인 방식 |
|---|---|---|---|
| 1 | P1 | 로그아웃한 WebSocket이 기존 채널 메시지를 계속 수신 | 실제 WebSocket 재현 |
| 2 | P1 | 로그인한 제3자가 공유되지 않은 타인의 파일을 다운로드 | 서비스 재현, 기존 위험 재확인 |
| 3 | P1 | 기본 설정의 패키지 앱에서 Refresh Cookie가 저장되지 않음 | 설치된 Electron 런타임 재현 |
| 4 | P1 | 허용 범위인 2MB 파일 업로드가 서블릿 단계에서 거절됨 | 실제 HTTP multipart 재현 |
| 5 | P1 | MySQL 8.4와 Compose의 시작 옵션이 호환되지 않음 | MySQL 8.4.6 설정 검사 |
| 6 | P1 | 온라인 수신자가 새 DM의 첫 메시지를 실시간으로 발견하지 못함 | 양쪽 코드 흐름 대조 |
| 7 | P2 | 이전 메시지를 읽으면 이후 메시지의 미읽음 수도 삭제됨 | 서비스 재현 |
| 8 | P2 | 최소화하거나 다른 앱을 사용 중이어도 읽음 처리하고 알림을 생략 | 클라이언트 조건 확인 |
| 9 | P2 | 4,000자 초과 메시지를 전송하면 입력을 지우고 서버 거절을 표시하지 않음 | 검증 규칙·전송 흐름 대조 |

## 1. [P1] 세션 폐기 시 기존 WebSocket도 서버에서 차단해야 한다

위치: [StompAuthChannelInterceptor.java:43](/Users/sangwookim/Developer/WorkSpace/Messanger/backend/src/main/java/com/company/messenger/config/StompAuthChannelInterceptor.java:43), [AuthService.java:89](/Users/sangwookim/Developer/WorkSpace/Messanger/backend/src/main/java/com/company/messenger/domain/user/AuthService.java:89).

인증은 CONNECT 및 이후 SEND/SUBSCRIBE에서 검사한다. 이미 만들어진 구독으로 브로커가 보내는 메시지는 재검사하지 않으며, 로그아웃은 세션 저장소만 삭제한다. 따라서 로그아웃하거나 다른 기기에서 로그인한 뒤에도 이전 연결이 별도의 프레임을 보내지 않고 남아 있으면 메시지를 계속 받을 수 있다. 클라이언트의 자발적인 연결 종료는 서버 권한 검증을 대신하지 못한다.

재현: Alice로 인증 → 채널 구독과 정상 수신 확인 → `AuthService.logout()` 실행 → 세션 조회가 비어 있음을 확인 → 같은 채널로 새 메시지 발행. 이전 소켓에서 `private-after-logout`을 수신했다. 세션 만료 알림 Bean 자체는 실제 `StompSessionExpiryNotifier`로 정상 등록되어 있었다.

수정 방향: 사용자·로그인 세션별 실제 WebSocket 연결을 추적하고 로그아웃/세션 교체 시 닫는다. 연결 종료와 메시지 발행 사이에서도 폐기된 세션으로 데이터가 전달되지 않도록 검증하고 회귀 테스트를 추가한다.

## 2. [P1] 파일 다운로드에서 업로더 또는 공유 채널 권한을 검사해야 한다

위치: [FileService.java:66](/Users/sangwookim/Developer/WorkSpace/Messanger/backend/src/main/java/com/company/messenger/domain/file/FileService.java:66).

현재는 요청한 사용자가 존재하는지 확인한 뒤 `fileId`만으로 파일을 반환한다. 채널 멤버십이나 업로더 여부는 확인하지 않는다. 파일 ID는 순차 증가하므로 다른 대화의 첨부나 아직 공유하지 않은 업로드도 로그인 사용자가 접근할 수 있다.

재현: Alice가 `private.txt` 업로드 → 채널 관계가 없는 `outsider`로 다운로드 호출 → 접근 거절 없이 파일 Resource 응답을 반환했다.

이 항목은 `docs/implementation-status.md`의 기존 제한을 재확인한 것이다. 현재 명세의 단순 로그인 확인은 충족하지만, 비공개 대화와 파일의 접근 경계는 일치하지 않는다. 수정 방향은 미공유 파일은 업로더만, 공유된 파일은 연결된 메시지의 채널 멤버만 허용하는 것이다.

## 3. [P1] 패키지 앱의 `file://` 출처와 Refresh Cookie 정책이 맞지 않는다

위치: [AuthService.java:115](/Users/sangwookim/Developer/WorkSpace/Messanger/backend/src/main/java/com/company/messenger/domain/user/AuthService.java:115), [main.ts:22](/Users/sangwookim/Developer/WorkSpace/Messanger/electron-app/electron/main.ts:22).

기본 dev 설정에서는 `cookie-secure=false`로 `SameSite=Strict` 쿠키를 발급한다. 패키지 앱은 `loadFile()`로 실행되므로 API에 대한 요청이 이 쿠키 정책과 맞지 않는다. 최초 로그인 응답의 Access Token으로 잠시 사용할 수 있어도, 재시작 또는 15분 뒤 갱신 시 Refresh Cookie가 없어 세션 복구에 실패한다.

설치된 프로젝트 Electron으로 독립적인 숨김 창과 테스트 서버를 실행했다. 동일한 HttpOnly/Strict 쿠키에 대해 HTTP 페이지에서는 쿠키 1개 저장 및 다음 요청 전송이 성공했고, `file://` 페이지에서는 저장 0개·전송 없음이었다. 전체 설치 패키지 E2E가 아니라 해당 출처·쿠키 조합을 분리한 재현이다. `prod`의 HTTPS + Secure/None 설정까지 실패한다는 의미는 아니다.

수정 방향: 패키지 앱에서 사용할 API 출처와 세션 저장 경로를 명시적으로 설계한다. 고정 출처의 앱 프로토콜 또는 main 프로세스의 인증 요청 중계를 검토하고, 패키지 실행·재시작·토큰 갱신을 실제 Electron에서 검사한다. 단순히 CORS에 `null`을 추가해도 이 쿠키 저장 문제는 해결되지 않는다.

## 4. [P1] 파일 크기 설정을 실제 multipart 수신 계층에도 적용해야 한다

위치: [application.yml:22](/Users/sangwookim/Developer/WorkSpace/Messanger/backend/src/main/resources/application.yml:22).

서비스의 이미지 10MB/기타 50MB 설정은 있으나 `spring.servlet.multipart.max-file-size`와 `max-request-size`가 없다. Spring Boot의 기본 파일 제한은 1MB, 요청 제한은 10MB이므로 서비스 검증에 도달하기 전에 요청이 거절된다. [Spring Boot MultipartProperties 공식 문서](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/autoconfigure/web/servlet/MultipartProperties.html).

실제 임베디드 서버에 유효한 Bearer Token과 2MiB 파일을 전송했다. 서버에서 `MaxUploadSizeExceededException`이 발생했고 최종 응답은 401이었다. 업로드 실패가 인증 실패로 보이므로 클라이언트의 불필요한 Refresh 요청까지 유발한다.

기존 파일 테스트는 MockMvc의 미리 구성된 multipart와 10/20바이트 제한을 사용해 실제 서블릿 크기 제한을 확인하지 않는다. 수신 제한을 제품 한도 및 multipart 오버헤드에 맞추고, 초과 크기 응답을 413 또는 명시한 파일 오류로 일관되게 처리해야 한다.

## 5. [P1] MySQL 8.4에서 제거된 실행 옵션을 정리해야 한다

위치: [docker-compose.yml:13](/Users/sangwookim/Developer/WorkSpace/Messanger/docker-compose.yml:13).

이미지는 `mysql:8.4`인데 실행 옵션은 `--default-authentication-plugin=mysql_native_password`다. 해당 변수는 8.4에서 제거되었다. [MySQL 8.4 공식 변경 내역](https://dev.mysql.com/doc/refman/8.4/en/mysql-nutshell.html).

로컬의 MySQL 8.4.6 이미지로 볼륨·네트워크를 연결하지 않고 다음 검사를 실행했다.

```sh
docker run --rm --network none mysql:8.4.6 mysqld --validate-config --default-authentication-plugin=mysql_native_password
```

종료 코드 1, `unknown variable 'default-authentication-plugin=mysql_native_password'`, `Aborting`을 확인했다. 현재 실행 중인 별도 MySQL 8.0 컨테이너가 동작하는 것은 저장소의 8.4 Compose 설정이 정상이라는 증거가 아니다. 기본 인증 방식에 맞춰 옵션을 제거하고 신규 개발 환경 기동을 검증해야 한다.

## 6. [P1] 새 채널을 수신자에게 알리는 경로가 필요하다

위치: [useChatRealtime.ts:96](/Users/sangwookim/Developer/WorkSpace/Messanger/electron-app/src/features/chat/useChatRealtime.ts:96), [ChannelService.java:63](/Users/sangwookim/Developer/WorkSpace/Messanger/backend/src/main/java/com/company/messenger/domain/channel/ChannelService.java:63).

수신자는 자신이 이미 조회한 채널만 구독한다. 새 DM 생성은 DB 저장과 요청자 응답으로 끝나며, 이후 메시지도 그 채널 토픽으로만 발행한다. 수신자가 로그인한 상태에서 다른 사용자가 처음 DM을 만들면 새 채널 ID를 알 수 없어 해당 메시지와 네이티브 알림을 받지 못한다.

`handleIncomingMessage`의 알 수 없는 채널 처리 분기는 그 메시지를 먼저 받아야 실행되므로 이 경우의 복구 경로가 아니다. 채널 쿼리에도 주기적 재조회가 없다. 재연결이나 다른 재조회 계기가 생길 때까지 발견이 늦어진다.

수정 방향: 사용자별 큐에 채널 생성/멤버십 이벤트를 보내고 수신자가 채널 목록·구독·최신 이력을 동기화한다. 구독 전에 온 첫 메시지도 이력 조회로 복구해야 한다. 실제 두 클라이언트에서 최초 DM 생성과 수신을 검증한다.

## 7. [P2] 읽음 커서 이후의 미읽음 수를 보존해야 한다

위치: [ChannelService.java:121](/Users/sangwookim/Developer/WorkSpace/Messanger/backend/src/main/java/com/company/messenger/domain/channel/ChannelService.java:121).

읽은 메시지 ID를 저장한 뒤 채널 미읽음 수를 무조건 0으로 만든다. 메시지 조회 응답과 읽음 요청 사이에 새 메시지가 도착하거나 오래된 읽음 요청이 뒤늦게 처리되면 읽지 않은 메시지까지 카운트에서 사라진다. `ChannelMember.markRead`도 더 작은 ID로 읽음 위치가 후퇴하는 것을 막지 않는다.

재현: Alice가 두 메시지 전송 → Bob의 미읽음 수 2 확인 → 첫 메시지만 읽음 처리 → 남아야 할 미읽음 1이 실제로는 0. 재현에서는 Redis 저장소를 메모리 대역으로 교체했으며 실제 서비스의 increment/reset 호출을 그대로 적용했다.

수정 방향: 읽음 커서를 단조 증가시키고 그 이후의 상대 메시지만 집계한다. DB 커밋 및 동시 전송과 Redis 갱신의 순서도 함께 검증한다.

## 8. [P2] 백그라운드 수신을 화면에서 읽은 것으로 처리하지 않아야 한다

위치: [useChatRealtime.ts:83](/Users/sangwookim/Developer/WorkSpace/Messanger/electron-app/src/features/chat/useChatRealtime.ts:83).

읽음 여부는 화면 모드와 선택 채널 ID로만 결정한다. 같은 대화방을 열어 둔 채 앱을 최소화하거나 다른 앱으로 이동해도 들어오는 메시지를 즉시 읽음 처리하고 네이티브 알림 전에 반환한다. 사용자가 확인하지 않은 메시지의 배지와 알림이 모두 사라진다.

수정 방향: 창 포커스와 문서 가시성을 포함해 읽음 조건을 정하고, 백그라운드 수신은 미읽음 및 알림 경로로 처리한다. 포커스를 되찾으면 실제 표시한 최신 메시지까지 읽음 처리한다.

## 9. [P2] 서버가 수락하기 전에 메시지 초안을 폐기하지 않아야 한다

위치: [ChatLayout.tsx:211](/Users/sangwookim/Developer/WorkSpace/Messanger/electron-app/src/features/chat/ChatLayout.tsx:211), [socketService.ts:52](/Users/sangwookim/Developer/WorkSpace/Messanger/electron-app/src/socket/socketService.ts:52), [ChatMessageRequest.java:9](/Users/sangwookim/Developer/WorkSpace/Messanger/backend/src/main/java/com/company/messenger/domain/message/ChatMessageRequest.java:9).

서버는 내용을 4,000자로 제한하지만 입력창과 전송 함수에는 동일한 검증이 없다. `publish()`의 true는 연결된 소켓에 프레임을 전달했다는 뜻인데 화면에서는 이를 저장 성공으로 취급해 초안을 지운다. 4,001자 텍스트를 붙여 넣으면 서버는 검증에서 거절하고, 클라이언트는 실패 내용을 표시하거나 초안을 복구할 응답 경로가 없다. 연결 확인만으로 서버 거절이나 저장 실패에 따른 데이터 손실은 막지 못한다.

수정 방향: 우선 입력과 전송 단계에 길이 검증을 맞춘다. 이후 메시지 요청 ID와 서버의 저장 결과/오류 응답을 연결하고, 수락 전까지 전송 대기 상태 또는 복구 가능한 초안을 보존한다. STOMP 전송 여부만으로 저장 성공을 판단하지 않는다.

## 검증 결과와 한계

| 대상 | 실행 결과 |
|---|---|
| Electron `npm run typecheck` | 통과 |
| Electron `npm run build` | 통과 |
| Backend `./mvnw test` | 28건 중 26건 통과, Redis 2건 건너뜀 |
| Backend Redis 2건 별도 실행 | 저장소 ACL을 사용하는 임시 Redis에서 2건 모두 통과 |
| Usersdummy `./mvnw test` | 7건 모두 통과 |
| 임시 백엔드 회귀 검증 5건 | 결함을 드러내는 기대값 검사 4건 실패, notifier 구성 검사 1건 통과 |
| Electron 쿠키 비교 | HTTP 출처 저장·전송 성공 / file 출처 저장·전송 실패 |
| MySQL 8.4.6 설정 검사 | 제거된 옵션으로 실패 |

임시 백엔드 검증은 현재 main 소스를 별도 디렉터리에 복사해 수행했다. H2와 실제 임베디드 HTTP/WebSocket 서버를 사용하고 외부 인증·세션 저장소·Presence·미읽음 저장소는 테스트 대역으로 분리했다. 기존 데이터베이스와 Redis 데이터를 변경하지 않았다. 별도 Redis는 loopback의 임의 포트에서 실행 후 제거했다. Windows 패키지 실행, 실제 인증 서버/운영 DB까지 연결한 전체 E2E, 부하 테스트, 코드 서명은 이번 검토에서 검증하지 않았다.

재현 소스: [AstraReviewProbeTest.java](/tmp/messenger-astra-review-20260913/src/test/java/com/company/messenger/AstraReviewProbeTest.java), [cookie-probe.cjs](/tmp/messenger-astra-review-20260913/cookie-probe.cjs). 실행 로그: [백엔드](/tmp/messenger-astra-backend-test.log), [Redis](/tmp/messenger-astra-redis-test.log), [Usersdummy](/tmp/messenger-astra-usersdummy-test.log), [추가 검증](/tmp/messenger-astra-probes.log), [쿠키](/tmp/messenger-astra-cookie-probe.log). `/tmp` 자료는 임시 파일이며 삭제될 수 있다.

## 기존 미구현 범위와 문서 상태

- 그룹 생성 UI와 멤버 초대/퇴장, 메시지 수정/삭제, 과거 메시지 조회 UI, 공지 이력 조회, 운영 스키마 마이그레이션은 여전히 미구현이다. 이미 구현 상태 문서에 명시되어 있어 새 결함으로 중복 계산하지 않았다.
- 알림은 첫 20개만 조회하고 배지도 이 페이지에서 계산한다. 더 오래된 미읽음이 있어도 배지가 0일 수 있으므로 페이지네이션과 전체 미읽음 집계를 함께 완성해야 한다.
- Usersdummy의 비밀번호 포함 목록 응답은 자체 구현 기획서에 따른 더미 서비스 계약이다. 메신저 디렉터리 응답으로 이 필드를 전달하지 않는 것을 확인했다. 실제 계정을 사용하는 인증 서비스의 계약으로 그대로 가져갈 수는 없다.
- Backend와 Electron 하위 README는 아직 초기 뼈대 단계라고 설명해 실제 구현 상태와 어긋난다. 최상위 Run 절차에는 기본 로그인 연동에 필요한 Usersdummy 실행 단계도 빠져 있다.
- 기존 테스트의 통과는 정상 단일 요청 흐름 위주다. 실제 multipart, 폐기된 구독, 패키지 쿠키, 최초 DM 수신, 늦게 도착한 읽음 요청, 서버 전송 거절을 검증 범위에 추가할 필요가 있다.

우선 세션 폐기와 파일 접근 권한을 정리하고, 패키지 인증·업로드·Compose를 고친 뒤 새 채널 수신 및 읽음/전송 상태를 보완하는 순서를 권한다.


## 2026-09-14 조치

본 검토의 9개 결함과 당시 미구현 항목을 코드 및 화면에 반영했다. 로그인/사용자 존재 여부는 사내 API를 기준으로 하고 메신저 데이터는 별도 스키마를 사용한다. 외부 properties와 설치 절차, 검증 내용은 [구현 상태](../implementation-status.md)와 [내부망 배포](../intranet-deployment.md)를 참고한다. 실제 회사 API 계약 적용, 회사 인증서 서명/공증과 내부망 현장 설치는 환경 정보가 필요하다.
