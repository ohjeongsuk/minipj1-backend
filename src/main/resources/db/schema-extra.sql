-- Hibernate 가 만들지 못하는 제약만 담는다. 테이블과 일반 인덱스는 ddl-auto 가 만든다.
--
-- 로컬 적용 (기동해서 테이블이 생긴 뒤):
--   & "C:\Program Files\PostgreSQL\17\bin\psql" -U postgres -d miniproject1_db -f src/main/resources/db/schema-extra.sql
--
-- 테스트에서는 RepositoryTestSupport 의 @Sql 이 적용한다.
-- create-drop 은 매 컨텍스트마다 스키마를 새로 만들기 때문이다.
--
-- ⚠️ 이 파일에 DO $$ ... $$ 블록을 쓰지 않는다.
--    Spring 의 @Sql(ScriptUtils)은 세미콜론으로 문장을 쪼개는데 달러 인용을 이해하지 못해
--    블록 내부의 세미콜론에서 잘린다. psql 은 정상 처리하므로 로컬에서는 재현되지 않고
--    테스트에서만 "Unterminated dollar quote" 로 터진다.
--    멱등성은 DROP ... IF EXISTS 로 만든다. @Sql 은 테스트 메서드마다 실행되므로 멱등성이 필수다.

-- 1) 카테고리 이름 중복 방지 (CAT-04)
--    일반 UNIQUE 를 걸면 "식비"를 삭제한 뒤 다시 "식비"를 만들 수 없다.
--    Soft Delete 와 UNIQUE 는 항상 이 충돌을 일으킨다.
--    JPA 의 @Table(uniqueConstraints=...) 로는 부분 인덱스를 만들 수 없어 여기에 적는다.
DROP INDEX IF EXISTS uq_categories_user_name_type;
CREATE UNIQUE INDEX uq_categories_user_name_type
    ON categories (user_id, name, type)
    WHERE deleted_at IS NULL;

-- 2) 금액은 항상 양수다. 부호는 type 으로만 표현한다.
--    음수를 허용하면 "지출 -5000"이 환불인지 입력 실수인지 알 수 없고 집계가 이중 의미를 갖는다.
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS ck_transactions_amount_positive;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0);

ALTER TABLE budgets DROP CONSTRAINT IF EXISTS ck_budgets_amount_positive;
ALTER TABLE budgets ADD CONSTRAINT ck_budgets_amount_positive CHECK (amount > 0);

-- 3) 구글 로그인 (AUTH-09)
--    ddl-auto: update 는 기존 행이 있는 테이블에 NOT NULL 컬럼을 붙이지 못한다.
--    DEFAULT 를 함께 줘야 하므로 여기에 직접 적는다.
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider    VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255);

--    구글 계정에는 비밀번호가 없다. 랜덤 해시를 채우면 "비밀번호가 있는 계정"처럼 보여
--    로그인 경로가 헷갈린다. NULL 을 허용하고 provider 로 분기한다.
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

--    같은 구글 계정이 두 번 가입되지 않게 한다. 로컬 계정은 provider_id 가 NULL 이라 제외된다.
DROP INDEX IF EXISTS uq_users_provider;
CREATE UNIQUE INDEX uq_users_provider
    ON users (provider, provider_id)
    WHERE provider_id IS NOT NULL;
