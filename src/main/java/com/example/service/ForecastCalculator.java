package com.example.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * 예측·이상치 계산. CLAUDE.md 5장 「예측 계산 규칙」이 정본이다.
 *
 * ⚠️ 이 클래스에 now() 계열 호출이 없다. 의도적이다.
 *    서버는 UTC 로 돌고 사용자는 KST 다. 서버에서 LocalDate.now() 를 부르면
 *    매월 1일 0~9시 사이에 사용자가 지난달 대시보드를 보게 된다.
 *    "오늘"과 "이번 달"은 클라이언트가 asOf·yearMonth 로 보낸다.
 *
 * ⚠️ 모든 나눗셈에 scale 과 RoundingMode 를 함께 넘긴다.
 *    BigDecimal.divide 는 무한소수가 나오면 ArithmeticException 을 던진다.
 *    이 앱의 예측은 전부 나눗셈이라 3 으로 나누는 순간 터진다.
 */
public final class ForecastCalculator {

    /** 기준선 산정에 쓰는 개월 수 */
    public static final int BASIS_MONTHS = 3;

    /** 이상치 임계값. 낮추면 매달 모든 카테고리가 "이상"이 되어 알림이 무의미해진다 */
    private static final BigDecimal ANOMALY_THRESHOLD = new BigDecimal("0.30");

    /** 월초 노이즈 차단. 1일에 외식 한 번 하면 식비가 3000% 증가로 나온다 */
    private static final int MIN_DAYS_FOR_ANOMALY = 7;

    private static final int MONEY_SCALE = 2;
    private static final int RATIO_SCALE = 4;

    private ForecastCalculator() {
    }

    // ---------- 기간 ----------

    /** 기준선 산정 구간의 시작일 (대상 월의 BASIS_MONTHS 개월 전 1일) */
    public static LocalDate baselineStart(YearMonth target) {
        return target.minusMonths(BASIS_MONTHS).atDay(1);
    }

    /** 기준선 산정 구간의 종료일 (대상 월 직전 달의 말일). 당월은 포함하지 않는다 */
    public static LocalDate baselineEnd(YearMonth target) {
        return target.minusMonths(1).atEndOfMonth();
    }

    /** 기준선 구간의 실제 총 일수. 개월 수가 아니라 일수로 나누는 것이 규칙이다 */
    public static int baselineTotalDays(YearMonth target) {
        int days = 0;
        for (int i = 1; i <= BASIS_MONTHS; i++) {
            days += target.minusMonths(i).lengthOfMonth();
        }
        return days;
    }

    /**
     * 경과일수. asOf 가 대상 월보다 앞이면 0, 뒤면 그 달의 총 일수로 간주한다.
     * 과거 달을 조회하면 예측하지 않는다는 규칙이 여기서 나온다
     * (daysElapsed == daysInMonth 이면 남은 일수가 0 이라 예측 = 확정).
     */
    public static int daysElapsed(YearMonth target, LocalDate asOf) {
        YearMonth asOfMonth = YearMonth.from(asOf);
        if (asOfMonth.isAfter(target)) {
            return target.lengthOfMonth();
        }
        if (asOfMonth.isBefore(target)) {
            return 0;
        }
        return asOf.getDayOfMonth();
    }

    // ---------- 기준선 ----------

    /**
     * 일평균 지출. 직전 3개월 합계를 그 3개월의 실제 총 일수로 나눈다.
     * 개월 수로 나누면 28일인 2월과 31일인 1월이 같은 가중치를 받는다.
     */
    public static BigDecimal baselineDailyAvg(BigDecimal baselineExpense, int totalDays) {
        if (totalDays <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        return baselineExpense.divide(BigDecimal.valueOf(totalDays), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    // ---------- 런레이트 ----------

    /**
     * 이번 달 예상 지출 = 확정 + 일평균 × 남은 일수.
     * asOf 가 대상 월 이후면 남은 일수가 0 이라 확정값과 같아진다.
     */
    public static BigDecimal projectedExpense(BigDecimal confirmedExpense,
                                              BigDecimal baselineDailyAvg,
                                              int daysInMonth,
                                              int daysElapsed) {
        int remaining = Math.max(0, daysInMonth - daysElapsed);
        return confirmedExpense
                .add(baselineDailyAvg.multiply(BigDecimal.valueOf(remaining)))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    // ---------- 이상치 ----------

    /**
     * 카테고리별 월 환산 기준선.
     * 일평균으로 환산한 뒤 당월 일수를 곱해 currentPace 와 단위를 맞춘다.
     */
    public static BigDecimal categoryBaseline(BigDecimal categoryExpense, int totalDays, int daysInMonth) {
        if (totalDays <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        return categoryExpense
                .divide(BigDecimal.valueOf(totalDays), MONEY_SCALE + 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(daysInMonth))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** 이번 달 지출 속도를 월 환산한 값 */
    public static BigDecimal currentPace(BigDecimal monthExpense, int daysElapsed, int daysInMonth) {
        if (daysElapsed <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        return monthExpense
                .divide(BigDecimal.valueOf(daysElapsed), MONEY_SCALE + 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(daysInMonth))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** (currentPace - baseline) / baseline. baseline 이 0 이면 계산하지 않는다 */
    public static BigDecimal deltaRatio(BigDecimal currentPace, BigDecimal baseline) {
        if (baseline.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return currentPace.subtract(baseline)
                .divide(baseline, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /** 월초(7일 미만)에는 계산하지 않는다 */
    public static boolean anomalyPeriodReached(int daysElapsed) {
        return daysElapsed >= MIN_DAYS_FOR_ANOMALY;
    }

    /** |deltaRatio| >= 0.30 일 때만 이상치로 본다 */
    public static boolean isAnomaly(BigDecimal deltaRatio) {
        return deltaRatio != null && deltaRatio.abs().compareTo(ANOMALY_THRESHOLD) >= 0;
    }

    // ---------- 비율 ----------

    /** 카테고리 비율. 분모가 0 이면 0 을 반환한다(Infinity/NaN 방지) */
    public static BigDecimal ratio(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE);
        }
        return part.divide(total, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 실제로 거래가 있었던 개월 수. 화면이 "최근 1개월 기준"이라고 밝힐 수 있게 한다.
     *
     * @param monthlyExpenses 직전 BASIS_MONTHS 개월의 월별 지출 합계
     */
    public static int basisMonths(List<BigDecimal> monthlyExpenses) {
        return (int) monthlyExpenses.stream()
                .filter(amount -> amount != null && amount.compareTo(BigDecimal.ZERO) > 0)
                .count();
    }

    /** 직전 3개월에 거래가 한 건도 없으면 예측하지 않는다 */
    public static boolean canForecast(Map<YearMonth, BigDecimal> monthlyExpenses) {
        return monthlyExpenses.values().stream()
                .anyMatch(amount -> amount != null && amount.compareTo(BigDecimal.ZERO) > 0);
    }
}
