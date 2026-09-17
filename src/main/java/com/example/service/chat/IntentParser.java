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
    private static final Pattern PREV_MONTH = Pattern.compile("지난\\s*달|저번\\s*달|전달");
    /** 자릿수를 제한해야 Integer.parseInt 가 터지지 않는다 */
    private static final Pattern COUNT = Pattern.compile("(\\d{1,4})\\s*건");

    private static final Pattern LIST_WORDS = Pattern.compile("내역|목록|보여|리스트");
    private static final Pattern AMOUNT_WORDS = Pattern.compile("얼마|지출|수입|썼|벌었|잔액|요약|수지");

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;

    private IntentParser() {
    }

    public static Intent parse(String message, LocalDate asOf, List<CategoryResponse> categories) {
        if (message == null || message.isBlank()) {
            return Intent.unknown();
        }
        String text = message.toLowerCase();
        YearMonth yearMonth = parseYearMonth(text, asOf);
        CategoryResponse category = matchCategory(text, categories);

        // 구체적인 것부터 본다. "식비 예산 얼마" 는 카테고리와 예산이 둘 다 있는데,
        // 예산을 먼저 보지 않으면 사용자는 예산을 물었는데 지출액을 받는다.
        if (text.contains("예산")) {
            return new Intent(IntentType.BUDGET_STATUS, yearMonth,
                    category == null ? null : category.name(),
                    category == null ? null : category.type(),
                    null, 0);
        }
        if (category != null) {
            return new Intent(IntentType.CATEGORY_AMOUNT, yearMonth,
                    category.name(), category.type(), null, 0);
        }
        if (LIST_WORDS.matcher(text).find()) {
            return new Intent(IntentType.RECENT_TRANSACTIONS, yearMonth,
                    null, null, parseTxnType(text), parseLimit(text));
        }
        if (AMOUNT_WORDS.matcher(text).find()) {
            return new Intent(IntentType.MONTHLY_SUMMARY, yearMonth, null, null, null, 0);
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
