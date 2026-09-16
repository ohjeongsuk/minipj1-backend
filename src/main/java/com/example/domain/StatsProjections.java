package com.example.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 집계 쿼리의 결과 형태. JPQL 의 select new 로 바로 매핑한다.
 *
 * 엔티티를 통째로 읽어와 Java 에서 합산하지 않는다.
 * 2만 건을 메모리에 올리면 대시보드 하나가 수백 MB 를 쓴다.
 */
public final class StatsProjections {

    private StatsProjections() {
    }

    /** 구분별 합계 */
    public record TypeSum(TransactionType type, BigDecimal amount) {}

    /** 카테고리별 합계. 삭제된 카테고리도 포함해야 과거 달의 합계가 바뀌지 않는다 */
    public record CategorySum(Long categoryId, String name, String color,
                              boolean deleted, BigDecimal amount) {}

    /** 일별 합계 */
    public record DailySum(LocalDate date, TransactionType type, BigDecimal amount) {}

    /** 월별 지출 합계 (기준선 산정용) */
    public record MonthSum(String yearMonth, BigDecimal amount) {}

    /** 카테고리별 기간 지출 (이상치 기준선용) */
    public record CategoryPeriodSum(Long categoryId, BigDecimal amount) {}
}
