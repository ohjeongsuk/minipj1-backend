-- 로컬 개발용 시드. local 프로파일에서 수동 실행한다.
--
--   & "C:\Program Files\PostgreSQL\17\bin\psql" -U postgres -d miniproject1_db -f src/main/resources/db/seed-dev.sql
--
-- ⚠️ 한 달치만 넣으면 Phase 5 의 예측 로직을 전혀 검증할 수 없다.
--    기준선이 직전 3개월이므로 최소 4개월치가 필요하다. 여기서는 6개월을 넣는다.
-- ⚠️ 고정지출 감지용 데이터를 의도적으로 심는다.
--    같은 상호 + 비슷한 금액이 3개월 연속 있어야 감지 대상이 된다.
-- ⚠️ type·amount·txn_date·category_id 는 NOT NULL 이고 DB DEFAULT 가 없다. 반드시 명시한다.
--
-- 재실행 가능하도록 테스트 계정의 데이터를 먼저 지운다.

BEGIN;

-- 기존 시드 계정 정리 (물리 삭제. 시드 전용 계정이므로 Soft Delete 규칙의 예외다)
DELETE FROM budgets      WHERE user_id IN (SELECT id FROM users WHERE email = 'dev@moneylog.local');
DELETE FROM transactions WHERE user_id IN (SELECT id FROM users WHERE email = 'dev@moneylog.local');
DELETE FROM categories   WHERE user_id IN (SELECT id FROM users WHERE email = 'dev@moneylog.local');
DELETE FROM users        WHERE email = 'dev@moneylog.local';

-- 1) 테스트 계정 (비밀번호: password1)
INSERT INTO users (email, password, nickname, created_at, updated_at)
VALUES ('dev@moneylog.local',
        '$2a$10$rPtHwCUMCpFcjEXsuE50ouJ4aTKymJZ6nWX8UgAcSBK6BgWeE6WnG',
        '개발테스터',
        now() AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC');

-- 2) 기본 카테고리 9개 (AuthService.signup 과 같은 구성)
INSERT INTO categories (user_id, name, type, color, sort_order, created_at, updated_at)
SELECT u.id, c.name, c.type, c.color, c.sort_order,
       now() AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC'
FROM users u,
     (VALUES ('식비',      'EXPENSE', '#EF4444', 0),
             ('교통',      'EXPENSE', '#F59E0B', 1),
             ('주거/통신', 'EXPENSE', '#6366F1', 2),
             ('생활용품',  'EXPENSE', '#10B981', 3),
             ('문화/여가', 'EXPENSE', '#EC4899', 4),
             ('의료/건강', 'EXPENSE', '#14B8A6', 5),
             ('기타',      'EXPENSE', '#737373', 6),
             ('급여',      'INCOME',  '#4F46E5', 7),
             ('기타수입',  'INCOME',  '#737373', 8)
     ) AS c(name, type, color, sort_order)
WHERE u.email = 'dev@moneylog.local';

-- 3) 고정지출 감지용 — 같은 상호·같은 금액을 6개월 연속 (Phase 5 recurring 검증)
INSERT INTO transactions
    (user_id, category_id, type, amount, txn_date, merchant, memo, created_at, updated_at)
SELECT u.id, c.id, 'EXPENSE', f.amount,
       (date_trunc('month', CURRENT_DATE) - (m || ' month')::interval)::date + (f.day - 1),
       f.merchant, NULL,
       now() AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC'
FROM users u
CROSS JOIN (VALUES ('넷플릭스', 17000.00, 5,  '문화/여가'),
                   ('통신비',   45000.00, 12, '주거/통신'),
                   ('헬스장',   55000.00, 20, '의료/건강')
           ) AS f(merchant, amount, day, cat)
JOIN categories c ON c.user_id = u.id AND c.name = f.cat
CROSS JOIN generate_series(0, 5) AS m
WHERE u.email = 'dev@moneylog.local';

-- 4) 월급 — 매월 25일 (INCOME 기준선)
INSERT INTO transactions
    (user_id, category_id, type, amount, txn_date, merchant, memo, created_at, updated_at)
SELECT u.id, c.id, 'INCOME', 3200000.00,
       (date_trunc('month', CURRENT_DATE) - (m || ' month')::interval)::date + 24,
       '(주)머니로그', '월급',
       now() AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC'
FROM users u
JOIN categories c ON c.user_id = u.id AND c.name = '급여'
CROSS JOIN generate_series(0, 5) AS m
WHERE u.email = 'dev@moneylog.local';

-- 5) 일반 지출 약 380건 — 최근 6개월에 흩뿌린다.
--    setseed 로 매번 같은 데이터가 나오게 고정한다(재현 가능한 시드).
SELECT setseed(0.42);

INSERT INTO transactions
    (user_id, category_id, type, amount, txn_date, merchant, memo, created_at, updated_at)
SELECT u.id,
       c.id,
       'EXPENSE',
       round((random() * 48000 + 2000)::numeric, 2),
       (CURRENT_DATE - (random() * 179)::int),
       (ARRAY['스타벅스 강남점','GS25','이마트','배달의민족','올리브영',
              'CU 역삼점','쿠팡','카카오T','메가커피','다이소'])[1 + (random() * 9)::int],
       CASE WHEN random() < 0.25
            THEN (ARRAY['팀 미팅','주말 장보기','급한 출장','선물','생필품'])[1 + (random() * 4)::int]
            ELSE NULL END,
       now() AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC'
FROM generate_series(1, 380) AS i
CROSS JOIN users u
CROSS JOIN LATERAL (
    SELECT id FROM categories
    WHERE user_id = u.id AND type = 'EXPENSE'
    ORDER BY random() LIMIT 1
) AS c
WHERE u.email = 'dev@moneylog.local';

COMMIT;

-- 결과 확인
SELECT '총 거래 건수' AS 항목, count(*)::text AS 값 FROM transactions
  WHERE user_id = (SELECT id FROM users WHERE email = 'dev@moneylog.local')
UNION ALL
SELECT '기간', min(txn_date)::text || ' ~ ' || max(txn_date)::text FROM transactions
  WHERE user_id = (SELECT id FROM users WHERE email = 'dev@moneylog.local')
UNION ALL
SELECT '넷플릭스 건수', count(*)::text FROM transactions
  WHERE merchant = '넷플릭스'
    AND user_id = (SELECT id FROM users WHERE email = 'dev@moneylog.local');
