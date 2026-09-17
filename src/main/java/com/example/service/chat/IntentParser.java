package com.example.service.chat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.domain.TransactionType;
import com.example.dto.CategoryResponse;

/**
 * 자연어 질문을 Intent 로 바꾼다.
 *
 * ⚠️ 순수 함수다. DB·시계·설정을 참조하지 않는다.
 *    카테고리를 인자로 받는 이유가 둘이다 — 테스트가 DB 없이 돌고,
 *    넘기는 목록이 인증 사용자의 것뿐이라 소유권 검증이 공짜로 따라온다.
 *
 * ⚠️ "오늘"을 서버가 정하지 않는다. asOf 를 받아 쓴다 (CLAUDE.md §4).
 */
public final class IntentParser {

    private static final Pattern YEAR_MONTH = Pattern.compile("(\\d{4})-(\\d{1,2})");
    private static final Pattern MONTH_ONLY = Pattern.compile("(\\d{1,2})\\s*월");
    private static final Pattern DAY_ONLY = Pattern.compile("(\\d{1,2})\\s*일");
    private static final Pattern PREV_MONTH = Pattern.compile("지난\\s*달|저번\\s*달|전달");
    /** 자릿수를 제한해야 Integer.parseInt 가 터지지 않는다 */
    private static final Pattern COUNT = Pattern.compile("(\\d{1,4})\\s*건");

    private static final Pattern RECURRING_WORDS = Pattern.compile("고정\\s*지출|정기\\s*결제|구독");
    private static final Pattern FORECAST_WORDS = Pattern.compile("예상|예측|이\\s*속도|쓰게\\s*될|얼마나\\s*쓸");
    private static final Pattern LIST_WORDS = Pattern.compile("내역|목록|보여|리스트");
    private static final Pattern AMOUNT_WORDS = Pattern.compile("얼마|지출|수입|썼|벌었|잔액|요약|수지");

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;

    private IntentParser() {
    }

    /**
     * 의도는 구체적인 것부터 본다. 뒤쪽 규칙일수록 어휘가 넓어서,
     * 순서를 뒤집으면 좁은 질문이 넓은 규칙에 먼저 잡혀 엉뚱한 답이 나간다.
     * 예를 들어 "고정지출 뭐 있어" 에는 "지출" 이, "예상 지출 얼마야" 에는
     * "지출"·"얼마" 가 들어 있어 월 요약으로 새어 버린다.
     */
    public static Intent parse(String message, LocalDate asOf, List<CategoryResponse> categories) {
        if (message == null || message.isBlank()) {
            return Intent.unknown();
        }
        String text = message.toLowerCase();
        YearMonth yearMonth = parseYearMonth(text, asOf);
        CategoryResponse category = matchCategory(text, categories);
        LocalDate day = parseDay(text, asOf, yearMonth);

        // 1) 고정지출 — 대상 월이 없다. asOf 기준으로만 계산한다
        if (RECURRING_WORDS.matcher(text).find()) {
            return new Intent(IntentType.RECURRING, null, null, null, null, 0, null);
        }
        // 2) 예산 — "식비 예산 얼마" 처럼 카테고리와 겹치므로 먼저 본다
        if (text.contains("예산")) {
            return new Intent(IntentType.BUDGET_STATUS, yearMonth,
                    name(category), type(category), null, 0, null);
        }
        // 3) 예상 지출
        if (FORECAST_WORDS.matcher(text).find()) {
            return new Intent(IntentType.FORECAST, yearMonth, null, null, null, 0, null);
        }
        // 4) 특정 하루 — 날짜가 잡혔을 때만. 카테고리가 함께 있어도 날짜를 우선한다
        //    (일별 집계에는 카테고리 구분이 없어 ChatService 가 한계를 밝힌다)
        if (day != null) {
            return new Intent(IntentType.DAILY_AMOUNT, YearMonth.from(day),
                    name(category), type(category), null, 0, day);
        }
        // 5) 카테고리별 금액
        if (category != null) {
            return new Intent(IntentType.CATEGORY_AMOUNT, yearMonth,
                    category.name(), category.type(), null, 0, null);
        }
        // 6) 최근 내역
        if (LIST_WORDS.matcher(text).find()) {
            return new Intent(IntentType.RECENT_TRANSACTIONS, yearMonth,
                    null, null, parseTxnType(text), parseLimit(text), null);
        }
        // 7) 월 요약
        if (AMOUNT_WORDS.matcher(text).find()) {
            return new Intent(IntentType.MONTHLY_SUMMARY, yearMonth, null, null, null, 0, null);
        }
        return Intent.unknown();
    }

    /**
     * ⚠️ 연도를 추측하지 않는다. asOf 가 2026-09 여도 "12월" 은 2026-12 다.
     *    "미래니까 작년이겠지" 같은 보정을 넣으면, 사용자가 실제로 미래 달을 물었을 때
     *    말없이 다른 달을 조회하고 그 사실이 화면에 드러나지 않는다.
     */
    private static YearMonth parseYearMonth(String text, LocalDate asOf) {
        YearMonth current = YearMonth.from(asOf);

        Matcher explicit = YEAR_MONTH.matcher(text);
        if (explicit.find()) {
            int month = Integer.parseInt(explicit.group(2));
            if (month >= 1 && month <= 12) {
                return YearMonth.of(Integer.parseInt(explicit.group(1)), month);
            }
        }
        Matcher monthOnly = MONTH_ONLY.matcher(text);
        if (monthOnly.find()) {
            int month = Integer.parseInt(monthOnly.group(1));
            if (month >= 1 && month <= 12) {
                return YearMonth.of(current.getYear(), month);
            }
        }
        if (PREV_MONTH.matcher(text).find()) {
            return current.minusMonths(1);
        }
        // "이번 달" 과 미지정이 같은 결과이므로 따로 검사하지 않는다
        return current;
    }

    /**
     * 특정 하루를 뽑는다. 잡히지 않으면 null 이고 그때는 월 단위 의도로 넘어간다.
     *
     * ⚠️ "오늘·어제" 는 asOf 를 기준으로 빼므로 달을 넘어갈 수 있다.
     *    9월 1일에 "어제" 는 8월 31일이다. 그래서 대상 월을 텍스트가 아니라
     *    계산된 날짜에서 다시 뽑아야 한다(호출부 참조).
     */
    private static LocalDate parseDay(String text, LocalDate asOf, YearMonth month) {
        if (text.contains("오늘")) {
            return asOf;
        }
        if (text.contains("어제")) {
            return asOf.minusDays(1);
        }
        if (text.contains("그저께") || text.contains("그제")) {
            return asOf.minusDays(2);
        }
        Matcher day = DAY_ONLY.matcher(text);
        if (day.find()) {
            int value = Integer.parseInt(day.group(1));
            if (value >= 1 && value <= month.lengthOfMonth()) {
                return month.atDay(value);
            }
        }
        return null;
    }

    /** 겹치는 이름 중 가장 긴 것을 쓴다. "통신" 이 "주거/통신" 보다 먼저 잡히면 안 된다. */
    private static CategoryResponse matchCategory(String text, List<CategoryResponse> categories) {
        if (categories == null) {
            return null;
        }
        CategoryResponse best = null;
        for (CategoryResponse candidate : categories) {
            if (candidate.deleted() || candidate.name() == null || candidate.name().isBlank()) {
                continue;
            }
            String name = candidate.name().toLowerCase();
            if (text.contains(name) && (best == null || name.length() > best.name().length())) {
                best = candidate;
            }
        }
        return best;
    }

    private static String name(CategoryResponse category) {
        return category == null ? null : category.name();
    }

    private static TransactionType type(CategoryResponse category) {
        return category == null ? null : category.type();
    }

    private static TransactionType parseTxnType(String text) {
        if (text.contains("수입") || text.contains("벌")) {
            return TransactionType.INCOME;
        }
        if (text.contains("지출") || text.contains("쓴") || text.contains("썼")) {
            return TransactionType.EXPENSE;
        }
        return null;
    }

    private static int parseLimit(String text) {
        Matcher matcher = COUNT.matcher(text);
        if (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            return Math.max(1, Math.min(MAX_LIMIT, value));
        }
        return DEFAULT_LIMIT;
    }
}
