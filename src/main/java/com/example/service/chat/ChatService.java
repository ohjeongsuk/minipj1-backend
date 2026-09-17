package com.example.service.chat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.StatsProjections.CategorySum;
import com.example.domain.StatsRepository;
import com.example.domain.TransactionType;
import com.example.dto.CategoryResponse;
import com.example.dto.ChatResponse;
import com.example.dto.MonthlyStatsResponse;
import com.example.dto.MonthlyStatsResponse.BudgetStat;
import com.example.dto.MonthlyStatsResponse.CategoryStat;
import com.example.dto.MonthlyStatsResponse.DailyStat;
import com.example.dto.MonthlyStatsResponse.Forecast;
import com.example.dto.RecurringResponse;
import com.example.dto.TransactionResponse;
import com.example.service.CategoryService;
import com.example.service.StatsService;
import com.example.service.TransactionService;

/**
 * 챗봇 응답 조립.
 *
 * ⚠️ 데이터를 새로 계산하지 않는다. 기존 StatsService·TransactionService 를 그대로 부른다.
 *    소진율이나 비율을 여기서 다시 구현하면 대시보드와 챗봇이 같은 달에 다른 숫자를 말한다.
 *
 * ⚠️ now() 계열을 쓰지 않는다. 기준 날짜는 asOf 로 들어온다.
 *
 * ⚠️ 조회 전용이다. 이 클래스에 쓰기 경로를 만들지 않는다.
 *    자연어 오해로 데이터가 바뀌면 되돌릴 방법이 없다.
 */
@Service
public class ChatService {

    /** 고정지출 목록을 문장에 몇 개까지 나열할지. 넘으면 "외 N건" 으로 줄인다 */
    private static final int RECURRING_SHOWN = 5;

    private static final List<String> SUGGESTIONS = List.of(
            "이번달 얼마 썼어?",
            "지난달 식비 얼마 썼어?",
            "최근 지출 보여줘",
            "고정지출 뭐 있어?");

    private final StatsService statsService;
    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final StatsRepository statsRepository;

    public ChatService(StatsService statsService,
                       TransactionService transactionService,
                       CategoryService categoryService,
                       StatsRepository statsRepository) {
        this.statsService = statsService;
        this.transactionService = transactionService;
        this.categoryService = categoryService;
        this.statsRepository = statsRepository;
    }

    @Transactional(readOnly = true)
    public ChatResponse ask(Long userId, String message, LocalDate asOf) {
        List<CategoryResponse> categories = categoryService.findAll(userId, null);
        Intent intent = IntentParser.parse(message, asOf, categories);

        return switch (intent.type()) {
            case MONTHLY_SUMMARY -> monthlySummary(userId, intent, asOf);
            case CATEGORY_AMOUNT -> categoryAmount(userId, intent, asOf);
            case BUDGET_STATUS -> budgetStatus(userId, intent, asOf);
            case RECENT_TRANSACTIONS -> recentTransactions(userId, intent);
            case FORECAST -> forecast(userId, intent, asOf);
            case RECURRING -> recurring(userId, asOf);
            case DAILY_AMOUNT -> dailyAmount(userId, intent, asOf);
            case UNKNOWN -> unknown();
        };
    }

    // ---------- 의도별 응답 ----------

    private ChatResponse monthlySummary(Long userId, Intent intent, LocalDate asOf) {
        MonthlyStatsResponse stats = statsService.monthly(userId, intent.yearMonth(), asOf);
        MonthlyStatsResponse.Summary summary = stats.summary();
        int month = intent.yearMonth().getMonthValue();

        if (isZero(summary.income()) && isZero(summary.expense())) {
            return answer(intent, month + "월에는 아직 기록이 없어요.");
        }
        return answer(intent, "%d월 지출은 %s, 수입은 %s이에요. 남은 돈은 %s입니다."
                .formatted(month, won(summary.expense()), won(summary.income()), won(summary.net())));
    }

    private ChatResponse categoryAmount(Long userId, Intent intent, LocalDate asOf) {
        MonthlyStatsResponse stats = statsService.monthly(userId, intent.yearMonth(), asOf);
        int month = intent.yearMonth().getMonthValue();
        String name = intent.categoryName();
        boolean income = intent.categoryType() == TransactionType.INCOME;

        BigDecimal amount = income
                ? incomeAmount(userId, intent)
                : expenseAmount(stats.byCategory(), name);
        BigDecimal total = income ? stats.summary().income() : stats.summary().expense();
        String kind = income ? "수입" : "지출";

        if (isZero(amount)) {
            return answer(intent, "%d월 %s %s은 없어요.".formatted(month, name, kind));
        }
        // 분모가 0 이면 비율 문장을 통째로 생략한다. NaN·Infinity 가 나갈 경로를 만들지 않는다.
        if (isZero(total)) {
            return answer(intent, "%d월 %s는 %s이에요.".formatted(month, name, won(amount)));
        }
        return answer(intent, "%d월 %s는 %s이에요. 전체 %s의 %d%%입니다."
                .formatted(month, name, won(amount), kind, percent(amount, total)));
    }

    private ChatResponse budgetStatus(Long userId, Intent intent, LocalDate asOf) {
        MonthlyStatsResponse stats = statsService.monthly(userId, intent.yearMonth(), asOf);
        int month = intent.yearMonth().getMonthValue();
        List<BudgetStat> budgets = stats.budgets().stream()
                .filter(b -> b.budget() != null && b.budget().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (intent.categoryName() != null) {
            BudgetStat found = budgets.stream()
                    .filter(b -> b.name().equalsIgnoreCase(intent.categoryName()))
                    .findFirst()
                    .orElse(null);
            if (found == null) {
                return answer(intent, "%s에 %d월 예산이 설정되어 있지 않아요."
                        .formatted(intent.categoryName(), month));
            }
            BigDecimal left = found.budget().subtract(found.spent());
            // 음수를 "-188,000원 남았습니다" 로 쓰면 읽는 사람이 한 번 더 계산해야 한다
            String tail = left.compareTo(BigDecimal.ZERO) < 0
                    ? "%s 초과했어요.".formatted(won(left.abs()))
                    : "%s 남았습니다.".formatted(won(left));
            return answer(intent, "%d월 %s 예산 %s 중 %s을 썼어요. %s"
                    .formatted(month, found.name(), won(found.budget()), won(found.spent()), tail));
        }

        if (budgets.isEmpty()) {
            return answer(intent, "%d월에 설정된 예산이 없어요.".formatted(month));
        }
        BigDecimal total = budgets.stream().map(BudgetStat::budget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spent = budgets.stream().map(BudgetStat::spent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return answer(intent, "%d월 예산은 총 %s 중 %s을 썼어요."
                .formatted(month, won(total), won(spent)));
    }

    private ChatResponse recentTransactions(Long userId, Intent intent) {
        List<TransactionResponse> rows = transactionService
                .search(userId, null, null, intent.txnType(), null, null, 0, intent.limit(), null)
                .content();

        if (rows.isEmpty()) {
            return new ChatResponse(intent.type(), "아직 기록이 없어요.",
                    yearMonthOf(intent), List.of(), List.of());
        }
        return new ChatResponse(intent.type(), "최근 %d건이에요.".formatted(rows.size()),
                yearMonthOf(intent), rows, List.of());
    }

    /**
     * 이번 달 예상 지출(런레이트).
     *
     * 수식은 백엔드의 ForecastCalculator 한 곳에만 있다. 여기서 다시 계산하면
     * 대시보드의 "이 속도면" 카드와 챗봇이 다른 숫자를 말한다.
     */
    private ChatResponse forecast(Long userId, Intent intent, LocalDate asOf) {
        MonthlyStatsResponse stats = statsService.monthly(userId, intent.yearMonth(), asOf);
        Forecast f = stats.forecast();
        int month = intent.yearMonth().getMonthValue();

        // 직전 3개월에 거래가 없으면 서버가 forecast 를 null 로 준다
        if (f == null) {
            return answer(intent, "예측하려면 데이터가 조금 더 필요해요. 최근 3개월 기록이 있어야 해요.");
        }
        // 이미 끝난 달은 "예상" 이 의미가 없다. 서버가 남은 일수를 0 으로 잡아
        // 예상액이 확정액과 같아지는데, 그대로 "쓰게 돼요" 라고 하면 미래처럼 읽힌다
        if (f.daysElapsed() >= f.daysInMonth()) {
            return answer(intent, "%d월은 이미 지난 달이라 예측하지 않아요. 지출은 %s이었어요."
                    .formatted(month, won(f.confirmedExpense())));
        }
        return answer(intent, "이 속도면 %d월에 %s을 쓰게 돼요. 지금까지 %s 썼고, 최근 %d개월 기준 하루 평균 %s이에요."
                .formatted(month, won(f.projectedExpense()), won(f.confirmedExpense()),
                        f.basisMonths(), won(f.baselineDailyAvg())));
    }

    /**
     * 고정지출 감지.
     *
     * ⚠️ 대상 월이 없다. asOf 기준 직전 3개월 + 당월을 스캔하는 별도 계산이라
     *    응답의 yearMonth 를 null 로 둔다. 특정 달의 값처럼 보이면 안 된다.
     */
    private ChatResponse recurring(Long userId, LocalDate asOf) {
        List<RecurringResponse> found = statsService.recurring(userId, asOf);

        if (found.isEmpty()) {
            return new ChatResponse(IntentType.RECURRING,
                    "고정지출로 볼 만한 게 아직 없어요. 같은 곳에서 비슷한 금액이 석 달 이상 나가야 찾아낼 수 있어요.",
                    null, null, List.of());
        }
        String list = found.stream()
                .limit(RECURRING_SHOWN)
                .map(r -> "%s %s".formatted(r.merchant(), won(r.medianAmount())))
                .collect(java.util.stream.Collectors.joining(", "));
        String more = found.size() > RECURRING_SHOWN
                ? " 외 %d건".formatted(found.size() - RECURRING_SHOWN)
                : "";
        return new ChatResponse(IntentType.RECURRING,
                "고정지출로 보이는 게 %d건 있어요. %s%s".formatted(found.size(), list, more),
                null, null, List.of());
    }

    /**
     * 특정 하루의 금액.
     *
     * ⚠️ daily 배열에는 카테고리 구분이 없다. 날짜와 카테고리를 함께 물으면
     *    그날 전체를 답하되 그 사실을 문장에 밝힌다. 카테고리 월 합계를
     *    그날 것처럼 보여주는 것이 가장 나쁜 실패다.
     */
    private ChatResponse dailyAmount(Long userId, Intent intent, LocalDate asOf) {
        MonthlyStatsResponse stats = statsService.monthly(userId, intent.yearMonth(), asOf);
        LocalDate target = intent.date();
        DailyStat day = stats.daily().stream()
                .filter(d -> d.date().equals(target))
                .findFirst()
                .orElse(null);

        String head = intent.categoryName() == null
                ? ""
                : "날짜별 금액은 카테고리를 나눠서 보여드릴 수 없어요. ";
        String when = "%d월 %d일".formatted(target.getMonthValue(), target.getDayOfMonth());

        if (day == null || (isZero(day.expense()) && isZero(day.income()))) {
            return answer(intent, head + "%s에는 기록이 없어요.".formatted(when));
        }
        if (isZero(day.income())) {
            return answer(intent, head + "%s 지출은 %s이에요.".formatted(when, won(day.expense())));
        }
        return answer(intent, head + "%s 지출은 %s, 수입은 %s이에요."
                .formatted(when, won(day.expense()), won(day.income())));
    }

    private ChatResponse unknown() {
        return new ChatResponse(IntentType.UNKNOWN,
                "무슨 말씀인지 잘 모르겠어요. 이렇게 물어보실 수 있어요.",
                null, null, SUGGESTIONS);
    }

    // ---------- 보조 ----------

    private BigDecimal expenseAmount(List<CategoryStat> byCategory, String name) {
        return byCategory.stream()
                .filter(c -> c.name().equalsIgnoreCase(name))
                .map(CategoryStat::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 수입은 대시보드의 byCategory 에 없다. 그 집계가 지출만 담기 때문이다.
     * 거기서 급여를 찾으면 "없는 값" 이 아니라 0원이라는 틀린 값이 나오므로,
     * 같은 쿼리를 INCOME 으로 한 번 더 부른다.
     */
    private BigDecimal incomeAmount(Long userId, Intent intent) {
        YearMonth target = intent.yearMonth();
        List<CategorySum> sums = statsRepository.sumByCategoryAndType(
                userId, target.atDay(1), target.atEndOfMonth(), TransactionType.INCOME);
        return sums.stream()
                .filter(s -> s.name().equalsIgnoreCase(intent.categoryName()))
                .map(CategorySum::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private static ChatResponse answer(Intent intent, String text) {
        return new ChatResponse(intent.type(), text, yearMonthOf(intent), null, List.of());
    }

    private static String yearMonthOf(Intent intent) {
        return intent.yearMonth() == null ? null : intent.yearMonth().toString();
    }

    private static boolean isZero(BigDecimal value) {
        // equals 는 scale 까지 비교한다. DB 의 0.00 과 코드의 0 이 다르다고 나온다.
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }

    /** 금액 포맷은 이 한 곳에만 둔다. DecimalFormat 은 스레드 안전하지 않아 매번 만든다. */
    private static String won(BigDecimal amount) {
        return new DecimalFormat("#,##0").format(amount) + "원";
    }

    private static int percent(BigDecimal part, BigDecimal total) {
        return part.divide(total, 4, RoundingMode.HALF_UP)   // scale 을 반드시 넘긴다
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }
}
