package com.example.domain;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.example.domain.StatsProjections.CategoryPeriodSum;
import com.example.domain.StatsProjections.CategorySum;
import com.example.domain.StatsProjections.DailySum;
import com.example.domain.StatsProjections.MonthSum;
import com.example.domain.StatsProjections.TypeSum;

/**
 * 집계 전용 리포지토리. CRUD 와 섞지 않고 분리했다.
 *
 * ⚠️ 모든 합계에 coalesce 를 건다. SUM() 은 대상 행이 없으면 0 이 아니라 NULL 이다.
 *    신규 가입 직후 대시보드가 그 경로를 그대로 탄다.
 *    다만 group by 가 있는 쿼리는 그룹 자체가 없으면 행이 안 나오므로
 *    빈 결과를 Java 쪽에서 0 으로 채운다.
 *
 * ⚠️ 카테고리 조인에 deleted_at IS NULL 을 걸지 않는다.
 *    걸면 과거 달의 합계가 바뀐다. 삭제는 "앞으로 안 쓴다"이지 "없던 일"이 아니다.
 *
 * ⚠️ 집계는 메서드 이름 규칙으로 표현하지 않고 @Query 로 명시한다.
 *    findByUserIdAndTxnDateBetweenAnd... 로 쓰면 이름이 감당 못 할 길이가 된다.
 */
public interface StatsRepository extends Repository<Transaction, Long> {

    /** 월 요약 — 수입·지출 각각의 합계 */
    @Query("""
            select new com.example.domain.StatsProjections$TypeSum(
                       t.type, coalesce(sum(t.amount), 0))
            from Transaction t
            where t.user.id = :userId
              and t.deletedAt is null
              and t.txnDate between :from and :to
            group by t.type
            """)
    List<TypeSum> sumByType(@Param("userId") Long userId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);

    /** 카테고리별 지출 — 삭제된 카테고리도 포함한다 */
    @Query("""
            select new com.example.domain.StatsProjections$CategorySum(
                       c.id, c.name, c.color,
                       case when c.deletedAt is null then false else true end,
                       coalesce(sum(t.amount), 0))
            from Transaction t
            join t.category c
            where t.user.id = :userId
              and t.deletedAt is null
              and t.type = com.example.domain.TransactionType.EXPENSE
              and t.txnDate between :from and :to
            group by c.id, c.name, c.color, c.deletedAt
            order by sum(t.amount) desc
            """)
    List<CategorySum> sumExpenseByCategory(@Param("userId") Long userId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    /** 일별 수입·지출 */
    @Query("""
            select new com.example.domain.StatsProjections$DailySum(
                       t.txnDate, t.type, coalesce(sum(t.amount), 0))
            from Transaction t
            where t.user.id = :userId
              and t.deletedAt is null
              and t.txnDate between :from and :to
            group by t.txnDate, t.type
            order by t.txnDate
            """)
    List<DailySum> sumDaily(@Param("userId") Long userId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);

    /**
     * 월별 지출 합계 (기준선 산정용).
     * yyyy-MM 문자열로 묶어 Java 에서 YearMonth 로 되돌린다.
     */
    @Query("""
            select new com.example.domain.StatsProjections$MonthSum(
                       cast(function('to_char', t.txnDate, 'YYYY-MM') as string),
                       coalesce(sum(t.amount), 0))
            from Transaction t
            where t.user.id = :userId
              and t.deletedAt is null
              and t.type = com.example.domain.TransactionType.EXPENSE
              and t.txnDate between :from and :to
            group by function('to_char', t.txnDate, 'YYYY-MM')
            """)
    List<MonthSum> sumExpenseByMonth(@Param("userId") Long userId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /** 카테고리별 기간 지출 (이상치 기준선용) */
    @Query("""
            select new com.example.domain.StatsProjections$CategoryPeriodSum(
                       c.id, coalesce(sum(t.amount), 0))
            from Transaction t
            join t.category c
            where t.user.id = :userId
              and t.deletedAt is null
              and t.type = com.example.domain.TransactionType.EXPENSE
              and t.txnDate between :from and :to
            group by c.id
            """)
    List<CategoryPeriodSum> sumExpenseByCategoryInPeriod(@Param("userId") Long userId,
                                                         @Param("from") LocalDate from,
                                                         @Param("to") LocalDate to);

    /**
     * 고정지출 감지 후보.
     * merchant 가 비어 있는 거래는 대상에서 제외한다.
     * 엔티티가 아니라 필요한 4개 필드만 읽는다.
     */
    @Query("""
            select t from Transaction t
            join fetch t.category
            where t.user.id = :userId
              and t.deletedAt is null
              and t.type = com.example.domain.TransactionType.EXPENSE
              and t.merchant is not null
              and trim(t.merchant) <> ''
              and t.txnDate between :from and :to
            """)
    List<Transaction> findRecurringCandidates(@Param("userId") Long userId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}
