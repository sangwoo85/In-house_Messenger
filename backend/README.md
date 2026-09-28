# Messenger Backend — IntelliJ 실행

Spring Boot 3.5.5 / Java 21 / Maven 프로젝트다. 엔트리포인트는 `com.company.messenger.MessengerApplication`이다.

## IntelliJ에서 열기

1. **File → Open**에서 `backend/pom.xml`을 선택하고 프로젝트로 연다. 전체 저장소를 이미 열었다면 `backend/pom.xml`을 **Add as Maven Project**로 등록한다. 현재 로컬 전체 프로젝트 설정에도 이 POM을 연결했다.
2. **Project Structure → Project SDK**를 **JDK 21**로 지정한다. Maven Runner의 JRE도 Project JDK로 설정한다.
3. Maven 동기화 후 실행 목록에서 **Messenger Backend (dev)**를 선택한다. Spring 전용 IDE 플러그인 없이 Java Application 실행 구성을 사용한다.
4. IDE에서 모듈명이 다르게 생성된 경우 **Messenger Backend (Maven dev)**를 선택한다. 이 구성은 모듈명에 의존하지 않고 `spring-boot:run`을 실행한다.

실행 구성은 저장소 전체를 열 때의 `.run/`과 백엔드만 열 때의 `backend/.run/`에 각각 있다. 작업 디렉터리는 항상 `backend`다. 기본 HTTP 포트는 `8082`, WebSocket 경로는 `/ws`다.

Lombok annotation processor는 `pom.xml`에 명시했다. 편집기에서만 Lombok 오류가 보이면 Maven을 다시 동기화하고 Annotation Processing 활성화 여부를 확인한다.

## 외부 개발 설정

MySQL, Redis, 사내 인증/사용자 API가 필요하다. 메신저 전용 스키마 `company_messenger`를 사용한다. 회사 사용자 DB를 데이터소스로 지정하지 않는다.

`backend/config/messenger.properties.example`을 같은 폴더의 `messenger.properties`로 복사하고 DB/Redis 포트·계정과 사내 API 주소를 수정한다. 실제 properties 파일은 Git에서 제외된다. 파일이 없으면 `application-dev.properties`의 기본값을 사용한다.

| 항목 | 개발 기본값 |
| --- | --- |
| MySQL | localhost:3306 / company_messenger |
| Redis | localhost:6379 / messenger_user |
| 인증·사용자 API | http://localhost:9090 |
| Backend | http://localhost:8082 |

다른 위치의 설정을 사용하려면 **Run → Edit Configurations → Environment variables**에 `MESSENGER_CONFIG=/절대경로/messenger.properties`를 지정한다. 운영용 설정은 [내부망 설치 가이드](../docs/intranet-deployment.md)를 따른다. 운영 프로필은 프로그램 인수를 `--spring.profiles.active=prod`로 바꾸고 운영 외부 설정 경로를 지정한다.

빈 전용 DB에서는 Flyway가 스키마를 생성한다. 기존 DB의 baseline 절차는 설치 가이드를 확인하며, IDE 실행을 위해 `ddl-auto=update`나 자동 baseline을 활성화하지 않는다.

개발용 인프라가 없다면 저장소 루트의 `docker compose up -d`를 사용할 수 있다. 기존 서비스가 있으면 실제 포트에 맞춰 properties를 설정한다. `Usersdummy`는 로컬 개발용 API 대체 서버이며 운영에 설치하지 않는다.

## 터미널 검증

백엔드 폴더에서 실행한다.

```sh
./mvnw -DskipTests compile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

기동 확인: `http://localhost:8082/actuator/health`

## 실행 위치와 macOS

개발 프로필은 저장소 루트에서 실행해도 `backend/config/messenger.properties`를 읽는다. 명시한 `MESSENGER_CONFIG`가 우선한다. IntelliJ 자동 생성 실행 구성은 작업 디렉터리를 `backend`로 지정한다. Apple Silicon Mac에서는 Maven의 `macos-apple-silicon` 프로필이 Netty ARM64 DNS 라이브러리를 자동으로 추가한다. POM 변경 후 Maven을 Reload한다.
