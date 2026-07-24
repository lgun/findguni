# Render 무료 티어 배포

이 저장소는 루트의 `render.yaml`을 이용해 Render Blueprint로 배포한다. Java 애플리케이션이므로 다단계 `Dockerfile`에서 빌드하고 JRE 17 이미지로 실행한다.

## 배포

1. 변경 사항을 GitHub 저장소에 커밋하고 푸시한다.
2. Render Dashboard에서 `New > Blueprint`를 선택한다.
3. 이 저장소를 연결하고 루트의 `render.yaml`을 적용한다.
4. 배포가 끝나면 `https://findguni-escape.onrender.com/health`가 `ok`를 반환하는지 확인한다.
5. 실제 게임 주소 `https://findguni-escape.onrender.com/play/dubu-housewarming`를 연다.

서비스 이름이 이미 사용 중이면 `render.yaml`의 `name`과 `FINDGUNI_BASE_URL.fromService.name`을 같은 새 이름으로 변경한다.

## 512MB 메모리 배분

`Dockerfile`의 `JAVA_TOOL_OPTIONS`는 컨테이너 전체 512MB를 넘지 않도록 다음 상한을 둔다.

| 영역 | 상한 |
| --- | ---: |
| Java heap | 256MB |
| Metaspace | 96MB |
| Code cache | 32MB |
| Direct memory | 32MB |
| Thread stack | 스레드당 512KB |

남는 메모리는 JVM 자체, 네이티브 라이브러리, 스레드와 OS에 사용한다. GC는 적은 CPU와 메모리에 맞춰 Serial GC를 사용하고 JVM이 인식하는 CPU를 1개로 제한한다.

애플리케이션 설정도 함께 제한한다.

- Tomcat 작업 스레드 최대 24개
- Aiven Hikari 커넥션 풀 최대 3개, 최소 유휴 연결 0개
- Hibernate 쿼리 계획 캐시 축소
- Thymeleaf와 정적 리소스 캐시 활성화
- JMX 비활성화

QR 인쇄 PDF는 순간적으로 힙을 많이 사용하므로 여러 사용자가 동시에 PDF/ZIP을 생성하지 않는 것을 전제로 한다.

## 무료 인스턴스 특성

무료 웹 서비스는 일정 시간 요청이 없으면 정지하고 다음 요청 때 콜드 스타트가 발생한다. 첫 화면이 열리기까지 시간이 걸릴 수 있으므로 실제 방탈출 시작 전 한 번 접속해 깨워 두는 것이 좋다.

무료 인스턴스의 파일시스템은 영속적이지 않다. 메이커에서 직접 업로드한 사진과 오디오는 재배포, 재시작 또는 유휴 정지 후 사라질 수 있다. 저장소의 `src/main/resources/static`에 포함된 두부 테마 정적 파일과 Aiven DB 데이터는 영향을 받지 않는다.

## Render 환경

- `SPRING_PROFILES_ACTIVE=aiven,render`
- `PORT=10000`
- `FINDGUNI_BASE_URL`: Render가 제공하는 `RENDER_EXTERNAL_URL`을 현재 서비스에서 참조
- `FINDGUNI_COOKIE_SECURE=true`
- `ANSWER_HMAC_SECRET`, `FINDGUNI_REMEMBER_ME_KEY`: Blueprint 최초 생성 시 Render가 각각 생성

Aiven 접속정보와 CA 인증서는 저장소의 `aiven-secrets.properties`와 `certs/aiven-ca.pem`을 사용한다.
