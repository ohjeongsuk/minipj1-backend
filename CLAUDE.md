# minipj1-backend

잔고(Zango) 백엔드. Spring Boot 4.1.1 + JDK 21 + PostgreSQL.

> **전체 스펙의 정본은 부모 저장소의 `mini-project/CLAUDE.md`다.**
> Claude Code는 상위 디렉토리를 거슬러 올라가며 `CLAUDE.md`를 로드하므로,
> 이 저장소에서 작업해도 부모 문서가 함께 읽힌다.
> 이 파일은 **이 저장소를 단독으로 클론했을 때 필요한 것**만 담는다.
> 충돌 시 부모 문서가 우선한다.

---

## 실행

> 개발 환경은 Windows다. `./mvnw`는 POSIX 셸 스크립트라 **PowerShell에서 실행되지 않는다.**

| 목적 | Git Bash | PowerShell / cmd |
|---|---|---|
| 기동 | `./mvnw spring-boot:run` | `.\mvnw.cmd spring-boot:run` |
| 테스트 | `./mvnw test` | `.\mvnw.cmd test` |
| 패키징 | `./mvnw clean package` | `.\mvnw.cmd clean package` |

- 앱: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html

### 사전 준비

```bash
# DB 생성 (최초 1회) — PostgreSQL bin 이 PATH 에 없으면 절대경로를 쓴다
& "C:\Program Files\PostgreSQL\17\bin\createdb" -U postgres miniproject1_db
& "C:\Program Files\PostgreSQL\17\bin\createdb" -U postgres miniproject1_test

# 환경 변수
cp .env.example .env    # 값을 채운다. .env 는 커밋되지 않는다
```

- `JAVA_HOME`과 PATH가 **같은 JDK 21**을 가리키는지 확인한다.
- **`JWT_SECRET`은 raw UTF-8 32자 이상.** 짧으면 `WeakKeyException`으로 기동에 실패한다.
  Base64 문자열로 두고 디코드하면 32자가 24바이트가 되어 같은 오류가 난다.

---

## 프로파일

`application.yml`(공통, `spring.profiles.active: local`) · `application-local.yml` · `src/test/resources/application-test.yml`

| 프로파일 | `ddl-auto` | DB |
|---|---|---|
| local | `update` | `miniproject1_db` |
| test | `create-drop` | `miniproject1_test` |

- **`application.properties`를 만들지 않는다.** `.yml`과 공존하면 `.yml` 설정이 조용히 무시된다.
- **`spring.flyway.enabled: false`를 유지한다.** `flyway-core`가 클래스패스에 있어 자동 구성되는데,
  이 프로젝트의 스키마 소유권은 `ddl-auto` + `db/schema-extra.sql`에 있다. 켜면 둘이 충돌한다.

---

## 패키지 구조 (계층형)

```
com.example
├── config/      Security, JWT, Swagger, CORS 설정
├── controller/  REST API
├── domain/      엔티티, Repository
├── dto/         요청/응답 record
├── exception/   BusinessException, ErrorCode, GlobalExceptionHandler
└── service/     비즈니스 로직, 집계·예측, CSV
```

`controller` → `service` → `repository`. **컨트롤러가 리포지토리를 직접 호출하지 않는다.**

---

## 이 저장소에서 자주 틀리는 것

- **엔티티를 컨트롤러에서 반환하지 않는다.** 항상 DTO(record)로 변환한다.
- **엔티티에 `@Setter`를 열지 않는다.** 상태 변경은 의미 있는 메서드로 (`softDelete()`, `updateAmount()`).
- **생성자 주입만 쓴다** (`@RequiredArgsConstructor`). 필드 `@Autowired` 금지.
- **`@ManyToOne`의 기본값은 EAGER다.** 반드시 `fetch = FetchType.LAZY`를 명시한다.
- **금액은 `BigDecimal`이다.** `double`/`float` 금지.
  - 비교는 `equals`가 아니라 **`compareTo(...) == 0`** (scale까지 비교하므로 `1000` != `1000.00`)
  - `divide`는 **반드시 scale + `RoundingMode`를 함께** 넘긴다. 아니면 `ArithmeticException`.
- **집계 쿼리에 `COALESCE`를 건다.** `SUM()`은 대상 행이 없으면 `0`이 아니라 `NULL`이다.
- **소유권 검증**: 거래·카테고리·예산은 `user_id == 인증 사용자 id`를 확인하고, 불일치 시 **404**(존재 여부 노출 방지).
- **삭제는 Soft Delete**다 (`budgets`만 예외 — 물리 삭제).
- **서버 집계·예측 코드에 `now()` 계열 호출이 등장하면 안 된다.** "오늘"과 "이번 달"은 클라이언트가 `asOf`·`yearMonth`로 보낸다.
- **주석은 한글로 작성한다.** 코드 식별자는 영문.

---

## 테스트

**H2를 쓰지 않는다.** 로컬 PostgreSQL의 `miniproject1_test`를 그대로 쓴다.

```java
// ⚠️ Spring Boot 4 는 테스트 슬라이스 애노테이션의 패키지를 옮겼다.
//    Boot 3 예제를 그대로 옮기면 "cannot find symbol" 로 컴파일에 실패한다.
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)  // 임베디드 DB 교체 방지
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/schema-extra.sql")   // create-drop 이라 제약을 매번 다시 건다
```

- Repository 테스트는 `RepositoryTestSupport` 를 상속하면 위 네 애노테이션이 함께 적용된다.
- **`db/schema-extra.sql` 은 멱등하게 유지한다.** `@Sql` 은 테스트 메서드마다 실행된다.

- `@EnableJpaAuditing`은 **메인 애플리케이션 클래스**에 붙인다. `@Configuration`에 두면 `@DataJpaTest`가 로드하지 않아 `created_at`이 null이 된다.
- 예측·집계 로직과 CSV 파서는 **DB 없이 순수 단위 테스트**로 검증한다.

---

## 커밋

- Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:` — **본문은 한글**
- 브랜치: `main` ← `develop` ← `feature/{작업명}`
- **이 저장소에는 커밋 훅이 없다.** 형식이 자동 검증되지 않으므로 직접 지킨다.
- 프론트엔드와 **별도 저장소이므로 커밋을 섞지 않는다.**
