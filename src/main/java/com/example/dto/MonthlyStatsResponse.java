package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 월 대시보드 일괄 응답.
 *
 * 요약·카테고리별·일별·런레이트·이상치·예산은 같은 화면이 동시에 필요로 하고
 * 같은 원본(해당 월의 거래)에서 나온다. 다섯 개로 쪼개면 요청 5번에 같은 테이블을
 * 5번 스캔하고, 각각 따로 만료되어 화면 안에서 숫자가 서로 어긋나는 순간이 생긴다.
 */
public record MonthlyStatsResponse(
        String yearMonth,
        Summary summary,
        List<CategoryStat> byCategory,
        List<DailyStat> daily,
        Forecast forecast,
        List<Anomaly> anomalies,
        List<BudgetStat> budgets
) {

    public record Summary(BigDecimal income, BigDecimal expense, BigDecimal net) {}

    /** deleted 는 화면에서 이름 옆에 "(삭제됨)"을 붙이는 데 쓴다 */
    public record CategoryStat(Long categoryId, String name, String color,
                               boolean deleted, BigDecimal amount, BigDecimal ratio) {}

    /** 해당 월의 1일부터 말일까지 전부 채운다. 거래가 없는 날은 0 */
    public record DailyStat(LocalDate date, BigDecimal expense, BigDecimal income) {}

    /**
     * 직전 3개월에 거래가 한 건도 없으면 이 객체 자체가 null 이다.
     * 화면은 "예측하려면 데이터가 조금 더 필요해요"를 보여준다.
     */
    public record Forecast(BigDecimal confirmedExpense,
                           BigDecimal projectedExpense,
                           BigDecimal baselineDailyAvg,
                           int daysElapsed,
                           int daysInMonth,
                           int basisMonths) {}

    public record Anomaly(Long categoryId, String name,
                          BigDecimal currentPace, BigDecimal baseline, BigDecimal deltaRatio) {}

    /** usageRatio 는 budget 이 0 이면 0 이다. Infinity 가 JSON 에 실리면 프론트에서 NaN% 로 표시된다 */
    public record BudgetStat(Long categoryId, String name, BigDecimal budget,
                             BigDecimal spent, BigDecimal usageRatio, boolean exceeded) {}
}
