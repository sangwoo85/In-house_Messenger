# Dummy Users API 구현 기획서

## 1. 목표

Spring Boot 기반으로 간단한 사용자 조회 및 로그인 API를 구현한다.

DB는 MySQL을 사용하고, 애플리케이션 실행 시 필요한 Database, Table, Dummy Data가 없으면 자동으로 생성한다.

---

## 2. 기술 기준

- Java
- Spring Boot
- Spring MVC
- Spring Data JPA
- MySQL
- REST API

복잡한 구조는 사용하지 않는다.

기본 MVC 구조로 작성한다.

```text
Controller
Service
Repository
Entity
DTO
Config 또는 Init
```

---

## 3. DB 접속 정보

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/DummyUsers?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=1234
```

---

## 4. Database 생성 조건

애플리케이션 실행 시 MySQL에 `DummyUsers` Database가 없으면 생성한다.

Database 생성 조건:

```sql
CREATE DATABASE IF NOT EXISTS DummyUsers
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
```

---

## 5. 테이블 생성 조건

테이블 이름은 `Users`로 생성한다.

테이블이 없으면 생성하고, 이미 존재하면 다시 생성하지 않는다.

컬럼은 아래와 같다.

| 컬럼명 | 설명 |
|---|---|
| id | 사용자 ID |
| password | 비밀번호 |
| name | 사용자 이름 |
| dept | 부서 |
| group | 권한 그룹 |
| email | 이메일 |

주의사항:

- `group`은 SQL 예약어일 수 있으므로 JPA 매핑 시 컬럼명을 안전하게 처리한다.
- 필요하면 Entity 필드는 `userGroup`으로 만들고 DB 컬럼명은 `group`으로 매핑한다.

예시:

```java
@Column(name = "`group`")
private String userGroup;
```

---

## 6. Dummy Data 생성 조건

애플리케이션 실행 시 `Users` 테이블에 데이터가 없으면 Dummy Data 30개를 생성한다.

이미 데이터가 있으면 Dummy Data를 다시 생성하지 않는다.

### 필수 생성 ID

아래 ID는 반드시 포함되어야 한다.

```text
ksswy
ksswy1
ksswy2
```

### Dummy Data 조건

- 전체 데이터는 30개만 생성한다.
- ID는 6글자 이내로 랜덤 생성한다.
- 단, 필수 ID `ksswy`, `ksswy1`, `ksswy2`는 반드시 포함한다.
- password는 모든 사용자 동일하게 `1234`로 생성한다.
- dept는 아래 값 중 하나를 사용한다.

```text
외환팀
영업팀
개발팀
QA
```

- group은 아래 값 중 하나를 사용한다.

```text
개발자
사용자
관리자
```

- email은 아래 규칙으로 생성한다.

```text
{id}@wooya.com
```

예시:

```text
ksswy@wooya.com
ksswy1@wooya.com
```

---

## 7. API 목록

구현할 REST API는 2개이다.

---

## 8. 사용자 전체 조회 API

### URL

```http
GET /api/v1/getUserList
```

### 설명

DB의 `Users` 테이블 전체 목록을 조회해서 반환한다.

### 요청 파라미터

없음.

### 응답

HTTP Status 200

```json
[
  {
    "id": "ksswy",
    "password": "1234",
    "name": "홍길동",
    "dept": "개발팀",
    "group": "개발자",
    "email": "ksswy@wooya.com"
  }
]
```

---

## 9. 로그인 API

### URL

```http
POST /api/v1/login
```

### 설명

요청으로 전달받은 `id`, `password`를 DB에서 조회한다.

DB에 동일한 ID와 Password가 존재하면 HTTP Status 200을 반환한다.

일치하지 않거나 사용자가 없으면 HTTP Status 403을 반환한다.

### 요청 방식

`application/json` 기준으로 구현한다.

### Request Body

```json
{
  "id": "ksswy",
  "password": "1234"
}
```

### 성공 응답

조건:

- DB에 id가 존재한다.
- password가 일치한다.

HTTP Status:

```http
200 OK
```

응답 예시:

```json
{
  "message": "login success"
}
```

### 실패 응답

조건:

- id가 존재하지 않는다.
- password가 일치하지 않는다.

HTTP Status:

```http
403 Forbidden
```

응답 예시:

```json
{
  "message": "login failed"
}
```

---

## 10. 구현 구조

아래 구조로 구현한다.

```text
src/main/java
└── com.example.dummyusers
    ├── controller
    │   └── UserController.java
    ├── service
    │   └── UserService.java
    ├── repository
    │   └── UserRepository.java
    ├── entity
    │   └── User.java
    ├── dto
    │   ├── LoginRequest.java
    │   ├── LoginResponse.java
    │   └── UserResponse.java
    └── init
        └── DummyUserInitializer.java
```

---

## 11. JPA 구현 기준

### Entity

`Users` 테이블과 매핑되는 `User` Entity를 생성한다.

`id`를 Primary Key로 사용한다.

```java
@Id
@Column(length = 6)
private String id;
```

`group` 컬럼은 예약어 충돌을 피하기 위해 아래와 같이 처리한다.

```java
@Column(name = "`group`")
private String userGroup;
```

---

## 12. Repository 구현 기준

Spring Data JPA Repository를 사용한다.

필요 메서드:

```java
Optional<User> findByIdAndPassword(String id, String password);
```

---

## 13. Service 구현 기준

Service에서는 아래 기능을 제공한다.

```text
- 전체 사용자 목록 조회
- 로그인 검증
```

Controller에서는 직접 Repository를 호출하지 않는다.

---

## 14. Controller 구현 기준

Controller URL Prefix는 아래와 같다.

```http
/api/v1
```

Controller에서 제공할 메서드는 아래 2개이다.

```text
GET /getUserList
POST /login
```

---

## 15. 초기화 로직 기준

애플리케이션 실행 시 아래 작업을 수행한다.

```text
1. DummyUsers Database가 없으면 생성
2. Users 테이블이 없으면 생성
3. Users 테이블에 데이터가 없으면 Dummy Data 30개 생성
4. Users 테이블에 데이터가 있으면 Dummy Data 생성하지 않음
```

단, Spring Boot의 datasource 연결 전에 Database가 없으면 접속이 실패할 수 있으므로 Database 생성 로직은 별도 설정으로 처리한다.

가능한 방식:

```text
- 최초 연결은 jdbc:mysql://localhost:3306/mysql 로 접속
- DummyUsers Database 존재 여부 확인
- 없으면 생성
- 이후 애플리케이션은 DummyUsers DB로 연결
```

또는 개발 편의상 아래 SQL을 README에 명시하고, 애플리케이션 실행 전 수동 실행해도 된다.

```sql
CREATE DATABASE IF NOT EXISTS DummyUsers
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
```

---

## 16. 반드시 동작해야 하는 조건

- 애플리케이션 실행 시 `DummyUsers` Database가 없으면 생성되어야 한다.
- `Users` 테이블이 없으면 생성되어야 한다.
- `Users` 테이블에 데이터가 없으면 Dummy Data 30개가 생성되어야 한다.
- `Users` 테이블에 데이터가 이미 있으면 Dummy Data를 추가 생성하지 않아야 한다.
- Dummy Data에는 `ksswy`, `ksswy1`, `ksswy2` ID가 반드시 포함되어야 한다.
- 모든 Dummy User의 password는 `1234`여야 한다.
- 모든 email은 `{id}@wooya.com` 형식이어야 한다.
- `/api/v1/getUserList`는 전체 사용자 목록을 반환해야 한다.
- `/api/v1/login`은 id/password가 일치하면 200을 반환해야 한다.
- `/api/v1/login`은 id/password가 불일치하거나 사용자가 없으면 403을 반환해야 한다.
- Controller, Service, Repository 구조를 지켜야 한다.
- JPA를 사용해서 DB를 조회해야 한다.

---

## 17. 구현 제외 사항

이번 구현에서는 아래 기능은 제외한다.

- Spring Security
- JWT
- Session Login
- Password 암호화
- 권한 체크
- 화면 개발
- 프론트엔드 개발
- 복잡한 예외 처리
- Swagger
- MyBatis

---

## 18. 테스트 기준

### 사용자 목록 조회 테스트

```bash
curl -X GET http://localhost:8080/api/v1/getUserList
```

기대 결과:

```text
HTTP Status 200
사용자 30명 반환
```

### 로그인 성공 테스트

```bash
curl -X POST http://localhost:8080/api/v1/login \
  -H "Content-Type: application/json" \
  -d '{"id":"ksswy","password":"1234"}'
```

기대 결과:

```text
HTTP Status 200
```

### 로그인 실패 테스트

```bash
curl -X POST http://localhost:8080/api/v1/login \
  -H "Content-Type: application/json" \
  -d '{"id":"ksswy","password":"wrong"}'
```

기대 결과:

```text
HTTP Status 403
```

---

## 19. Codex 작업 지시

이 문서를 기준으로 Spring Boot 프로젝트에 필요한 코드를 생성한다.

기존 Rule 문서가 있다면 반드시 그 Rule을 따른다.

특히 Java 메서드를 생성하거나 수정할 때는 기존 Java Method Comment Rule을 반드시 적용한다.

```text
목표는 간단한 Dummy Users API 구현이다.
불필요하게 복잡한 구조를 만들지 말고, MVC + JPA 기반으로 단순하고 명확하게 구현한다.
```