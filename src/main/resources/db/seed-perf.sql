-- 성능 DoD 측정 전용 시드. seed-dev.sql 을 먼저 실행해 계정·카테고리를 만든 뒤 쓴다.
--
--   & "C:\Program Files\PostgreSQL\17\bin\psql" -U postgres -d miniproject1_db -f src/main/resources/db/seed-perf.sql
--
-- ⚠️ 400건으로는 성능 지표가 무의미하다. 인덱스가 없어도 400행은 1ms 미만이라 항상 통과한다.
--    집계·검색 쿼리가 인덱스를 타는지는 데이터가 충분해야 드러난다.
--
-- INSERT 문을 2만 줄 쓰지 않고 generate_series 로 만든다.
-- 파일이 작고, 건수 조정이 숫자 하나다. setseed 로 재현 가능하게 고정한다.

BEGIN;

-- 기존 성능 시드만 지운다. memo 로 표시해 seed-dev 데이터와 구분한다.
DELETE FROM transactions
WHERE memo = '[perf]'
  AND user_id = (SELECT id FROM users WHERE email = 'dev@moneylog.local');

SELECT setseed(0.7);

INSERT INTO transactions
    (user_id, category_id, type, amount, txn_date, merchant, memo, created_at, updated_at)
SELECT u.id,
       c.id,
       c.type,
       round((random() * 98000 + 1000)::numeric, 2),
       -- 최근 24개월에 균등하게 흩뿌린다
       (CURRENT_DATE - (random() * 729)::int),
       -- 검색 성능을 재려면 상호가 다양해야 한다. 500종을 돌려 쓴다.
       (ARRAY['스타벅스','GS25','이마트','배달의민족','올리브영',
              'CU','쿠팡','카카오T','메가커피','다이소',
              'Starbucks','Coupang','KakaoT','Emart','Daiso'])[1 + (random() * 14)::int]
           || ' ' || (1 + (random() * 499)::int)::text || '호점',
       '[perf]',
       now() AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC'
FROM generate_series(1, 20000) AS i
CROSS JOIN users u
CROSS JOIN LATERAL (
    SELECT id, type FROM categories
    WHERE user_id = u.id AND deleted_at IS NULL
    ORDER BY random() LIMIT 1
) AS c
WHERE u.email = 'dev@moneylog.local';

COMMIT;

-- 통계를 갱신해야 플래너가 인덱스를 제대로 고른다.
-- 빠뜨리면 방금 넣은 2만 행에 대해 옛 통계로 실행 계획을 세워 측정값이 왜곡된다.
ANALYZE transactions;

SELECT '총 거래 건수' AS 항목, count(*)::text AS 값 FROM transactions
  WHERE user_id = (SELECT id FROM users WHERE email = 'dev@moneylog.local')
UNION ALL
SELECT '성능 시드 건수', count(*)::text FROM transactions
  WHERE memo = '[perf]'
UNION ALL
SELECT '기간', min(txn_date)::text || ' ~ ' || max(txn_date)::text FROM transactions
  WHERE user_id = (SELECT id FROM users WHERE email = 'dev@moneylog.local');
