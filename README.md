# minipj1-backend

잔고(Zango) 백엔드. **Spring Boot 4.1.1 + JDK 21 + PostgreSQL** REST API.

> 전체 스펙의 정본은 문서 저장소 `mini-project/CLAUDE.md` 다.
> 이 저장소 전용 규칙은 `CLAUDE.md`(같은 폴더)에 있다.

`v1.0.0` · 테스트 **202건 통과**

---

## 실행

```bash
cp .env.example .env        # 값을 채운다
./mvnw spring-boot:run      # http://localhost:8080
./mvnw test                 # 202건
```

| 목적 | Git Bash | PowerShell / cmd |
|---|---|---|
| 실행 | `./mvnw spring-boot:run` | `.\mvnw.cmd spring-boot:run` |
| 테스트 | `./mvnw test` | `.\mvnw.cmd test` |

> ⚠️ **`./mvnw` 는 POSIX 셸 스크립트라 PowerShell 에서 실행되지 않는다.** Git Bash 를 쓰거나 `mvnw.cmd` 를 쓴다.
> JDK 가 여러 개 설치된 환경이면 `JAVA_HOME` 과 PATH 가 같은 21 을 가리키는지 확인한다.

- Swagger UI: http://localhost:8080/swagger-ui/index.html
  → 우측 상단 **Authorize** 에 로그인으로 받은 JWT 를 넣어야 보호된 API 를 호출할 수 있다.

---

## 사전 준비

**PostgreSQL 17** 이 로컬에 떠 있어야 한다. Docker 는 쓰지 않는다.

```bash
createdb miniproject1_db
createdb miniproject1_test
```

`db/schema-extra.sql` 을 **최초 1회 수동 적용**한다. 부분 유니크 인덱스는 Hibernate 가 만들지 못한다.

```bash
psql -U postgres -d miniproject1_db -f src/main/resources/db/schema-extra.sql
```

여러 달에 걸친 데이터가 필요하면 시드를 넣는다. 한 달치만으로는 예측 로직을 검증할 수 없다.

| 파일 | 내용 |
|---|---|
| `db/seed-dev.sql` | 테스트 계정 1개 + 최근 6개월 거래 약 400건 |
| `db/seed-perf.sql` | 24개월 20,000건. 성능 측정 전용 |

---

## 환경 변수 (`.env`)

```
DB_URL=jdbc:postgresql://localhost:5432/miniproject1_db
DB_URL_TEST=jdbc:postgresql://localhost:5432/miniproject1_test
DB_USERNAME=
DB_PASSWORD=
JWT_SECRET=                 # raw UTF-8 32자 이상
JWT_EXPIRATION=86400000     # 24시간
CORS_ALLOWED_ORIGINS=http://localhost:3000
GOOGLE_CLIENT_ID=           # 비워 두면 구글 로그인만 동작하지 않는다
GOOGLE_CLIENT_SECRET=
```

> ⚠️ **`JWT_SECRET` 의 "32바이트" 는 디코드 후 기준이 아니다.** 이 프로젝트는 raw UTF-8 문자열을 그대로 키로 쓴다.
> Base64 문자열로 두고 디코드하면 32자가 24바이트가 되어 `WeakKeyException` 으로 **기동에 실패한다.**
>
> ⚠️ **`.env` 는 Spring 이 `.properties` 문법으로 읽는다.** 인라인 주석(`#`)이 값에 포함되므로 주석은 줄 단위로 쓴다.
>
> ⚠️ **구글 로그인의 승인된 리디렉션 URI 는 백엔드 주소다** — `http://localhost:8080/login/oauth2/code/google`.
> Google Cloud Console 에 프론트 주소를 넣으면 `redirect_uri_mismatch` 가 난다.

프로파일은 셋이다.

| 프로파일 | `ddl-auto` | DB |
|---|---|---|
| local (기본값) | `update` | `miniproject1_db` |
| test | `create-drop` | `miniproject1_test` |

---

## API

Base path `/api/v1`. **`GET /data/export` 하나를 뺀 모든 응답이 아래 봉투를 쓴다.**

```json
{ "success": true, "data": { }, "error": null }
{ "success": false, "data": null, "error": { "code": "TRANSACTION_NOT_FOUND", "message": "..." } }
```

| 컨트롤러 | 엔드포인트 |
|---|---|
| `AuthController` | `POST /auth/signup` · `POST /auth/login` · `GET /auth/me` |
| `CategoryController` | `GET` `POST` `/categories` · `PUT` `DELETE` `/categories/{id}` |
| `TransactionController` | `GET` `POST` `/transactions` · `GET` `PUT` `DELETE` `/transactions/{id}` |
| `StatsController` | `GET /stats/monthly?yearMonth=&asOf=` · `GET /stats/recurring?asOf=` |
| `BudgetController` | `GET /budgets?yearMonth=` · `PUT /budgets` (upsert) |
| `DataController` | `GET /data/export` · `POST /data/import` |
| `ChatController` | `POST /chat` — 자연어 조회 (조회 전용) |

- 대시보드 집계는 **엔드포인트 하나로 묶었다.** 다섯으로 쪼개면 같은 테이블을 5번 스캔하고, 각각 따로 만료되어 화면 안에서 숫자가 어긋나는 순간이 생긴다.
- **소유권 불일치는 403 이 아니라 404 다.** 존재 여부를 노출하지 않기 위해서다.
- **`POST /chat` 은 의도를 못 찾아도 4xx 가 아니라 200 + `UNKNOWN` 이다.** 사용자가 예상 밖으로 물어본 것은
  클라이언트 잘못도 서버 오류도 아니다. 4xx 로 만들면 프론트의 에러 경로를 타서 예시 질문을 보여줄 수 없다.

---

## 구조

```
src/main/java/com/example/
├── config/      (11) Security · JWT · CORS · Swagger · OAuth2
├── controller/  (7)  REST 엔드포인트
├── domain/      (13) 엔티티 · Repository
├── dto/         (19) 요청/응답 record
├── exception/   (3)  BusinessException · ErrorCode · GlobalExceptionHandler
└── service/     (13) 비즈니스 로직 · 집계/예측 · CSV
    └── chat/    (4)  자연어 의도 파서 · 응답 조립
```

계층은 `controller → service → repository` 다. 컨트롤러가 리포지토리를 직접 호출하지 않는다.

---

## 이 저장소에서 자주 틀리는 것

- **금액 비교는 `compareTo(...) == 0`.** `BigDecimal.equals` 는 scale 까지 본다 — DB 에서 읽은 `1000.00` 과 코드의 `1000` 이 다르다고 나온다.
- **`divide` 에 scale 과 `RoundingMode` 를 반드시 넘긴다.** 3으로 나누는 순간 `ArithmeticException` 이 난다.
- **모든 집계 쿼리에 `COALESCE(SUM(...), 0)`.** 대상 행이 없으면 `SUM` 은 0 이 아니라 `NULL` 이다.
- **집계·예측 코드에 `now()` 계열 호출이 등장하면 안 된다.** 기준 날짜는 클라이언트가 `yearMonth`·`asOf` 로 보낸다.
- **삭제는 Soft Delete.** `budgets` 만 예외로 물리 삭제한다.
- **거래 조회 시 카테고리 조인에는 `deleted_at IS NULL` 을 걸지 않는다.** 걸면 삭제된 카테고리를 쓰던 과거 내역이 목록에서 사라진다.
- **거래 목록은 `join fetch t.category`.** LAZY 로 두기만 하면 20건에 조회 20번이 더 나간다.
- **정렬에 `id DESC` 를 항상 2차 키로 덧붙인다.** `txnDate` 가 같은 거래가 흔해 페이지네이션에서 중복·누락이 난다.
- **내보내기 CSV 맨 앞에 UTF-8 BOM(`EF BB BF`)을 쓴다.** 없으면 엑셀이 CP949 로 읽어 한글이 전부 깨진다. VS Code 에서는 멀쩡해 보여 발견되지 않는다.
- **챗봇 의도는 구체적인 것부터 판정한다.** 고정지출 → 예산 → 예측 → 일별 → 카테고리 → 최근내역 → 월요약.
  `"고정지출 뭐 있어"` 에는 `지출` 이, `"예상 지출 얼마야"` 에는 `지출`·`얼마` 가 들어 있어
  순서를 뒤집으면 좁은 질문이 넓은 규칙에 먼저 잡힌다.
- **`IntentParser` 는 순수 함수로 유지한다.** 카테고리를 인자로 받으므로 DB 없이 테스트되고,
  넘기는 목록이 인증 사용자의 것뿐이라 소유권 검증이 함께 따라온다.
- **가져오기는 UTF-8 을 `CodingErrorAction.REPORT` 로 시도하고 실패 시 MS949 로 폴백한다.** 기본값 `REPLACE` 로 두면 예외가 나지 않아 폴백이 영영 동작하지 않는다.

---

## 테스트

**로컬 PostgreSQL(`miniproject1_test`)로 돌린다. H2 를 쓰지 않는다.**
집계가 `LOWER(...) LIKE`·`COALESCE`·`NUMERIC` 반올림 등 PostgreSQL 동작에 의존해, DB 를 바꾸면 테스트가 무의미해진다.

```
AuthIntegrationTest          GoogleOAuthIntegrationTest    SchemaConstraintTest
CategoryApiTest              CategoryRepositoryTest
TransactionApiTest           TransactionRepositoryTest
StatsApiTest                 ForecastCalculatorTest        RecurringDetectorTest
BudgetApiTest                DataApiTest                   CsvParserTest
ChatApiTest                  IntentParserTest
```

예측 계산·CSV 파서·챗봇 의도 파서는 DB 없이 입력→출력만 보면 되므로 순수 단위 테스트다.

---

## 커밋

- Conventional Commits: `feat:` `fix:` `refactor:` `test:` `docs:` `chore:` — **본문은 한글**
- 브랜치: `main` ← `develop` ← `feature/{작업명}`
- 프론트엔드와 **별도 저장소이므로 커밋을 섞지 않는다.**
