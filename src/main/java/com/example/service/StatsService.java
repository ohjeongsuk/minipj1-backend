package com.example.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.Budget;
import com.example.domain.BudgetRepository;
import com.example.domain.StatsProjections.CategoryPeriodSum;
import com.example.domain.StatsProjections.CategorySum;
import com.example.domain.StatsProjections.DailySum;
import com.example.domain.StatsProjections.MonthSum;
import com.example.domain.StatsProjections.TypeSum;
import com.example.domain.StatsRepository;
import com.example.domain.Transaction;
import com.example.domain.TransactionType;
import com.example.dto.MonthlyStatsResponse;
import com.example.dto.MonthlyStatsResponse.Anomaly;
import com.example.dto.MonthlyStatsResponse.BudgetStat;
import com.example.dto.MonthlyStatsResponse.CategoryStat;
import com.example.dto.MonthlyStatsResponse.DailyStat;
import com.example.dto.MonthlyStatsResponse.Forecast;
import com.example.dto.MonthlyStatsResponse.Summary;
import com.example.dto.RecurringResponse;

/**
 * 월 대시보드 집계와 고정지출 감지.
 *
 * ⚠️ 이 클래스에 LocalDate.now() / YearMonth.now() 가 없다. 의도적이다.
 *    서버는 UTC 로 돌고 사용자는 KST(+09:00)다. 서버에서 "오늘"을 판정하면
 *    매월 1일 0~9시 사이에 사용자가 지난달 대시보드를 보게 되고,
 *    매일 0~9시 사이에는 "오늘 지출"이 어제 것으로 집계된다.
 *    txn_date 가 LocalDate 라 데이터는 멀쩡한데 조회 범위만 어긋나 원인을 찾기 어렵다.
 *    기준 날짜는 클라이언트가 yearMonth·asOf 로 보낸다.
 */
@Service
public class StatsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final StatsRepository statsRepository;
    private final BudgetRepository budgetRepository;

    public StatsService(StatsRepository statsRepository, BudgetRepository budgetRepository) {
        this.statsRepository = statsRepository;
        this.budgetRepository = budgetRepository;
    }

    @Transactional(readOnly = true)
    public MonthlyStatsResponse monthly(Long userId, YearMonth target, LocalDate asOf) {
        LocalDate monthStart = target.atDay(1);
        LocalDate monthEnd = target.atEndOfMonth();
        int daysInMonth = target.lengthOfMonth();
        int daysElapsed = ForecastCalculator.daysElapsed(target, asOf);

        Summary summary = buildSummary(userId, monthStart, monthEnd);
        // 대시보드의 byCategory 는 지금까지도 지출만이었다. 동작은 바뀌지 않는다.
        List<CategorySum> categorySums =
                statsRepository.sumByCategoryAndType(userId, monthStart, monthEnd, TransactionType.EXPENSE);

        return new MonthlyStatsResponse(
                target.toString(),
                summary,
                buildByCategory(categorySums, summary.expense()),
                buildDaily(userId, target),
                buildForecast(userId, target, summary.expense(), daysInMonth, daysElapsed),
                buildAnomalies(userId, target, categorySums, daysInMonth, daysElapsed),
                buildBudgets(userId, target, categorySums));
    }

    /**
     * 고정지출 감지. asOf 기준 직전 3개월(당월 제외)을 스캔한다.
     * 최근 3개월 전체를 읽으므로 비용이 커서 대시보드와 분리된 엔드포인트다.
     */
    @Transactional(readOnly = true)
    public List<RecurringResponse> recurring(Long userId, LocalDate asOf) {
        YearMonth current = YearMonth.from(asOf);

        /*
         * 필수 개월은 직전 3개월이고, 당월은 집계에만 더한다.
         *
         * ⚠️ 당월을 필수로 만들면 매달 1일부터 결제일 사이에는 아직 결제가 없어
         *    목록이 통째로 비었다가 결제가 들어오면 다시 나타난다. 고장으로 보인다.
         *    필수를 직전 3개월로 두면 목록이 달 내내 안정적이고, 당월 결제는
         *    들어오는 대로 monthsSeen(3 또는 4)과 lastDate 에 반영된다.
         */
        Set<YearMonth> requiredMonths = new java.util.LinkedHashSet<>();
        for (int i = ForecastCalculator.BASIS_MONTHS; i >= 1; i--) {
            requiredMonths.add(current.minusMonths(i));
        }
        Set<YearMonth> scanMonths = new java.util.LinkedHashSet<>(requiredMonths);
        scanMonths.add(current);

        LocalDate from = current.minusMonths(ForecastCalculator.BASIS_MONTHS).atDay(1);
        // 당월의 상한은 달 끝이 아니라 asOf 다. 아직 오지 않은 날짜로 입력된 거래를 세지 않는다
        LocalDate to = asOf;

        List<RecurringDetector.Candidate> candidates =
                statsRepository.findRecurringCandidates(userId, from, to).stream()
                        .map(this::toCandidate)
                        .toList();

        return RecurringDetector.detect(candidates, scanMonths, requiredMonths).stream()
                .map(RecurringResponse::from)
                .toList();
    }

    // ---------- 조각별 조립 ----------

    private Summary buildSummary(Long userId, LocalDate from, LocalDate to) {
        Map<TransactionType, BigDecimal> sums = statsRepository.sumByType(userId, from, to).stream()
                .collect(Collectors.toMap(TypeSum::type, TypeSum::amount));

        // group by 쿼리는 그룹이 없으면 행 자체가 안 나온다. 여기서 0 으로 채운다
        BigDecimal income = sums.getOrDefault(TransactionType.INCOME, ZERO);
        BigDecimal expense = sums.getOrDefault(TransactionType.EXPENSE, ZERO);
        return new Summary(income, expense, income.subtract(expense));
    }

    private List<CategoryStat> buildByCategory(List<CategorySum> sums, BigDecimal totalExpense) {
        return sums.stream()
                .map(s -> new CategoryStat(s.categoryId(), s.name(), s.color(), s.deleted(),
                        s.amount(), ForecastCalculator.ratio(s.amount(), totalExpense)))
                .toList();
    }

    /** 1일부터 말일까지 전부 채운다. 프론트가 빈 날을 메우면 계산이 두 곳으로 갈라진다 */
    private List<DailyStat> buildDaily(Long userId, YearMonth target) {
        Map<LocalDate, BigDecimal> expenses = new HashMap<>();
        Map<LocalDate, BigDecimal> incomes = new HashMap<>();

        for (DailySum row : statsRepository.sumDaily(userId, target.atDay(1), target.atEndOfMonth())) {
            (row.type() == TransactionType.EXPENSE ? expenses : incomes)
                    .merge(row.date(), row.amount(), BigDecimal::add);
        }

        List<DailyStat> daily = new ArrayList<>(target.lengthOfMonth());
        for (int day = 1; day <= target.lengthOfMonth(); day++) {
            LocalDate date = target.atDay(day);
            daily.add(new DailyStat(date,
                    expenses.getOrDefault(date, ZERO),
                    incomes.getOrDefault(date, ZERO)));
        }
        return daily;
    }

    private Forecast buildForecast(Long userId, YearMonth target, BigDecimal confirmedExpense,
                                   int daysInMonth, int daysElapsed) {
        Map<YearMonth, BigDecimal> monthlyExpenses = baselineMonthlyExpenses(userId, target);

        // 직전 3개월에 거래가 한 건도 없으면 예측하지 않는다
        if (!ForecastCalculator.canForecast(monthlyExpenses)) {
            return null;
        }

        BigDecimal baselineExpense = monthlyExpenses.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dailyAvg = ForecastCalculator.baselineDailyAvg(
                baselineExpense, ForecastCalculator.baselineTotalDays(target));

        return new Forecast(
                confirmedExpense,
                ForecastCalculator.projectedExpense(confirmedExpense, dailyAvg, daysInMonth, daysElapsed),
                dailyAvg,
                daysElapsed,
                daysInMonth,
                ForecastCalculator.basisMonths(new ArrayList<>(monthlyExpenses.values())));
    }

    private List<Anomaly> buildAnomalies(Long userId, YearMonth target, List<CategorySum> currentSums,
                                         int daysInMonth, int daysElapsed) {
        // 월초에는 노이즈가 크다. 1일에 외식 한 번 하면 식비가 3000% 증가로 나온다
        if (!ForecastCalculator.anomalyPeriodReached(daysElapsed)) {
            return List.of();
        }

        int totalDays = ForecastCalculator.baselineTotalDays(target);
        Map<Long, BigDecimal> baselineByCategory = statsRepository.sumExpenseByCategoryInPeriod(
                        userId,
                        ForecastCalculator.baselineStart(target),
                        ForecastCalculator.baselineEnd(target))
                .stream()
                .collect(Collectors.toMap(CategoryPeriodSum::categoryId, CategoryPeriodSum::amount));

        List<Anomaly> anomalies = new ArrayList<>();
        for (CategorySum current : currentSums) {
            BigDecimal baselineExpense = baselineByCategory.get(current.categoryId());
            if (baselineExpense == null) {
                continue;
            }
            BigDecimal baseline = ForecastCalculator.categoryBaseline(baselineExpense, totalDays, daysInMonth);
            BigDecimal pace = ForecastCalculator.currentPace(current.amount(), daysElapsed, daysInMonth);
            BigDecimal delta = ForecastCalculator.deltaRatio(pace, baseline);

            if (ForecastCalculator.isAnomaly(delta)) {
                anomalies.add(new Anomaly(current.categoryId(), current.name(), pace, baseline, delta));
            }
        }
        return anomalies;
    }

    private List<BudgetStat> buildBudgets(Long userId, YearMonth target, List<CategorySum> currentSums) {
        Map<Long, BigDecimal> spentByCategory = currentSums.stream()
                .collect(Collectors.toMap(CategorySum::categoryId, CategorySum::amount));

        return budgetRepository.findByUserIdAndYearMonth(userId, target.toString()).stream()
                .map(budget -> toBudgetStat(budget, spentByCategory))
                .toList();
    }

    private BudgetStat toBudgetStat(Budget budget, Map<Long, BigDecimal> spentByCategory) {
        BigDecimal amount = budget.getAmount();
        BigDecimal spent = spentByCategory.getOrDefault(budget.getCategory().getId(), ZERO);
        // budget 이 0 이면 나눗셈을 하지 않는다. Infinity 가 JSON 에 실리면 프론트에서 NaN% 가 된다
        BigDecimal usageRatio = ForecastCalculator.ratio(spent, amount);
        boolean exceeded = amount.compareTo(BigDecimal.ZERO) > 0 && spent.compareTo(amount) > 0;

        return new BudgetStat(budget.getCategory().getId(), budget.getCategory().getName(),
                amount, spent, usageRatio, exceeded);
    }

    /** 직전 3개월의 월별 지출. 거래가 없는 달은 0 으로 채운다 */
    private Map<YearMonth, BigDecimal> baselineMonthlyExpenses(Long userId, YearMonth target) {
        Map<String, BigDecimal> rows = statsRepository.sumExpenseByMonth(
                        userId,
                        ForecastCalculator.baselineStart(target),
                        ForecastCalculator.baselineEnd(target))
                .stream()
                .collect(Collectors.toMap(MonthSum::yearMonth, MonthSum::amount));

        Map<YearMonth, BigDecimal> result = new LinkedHashMap<>();
        for (int i = ForecastCalculator.BASIS_MONTHS; i >= 1; i--) {
            YearMonth month = target.minusMonths(i);
            result.put(month, rows.getOrDefault(month.toString(), ZERO));
        }
        return result;
    }

    private RecurringDetector.Candidate toCandidate(Transaction transaction) {
        return new RecurringDetector.Candidate(
                transaction.getMerchant(),
                transaction.getCategory().getId(),
                transaction.getAmount(),
                transaction.getTxnDate());
    }
}
