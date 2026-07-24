# Aiven MySQL 연결

이 프로젝트는 `aiven` Spring 프로필로 Aiven for MySQL에 연결할 수 있다.

## 저장소에 넣을 파일

1. `src/main/resources/aiven-secrets.properties`
   - host
   - port
   - database
   - username
   - password
2. `src/main/resources/certs/aiven-ca.pem`
   - Aiven Console에서 내려받은 CA Certificate 원본

저장소 소유자의 요청에 따라 두 파일 모두 Git 추적 대상으로 두며, 현재 Aiven 서비스의 실제 연결 정보와 CA 인증서가 반영되어 있다.

## 실행

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=aiven"
```

패키징된 JAR:

```powershell
java -jar target/findguni-1.0.0.jar --spring.profiles.active=aiven
```

`application-aiven.properties`는 Aiven의 Java 예시와 같이 JDBC URL에 `sslmode=require`를 넣어 TLS 연결을 강제하고, Flyway로 스키마를 생성·갱신한다.

## 연결 확인

애플리케이션 시작 로그에서 Flyway 마이그레이션 성공과 웹 서버 시작을 확인한 뒤 다음 주소를 연다.

- 플레이: `http://localhost:8080/play/dubu-housewarming`
- 메이커: `http://localhost:8080/maker`
- 플랫폼 관리자: `http://localhost:8080/platform`

초기 계정:

- 메이커: `demo@findguni.local` / `test`
- 관리자: `admin@findguni.local` / `Admin1234!`
